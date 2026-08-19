# ADR-0075: 自動LLM Wiki構築を共通AIタスクキューへ統合する

- Status: Accepted
- Date: 2026-08-16

## Context

ADR-0109 で導入した Knowledge の自動Wiki再構築は、`KnowledgeViewModel` から `KnowledgeRepository.rebuild()` を直接呼び、ローカルLLM推論もその呼び出し中に実行していた。そのため長時間のWiki生成が画面操作のライフサイクルに結び付き、ADR-0069 で導入した共通AIタスクキューから状態を確認したり、全体一時停止、充電時自動再開、個別停止・キャンセル・再開を行えなかった。

一方、LLM Editor の「記事を作成」「記事を編集」は、ユーザーが入力した依頼に対して生成結果をその場で開く対話操作である。自動Wiki構築とは完了後のUI遷移と要求される永続入力が異なる。

## Decision

### 自動Wikiの構築・再構築をWorkManagerジョブにする

Knowledge画面の「自動Wikiを構築」「ナレッジを再構築」は、直接 `KnowledgeRepository.rebuild()` を実行せず、`KnowledgeBuildWorker` をWorkManagerへ登録する。

ジョブは一意な `knowledge-ai-wiki-build` とし、同じ構築要求を連打しても同時実行しない。要求の有無、個別停止、失敗とエラーは端末内SharedPreferencesに保持し、WorkManagerの実行状態と組み合わせて待機・実行・停止・失敗を投影する。完了時は要求状態を削除し、既存の共通キュー方針どおり完了タスクを一覧へ残さない。

Wiki本文やユーザー固有データはこのキュー状態へ保存しない。自動Wiki構築は既存の保存済み要約から再計算可能なため、再開時に入力スナップショットを永続化する必要はない。

### 共通AI実行ゲートを利用する

`KnowledgeBuildWorker` は `LocalAiBackgroundTaskGate` のpermitを取得してからローカルLLM推論を行う。これにより要約や蔵書整理と同時に複数の重いローカル推論を開始しない。

`LocalAiBackgroundExecutionPreferences.paused` が有効な場合は新しいWiki推論を開始しない。全体一時停止時は実行中WorkManager jobをcancelするが、構築要求自体は保持して「一時停止中」として共通キューへ表示する。全体再開時に再度enqueueする。

充電時自動再開が有効な場合はKnowledge専用のcharging workerを登録する。充電を契機に共有実行ゲートを開き、構築要求が残っていればWiki workerを再度enqueueする。この処理はsummary/libraryの充電再開workerと同様にidempotentとする。

### 共通AIタスクキューへ投影する

ADR-0069 の `AiTaskQueueItemKind` に `KNOWLEDGE_WIKI` を追加する。`:app` のcomposition adapterはKnowledgeのWorkManager状態を共通表示モデルへ変換し、次を提供する。

- 待機・実行・全体一時停止・個別停止・失敗の表示
- 待機中または実行中の個別停止
- タスクのキャンセル
- 個別停止からの再開
- 失敗からの再実行
- 全体一時停止と充電時自動再開への参加

Knowledge固有の生成ロジックは引き続き `:feature:knowledge` が所有し、共通キューへWiki生成ロジックを移さない。

### LLM Editorの対話操作は今回バックグラウンド化しない

自然言語による新規記事作成・編集・既存記事からの派生作成は、生成完了後に対象記事を選択して表示する対話フローを維持する。これらをdurable jobにするには、ユーザー入力、対象ページ、完了後遷移を安全に永続化する別設計が必要なため、本Decisionの対象外とする。

## Consequences

### Positive

- 自動Wikiの長時間生成をKnowledge画面から離れても継続できる。
- 要約・蔵書整理・自動Wikiを同じAIタスクキューから確認・制御できる。
- 自動Wiki生成が共通AI実行ゲートへ参加し、端末内LLMの重い推論を直列化できる。
- 全体一時停止と充電時自動再開がKnowledgeにも一貫して適用される。
- 自動Wikiの入力は再構築可能なため、キュー永続化へ記事本文やユーザー固有内容を複製しない。

### Negative

- 自動Wiki構築完了時の `KnowledgeBuildResult` はKnowledge画面へ同期的に返らない。生成されたページ自体は既存のDataChangeNotifierで再読込される。
- LLM Editorの新規作成・編集は引き続きforeground処理であり、共通キューの対象ではない。
- WorkManager状態に加えて小さな要求状態をSharedPreferencesで保持するため、状態源が1種類だけではない。

## Relationship to existing ADRs

- ADR-0109: 自動Wiki生成の入力、fingerprint、1回最大8ページという生成ポリシーは維持し、その実行場所だけをバックグラウンドジョブへ変更する。「バックグラウンドでの無制限再生成」は引き続きDeferredであり、本Decisionは上限付きの明示的構築だけを対象とする。
- ADR-0069: feature固有のタスク意味論を維持したまま共通AIタスクキューへ投影する方針をKnowledgeへ拡張する。
- ADR-0056: Wiki生成ポリシーはKnowledge feature、推論実行の直列化は汎用AI/background capabilityという責務境界を維持する。
- ADR-0063: settings UIはfeature側に残し、appはWorkManagerと既存featureを接続するcomposition adapterに限定する。
