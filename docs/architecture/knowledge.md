# Knowledge Architecture

Knowledge は保存済みブックマークの要約を根拠として、出典付きWikiの自動構築とLLM Editorを提供する。Knowledge固有の資料選択、トピック化、prompt、生成状態、永続化は `:feature:knowledge` が所有する。

## Auto Wiki rebuild

自動Wiki再構築は、1個の長時間Workerで複数ページを生成しない。現在の処理は次の2段階とする。

```text
KnowledgeBuildWorker
  保存済み要約を取得
  -> tag / folder / source からtopicを抽出
  -> obsolete pageを整理
  -> fingerprintを比較
  -> 変更topic IDを計画
             |
             +--> KnowledgeTopicBuildWorker(topic A)
             +--> KnowledgeTopicBuildWorker(topic B)
             +--> KnowledgeTopicBuildWorker(topic C)
                         ...
```

`KnowledgeBuildWorker` は計画だけを担当し、LLM推論を行わない。変更が必要なトピックごとに独立した `KnowledgeTopicBuildWorker` をWorkManagerへ登録し、各Workerは1トピックだけを生成または更新する。

Local providerでもトピックWorker同士へWorkManager dependencyを作らず、各topicの失敗は他topicから独立させる。ただしLocal workerは `LocalAiBackgroundTaskGate` のpermitを取得してからforeground化する。permit待ちのworkerはforeground serviceを開始せず、実際に高コストなLocal AI処理を行うworkerだけをforegroundにする。`LocalAiBackgroundTaskGate` はSummary・Library等を含むfeature横断のLocal AI排他・優先度制御を担当する。

ChatGPT providerでは各topicを独立したWorkManager taskとして扱う。network constraintとretry/backoffは各workerへ適用する。

トピックWorkerは実行直前に最新の保存済み要約から対象トピックを再構築し、editor-managed状態とfingerprintを再確認する。別Workerや再計画で既に同じ内容が生成済みならLLMを呼ばない。

再構築要求には `requestId` を持たせ、計画Workerと子Workerへ同じIDを渡す。再要求、provider変更、停止・失敗後の再開では新しいIDで再計画するため、古いgenerationのWorkerは現在queue stateを更新できない。

計画完了後はpending topic集合をdurable queue stateとして保持する。pending topicが残っている間の通常 `kick()` は新しい計画Workerを投入しない。Global pauseで現在generationのWorkをキャンセルする場合はpending topic集合を破棄し、再開時に保存済みデータから再計画する。すでに生成済みのページはfingerprint比較で再利用される。

AIタスクキューへKnowledge build状態を投影するときは、個々のWorkManager `WorkInfo` を全件列挙しない。provider pause、stopped / failed状態、およびpending topic集合から `PAUSED` / `STOPPED` / `FAILED` / `RUNNING` / `QUEUED` を投影し、監視UIの読み取り負荷をWork数へ比例させない。

## Topic and source selection

自動Wikiのトピック候補は次の優先順位で決める。

1. タグがある資料はタグ単位
2. タグがなく通常ブックマークフォルダがある資料はフォルダ単位
3. どちらもない資料は提供元単位

同一資料が複数タグを持つ場合は複数トピックの根拠になり得る。トピックIDは種別と正規化キーから決定論的に生成する。

1トピックで保持する自動Wiki source上限はprovider別とする。

| Provider | Maximum sources per auto-Wiki topic |
| --- | ---: |
| Local | 12 |
| ChatGPT / Codex | 100 |

Cloudで100件を選択しても100件の要約全文を無制限にpromptへ連結しない。Knowledge adapterが公開するprompt budget内に収まるようsource blockの文字数を制限し、各要約excerptを縮める。ChatGPT / CodexのKnowledge prompt budgetは現在16,000文字の保守的上限を維持する。

このprovider別上限は自動Wiki構築に対するものとし、ユーザー指示による新規ページ作成やLLM Editorのretrieval上限は別ポリシーとして扱う。

## Background provider execution

Knowledgeの実行先はユーザーが `LOCAL` / `CHATGPT` を明示選択する。入力内容による自動routingは行わない。

自動Wikiでは計画時のprovider snapshotをトピックWorkerへ引き継ぐ。

- Local topic worker: 独立したWorkRequestのまま `LocalAiBackgroundTaskGate`、Local pause、charging resumeを利用し、permit取得後にforeground化する。
- ChatGPT topic worker: Cloud pause、network connectivity constraint、retryable failureのexponential backoffを利用する。
- Cloudの計画Workerは端末内データだけを読むためnetwork constraintを要求しない。
- provider変更時は現在generationのWorkをキャンセルし、新providerで再計画する。

Cloud topic worker同士をKnowledge独自のgateでは直列化しない。各topicは独立したWorkManager taskとして実行し、providerの一時的なrate limitやnetwork failureは各taskのretry/backoffで処理する。複数のforeground topic workerが同時に動作しても通知が競合しないよう、workerごとにnotification IDを分ける。

## Persistence and editor ownership

自動生成ページは `editor_managed = false` とし、現在のトピック集合から消えた場合は削除できる。ユーザー依頼による新規作成またはLLM編集を受けたページは `editor_managed = true` とし、自動Wiki再構築で上書き・削除しない。

ページとsource集合のfingerprintが一致する場合は再生成しない。providerごとに自動Wiki source上限が異なるため、providerを変更すると同じトピックでもsource集合が変わり、必要に応じて再生成される。

## Sources

- [ADR-0109](../adr/0109-generated-knowledge-wiki.md)
- [ADR-0175](../adr/0175-knowledge-local-chatgpt-routing.md)
- [ADR-0227](../adr/0227-partition-auto-wiki-generation-by-topic.md)
- [ADR-0229](../adr/0229-bound-knowledge-rebuild-runtime-pressure.md)
