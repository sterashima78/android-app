# ADR-0226: 自動Wiki生成をトピック単位の永続タスクへ分割する

- Status: Accepted
- Date: 2026-08-31
- Refines: [ADR-0109](0109-generated-knowledge-wiki.md), [ADR-0175](0175-knowledge-local-chatgpt-routing.md)

## Context

ADR-0109 では、保存済みブックマークの要約からタグ・通常フォルダ・提供元を使って自動Wikiトピックを構築し、fingerprint が変わったページだけを増分再生成する設計を採用した。また、モバイル端末上で1回の処理が大きくなりすぎないよう、1回の再構築で生成するページを最大8件、1トピックの出典を最大12件に制限した。

その後、Knowledge の自動再構築は WorkManager の単一 `KnowledgeBuildWorker` 内でトピック抽出から複数ページのLLM生成まで順番に実行する形になった。このため、変更トピックが複数あると1個の Worker が長時間生存し、Android のバックグラウンド実行制約やプロセス終了の影響を受けやすい。8件上限は1 Workerのサイズを抑える一方で、9件目以降を同じ再構築要求の中で完了できず、ユーザーが再度再構築を要求する必要も生じる。

また、ADR-0175 により自動Wikiを ChatGPT / Codex へ送れるようになった。Cloud provider はLocalモデルより広い入力を扱えるが、自動Wikiの出典数はLocalと同じ12件に制限されたままであり、多数の保存済み要約を横断してWikiを作る用途では厳しすぎる。一方、ADR-0175 はCloud Knowledgeのprompt budgetを保守的な16,000文字と定めており、単純に100件の要約を固定長で連結するとこのbudgetを超える。

## Decision

### 1. 自動Wiki再構築を計画WorkerとトピックWorkerへ分割する

自動Wiki再構築は次の2段階に分ける。

1. `KnowledgeBuildWorker` は保存済み要約を読み、トピック抽出、obsolete page整理、fingerprint比較を行い、生成または更新が必要なトピックIDを計画する。
2. 変更対象の各トピックについて `KnowledgeTopicBuildWorker` を1件ずつWorkManagerへ登録し、各Workerは指定された1トピックだけを生成または更新する。

計画WorkerはLLM推論を行わない。LLM推論を伴う長時間処理をトピックごとの独立した永続WorkRequestへ分離することで、トピック数が増えても単一Workerの実行時間を増大させない。

ADR-0109 の「1回の再構築で最大8ページ生成」という制限は廃止する。再構築要求内の変更トピック数そのものには8件の上限を設けず、代わりに1つの生成Workerが扱うページ数を常に1件へ制限する。

### 2. 再構築要求ごとにgeneration IDを持ち、古い子Workerを無効化する

Knowledge build queue state に再構築要求を識別する `requestId` を保持する。計画WorkerとすべてのトピックWorkerは同じ `requestId` とprovider snapshotをWorkManager inputに保持する。

ユーザーが再構築を再要求した場合、providerを変更した場合、停止後または失敗後に再開した場合は新しい `requestId` を発行して再計画する。古いgenerationのWorkerが遅れて再開しても、現在の `requestId` と一致しなければqueue stateを更新しない。

計画されたトピックIDをpending setとして保持し、トピックWorkerの成功ごとに1件ずつ除去する。すべて完了し、かつ失敗・停止状態でなければbuild requestを完了する。重複した完了通知や古いgenerationの完了通知ではqueueを早期完了させない。

1トピックが非再試行エラーになっても、すでに登録済みの他トピックWorkerは独立して実行できる。ユーザーが失敗状態から再開した場合は新しいgenerationで再計画し、すでに成功してfingerprintが一致するページは再利用する。

### 3. provider snapshotとLocal / Cloud実行制約を各トピックWorkerへ引き継ぐ

ADR-0175 の明示provider routingは維持する。

- 計画Workerはenqueue時の `KnowledgeExecutionProvider` を保持する。
- 生成対象ごとに作るトピックWorkerへ同じprovider snapshotを渡す。
- `LOCAL` のトピックWorkerは `LocalAiBackgroundTaskGate` とLocal pause / charging resumeの対象とする。
- `CHATGPT` のトピックWorkerはCloud pauseを適用し、network connectivity constraintとretryable failureのexponential backoffを設定する。
- provider変更時は現在generationの計画・トピックWorkをキャンセルし、新providerと新しい `requestId` で再計画する。

計画処理自体は端末内に保存されたブックマークと要約だけを読むため、ChatGPTを選択していても計画Workerにはnetwork constraintを要求しない。network constraintは実際にCloud推論を行うトピックWorkerにだけ適用する。

Cloud topic worker同士をKnowledge独自の実行gateでは直列化しない。各topicを独立したWorkManager taskとして扱い、一時的なrate limitやnetwork failureは各taskのretry/backoffへ委譲する。複数のforeground topic workerが同時に実行される場合に備え、notification IDはworkerごとに分ける。

### 4. 自動Wikiの出典数上限をprovider別にする

自動Wikiの1トピックあたりの出典候補数は次とする。

- `LOCAL`: 最大12件
- `CHATGPT`: 最大100件

Localは端末内モデルの入力・メモリ・推論時間への影響を抑えるためADR-0109の12件上限を維持する。Cloudは保存済み要約をより広く横断できるよう100件へ拡張する。

この上限は自動Wikiのトピック構築に適用する。ユーザー指示による新規記事作成やLLM編集の既存retrieval上限は今回変更しない。

### 5. Cloudの100ソースはprompt budget内で縮約して渡す

ADR-0175 のCloud Knowledge prompt budget 16,000文字は変更しない。

トピックが多数の出典を持つ場合、各出典のタイトル・提供元・引用番号を含むheaderを組み立てたうえで、利用可能なsource文字budgetを出典間に配分し、それぞれの要約excerptを短くする。最終的なsource blockは指定された文字budgetを超えないよう切り詰める。

したがって、Cloudの「最大100件」は100件分の完全な要約全文を必ずLLMへ送るという意味ではない。保存・fingerprint・citation source集合として最大100件を扱い、選択モデルのprompt budgetに応じて各要約の情報量を縮約する。既存Wiki本文を同時に渡すrefreshでは、本文が占めるbudgetに応じてsource excerptがさらに短くなる場合がある。

### 6. 既存Wikiの増分更新とeditor-managed保護は維持する

トピックWorkerは実行時点の保存済み要約から対象トピックを再構築し、次を再確認する。

- editor-managed pageなら自動更新しない
- fingerprintがすでに一致していればLLMを呼ばず成功扱いにする
- トピック自体が消えていれば生成しない

これにより計画後に別Workerが先に更新した場合や、再試行・再計画で同じトピックが現れた場合も冪等に処理する。

## Consequences

### Positive

- トピック数が多くてもLLM生成を1個の長時間Workerへ集約しない。
- 8ページを超える変更も1回の再構築要求からトピック単位のWorkとして登録できる。
- 1トピックの失敗・再試行が他トピックの生成処理そのものを1つのcoroutine lifetimeへ巻き込まない。
- provider変更や再要求時に古いgenerationのWorkerがqueue stateを破壊しない。
- Localの負荷上限を維持しつつ、ChatGPT自動Wikiでは最大100件の保存済み要約をトピック根拠として扱える。
- Cloudの入力を増やしてもADR-0175の保守的prompt budgetを維持できる。

### Negative

- 1回の再構築で多数のWorkRequestが生成されるため、WorkManager上のtask数は増える。
- トピックWorkerは実行時に最新source snapshotからトピックを再構築するため、ブックマーク数が非常に多い場合は計画時のローカル集計コストを各Workerでも一部繰り返す。
- Cloudで100件を候補にしても16,000文字budgetでは各要約excerptが短くなり、完全な100件要約を同時に利用できるとは限らない。
- Cloud topic workerが同時実行された場合はproviderのrate limitへ到達する可能性があり、その場合はWorkManagerのretry/backoffにより完了までの時間が延びる。
- queue stateにrequest IDとpending topic集合を持つため、従来の単一Workerより状態管理が増える。

## Verification

- 自動Wikiの親Workerが `planRebuild` だけを呼び、LLMページ生成を直接呼ばないことをarchitecture testで固定する。
- 変更トピックごとに `KnowledgeTopicBuildWorker` のWorkRequestを生成し、各Workerが1つのtopic IDだけを `rebuildTopic` へ渡すことを確認する。
- Local自動Wikiのsource上限が12、ChatGPT自動Wikiのsource上限が100であることをunit testする。
- 120件の同一トピック資料から100件まで選択できることをunit testする。
- 100件の長い要約を入力しても新規Wiki promptが16,000文字budgetを超えず、100番目のcitation headerまで含められる代表ケースをunit testする。
- editor-managed protectionとfingerprint再利用の既存テストを維持する。
- Public repository、Architecture、Test、LintのCI gateを通す。

## Documentation

- ADR-0109の8ページ/12件一律上限は本ADRで部分的に更新する。
- ADR-0175のprovider snapshot、Local/Cloud pause、Cloud retry、16,000文字prompt budgetは維持し、network constraintの適用先をCloud推論トピックWorkerへ明確化する。
- `docs/architecture/knowledge.md` にKnowledge background buildの現在形を集約する。`module-map.md` のKnowledge ownership記述は引き続き有効である。

## References

- [ADR-0006](0006-durable-background-sync.md)
- [ADR-0109](0109-generated-knowledge-wiki.md)
- [ADR-0146](0146-workmanager-worker-factory-injection.md)
- [ADR-0175](0175-knowledge-local-chatgpt-routing.md)
