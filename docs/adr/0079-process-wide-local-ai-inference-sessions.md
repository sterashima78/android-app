# ADR-0079: ローカルAIのEngine寿命と推論セッション寿命を分離する

- Status: Accepted
- Date: 2026-08-16
- Refines: ADR-0020, ADR-0056, ADR-0070, ADR-0071

## Context

ADR-0020では、同じモデル・backend・context・モデルファイルを利用する間はLiteRT-LMの初期化済みEngineを `LocalModelManager` 内で再利用し、推論リクエストごとにEngineを再初期化しない方針を採用した。ADR-0056では、このEngine cacheとlifecycleを `core:ai-runtime` の技術的責務としている。

しかしバックグラウンド実装では、SummaryWorkerが記事1件ごと、LibraryOrganizationBatchWorkerが書籍1件ごとに `LocalModelManager` を生成し、処理終了時に `close()` していた。`close()` は保持中Engineを閉じるため、同じモデルで連続タスクを処理しても次のタスクが毎回 `PREPARING_MODEL` から始まり、ADR-0020の再利用方針がWorker境界で失われていた。

一方、単にManagerをApplication寿命まで保持すると、最後の推論後も数GB級のモデルとEngineが不要にメモリへ残り続ける。タスク単位の寿命と重いEngineの寿命を分離し、連続実行時の再利用とアイドル時の解放を両立する必要がある。

またADR-0070/0071ではバックグラウンドAIの実行権を1タスク単位で直列・優先度制御している。Engineの保持期間はこのfeature固有タスク状態とは独立したruntimeの技術的ライフサイクルとして扱う必要がある。

## Decision

### 1. プロセス内の推論利用者は共有 `LocalModelManager` を利用する

Chat、Summary、Knowledge、Libraryなどローカル推論を行う経路は、同一アプリプロセス内では `LocalModelManager.shared(context)` から同じManagerを取得する。

`AppContainer` もこの共有Managerを利用する。SummaryWorkerやLibraryOrganizationBatchWorkerはタスクごとにManagerを生成せず、共有Managerを取得する。また共有ManagerをWorkerのタスク終了時に `close()` しない。

モデルダウンロード処理は推論Engineの利用者ではなく、ダウンロード進捗と一時的なファイル管理をWorker/JobService自身が所有するため、従来どおり独立した `LocalModelManager` を生成して終了時に閉じる。ダウンロードWorkerが共有推論Managerを閉じる構造にはしない。

### 2. 1回の推論利用を `LocalInferenceSession` として数える

`LocalModelManager` の各汎用推論呼び出しは、Engine利用前に `LocalInferenceSession` を取得し、生成処理終了時に必ず閉じる。

このSessionはLiteRT-LMの `Conversation` とは別概念である。

- `LocalInferenceSession`: アプリruntime上のEngine利用権・寿命境界
- LiteRT-LM `Conversation`: 1回のモデル会話・生成コンテキスト

複数の推論要求が同時に到着し、1件が `inferenceLock` の実行権を待っている場合もSession数へ含める。これにより、実行待ちの要求が存在する間にEngineをアイドル解放しない。

Sessionは `AutoCloseable` とし、多重 `close()` でも参照数を一度だけ減らす。

### 3. Session数が0になってから5分間アイドルならEngineを解放する

最後のSessionが終了してSession数が0になった時点で、Engine解放を5分後に予約する。

- 5分以内に新しいSessionが開始された場合、予約済み解放を取り消す
- そのSession終了後に再び0になれば、そこから改めて5分を数える
- 5分経過時点でもSession数が0なら、保持中の推論Engineを `close()` してcacheから除去する
- Engine解放処理と新規Session開始は同期し、Engineを閉じている途中に同じEngineを新しいSessionへ渡さない
- 次の推論要求時は通常の `PREPARING_MODEL` を経てEngineを再初期化する

5分は初期値であり、連続キューのタスク間隔を十分吸収しつつ、AI利用終了後にモデルを無期限保持しないためのruntime policyとする。実端末でメモリ圧迫や再ロード頻度に問題があれば後続ADRで調整する。

Tokenizer cacheはEngineとは別の軽量資源であり、このアイドルタイムアウトでは解放対象にしない。Manager自体を閉じる場合は従来どおりEngineとTokenizerの両方を解放する。

### 4. Engine cache keyと即時invalidate条件はADR-0020を維持する

アイドル解放を追加しても、次の場合は5分を待たず既存方針どおりEngineを再利用しない。

- モデルIDが変わる
- CPU/GPU backendが変わる
- context tokensまたはspeculative decoding設定が変わる
- モデルファイルのサイズまたは更新時刻が変わる
- モデルを削除する
- 推論処理が失敗し、保持Engineをinvalidateする
- 独立Managerを明示的に `close()` する

したがってSession参照数は「同一条件のEngineを安全に温存する期間」を決めるものであり、cache keyの妥当性判定を置き換えない。

### 5. 共有Managerは外部のモデル状態変更を再読込する

モデルダウンロードは独立Managerで行うため、共有Managerが先に生成済みでも新しいモデルファイル・選択状態を認識できる必要がある。

`selectedModel()` は返却前にmodel catalogをrefreshし、SharedPreferencesとモデルファイルの現在状態から選択モデルを再解決する。これにより、プロセス再起動を要求せずダウンロード直後のモデルを共有推論Managerから利用できるようにする。

### 6. feature固有のキュー・優先度とは分離する

`LocalInferenceSession` は記事ID、蔵書ID、要約、タグなどの業務概念を持たない。バックグラウンドのタスク選択・優先度・pause/resumeはADR-0070/0071の `LocalAiBackgroundTaskGate` と各featureのdurable stateが引き続き所有する。

Sessionは「推論要求が存在するか」と「Engineをいつ解放できるか」だけを扱う。

### 7. 公開リポジトリへユーザーデータを追加しない

本変更のテストはSession数とidle timerのみを検証し、実在する記事タイトル、URL、蔵書名、ASIN、Personal Document ID、タグ、認証情報、モデル利用ログなどをfixtureへ追加しない。

## Consequences

### Positive

- 連続するSummaryやLibrary AIタスクの2件目以降で、同じ条件のEngineを再利用できる。
- タスクごとの `LocalModelManager.close()` による不要なモデル再ロードをなくせる。
- SummaryからLibrary、LibraryからChatなどfeatureをまたいだ連続推論でも同じEngineを再利用できる。
- AI利用が終われば5分後に重いEngineを解放し、Application寿命中ずっとモデルを保持しない。
- Engine lifecycleは `core:ai-runtime` に閉じ、feature固有queueの責務境界を維持できる。
- Session trackerをruntime非依存の単体テストで検証できる。

### Negative

- 最後の推論後、最大5分間はモデルとEngineのメモリ使用量が残る。
- プロセス内に共有Managerとidle release用の軽量なスケジューラが存在する。
- `LocalModelManager` の汎用進捗Flowは引き続きManager単位であり、Session固有progress APIへの分離は本ADRの対象外とする。推論自体は `inferenceLock` で直列化される。
- 実行中推論をプリエンプトする機能は追加しない。バックグラウンド優先度はADR-0071どおり次のタスク境界で評価する。

## Relationship to existing ADRs

- ADR-0020: Engineを再利用する既存決定を、Managerのプロセス共有とidle evictionで実際のWorker境界まで適用する。
- ADR-0056: Engine lifecycleを `core:ai-runtime` が所有し、feature固有AI policyを持たない境界を維持する。
- ADR-0070: バックグラウンドAI推論の直列化は維持する。Sessionはその実行権とは別にEngine寿命を管理する。
- ADR-0071: 優先度付き非プリエンプティブ実行を維持する。Engine再利用によってタスク切替時の再ロードを不要にする。
