# ADR-0105: 要約の記事本文取得とローカルAI推論をパイプライン分離する

- Status: Accepted
- Date: 2026-08-19
- Refines: ADR-0029, ADR-0071, ADR-0079, ADR-0104

## Context

要約処理は記事本文をネットワークから取得した後、端末内のローカルモデルで要約と必要なブックマークメタデータ生成を行う。従来の `SummaryWorker` は `LocalAiBackgroundTaskGate` の permit を取得してから `ArticleContentClient.fetchArticleText()` を実行していたため、HTTP取得や本文抽出を待っている間もバックグラウンドAIの唯一の実行枠を占有していた。

ADR-0071では `LocalAiBackgroundTaskGate` をローカルAI推論の非プリエンプティブな優先度付き実行権として定義し、タスク境界ごとに優先度を再評価する。ADR-0079でもローカルAI Engine の寿命と feature 固有タスクの準備処理を分離している。記事取得をAI permit内で行う従来構造は、これらの責務境界より広い範囲を直列化していた。

一方、記事本文をWorkManagerの `Data` に直接渡すとサイズ制約とプロセス再生成時の耐久性に依存する。さらに全記事を無制限に先読みすると、不要な通信と端末DB使用量が増えるため、少量の準備済み本文だけを保持する必要がある。

## Decision

### 1. Summary のdurable taskは1記事1タスクのまま維持する

`summary_tasks` は要約要求のdurable stateを引き続き所有する。本文取得と推論を別々のユーザー向けタスクとして公開せず、共通AIタスクキューからも従来どおり1記事を1タスクとして表示する。

本文取得中もタスクの `state` は `queued` のままとし、`progress_stage = fetching_article` で準備中であることを表現する。AI推論を開始した時点でのみ `running` へ遷移する。

### 2. 記事本文取得専用Workerを追加する

`:feature:summary:data` に `SummaryContentFetchWorker` を追加し、`SummaryWorker` からネットワーク取得を除去する。

`SummaryContentFetchWorker` は次の条件を満たす待機タスクだけを対象にする。

- `state = queued`
- 強制再要約、または既存要約が存在しない
- 準備済み本文がまだ存在しない

取得処理は `LocalAiBackgroundTaskGate` のpermitを取得しない。これにより、別記事のローカルAI推論中に次の記事本文を取得できる。

ただし記事本文を取得する前に、共有 `LocalModelManager` で要約モデルが選択済みかだけを確認する。モデル未選択なら従来の `SummaryWorker` と同じエラーでタスクを失敗させ、本文を先に取得・保存しない。この確認はEngineの生成やAI permitの取得を伴わない。

### 3. 準備済み本文は端末DBへ永続化する

`summary_article_content` を追加し、次を保持する。

- `article_id`
- `content`
- `fetched_at`

本文はWorkManager `Data` やプロセスメモリだけに保持しない。`summary_tasks.article_id` を外部キーとして参照し、タスク行が置換・削除された場合は準備済み本文も削除する。

推論成功時にはタスク完了と同じDB transactionで準備済み本文を削除する。推論失敗時は本文を残し、明示的な再開時にネットワーク取得を繰り返さず再利用する。ユーザーがタスクをキャンセルした場合は再開対象ではないため、キャンセル状態への遷移と同じtransactionで準備済み本文を削除する。停止は再開可能な状態なので本文を保持する。

### 4. 推論Workerは準備済みタスクだけをAI gateへ投入する

`SummaryWorker` が `LocalAiBackgroundTaskGate` の候補にするのは次のいずれかだけとする。

- 強制再要約ではなく既存要約を再利用できるタスク
- `summary_article_content` に準備済み本文が存在するタスク

したがって本文取得待ちのタスクはAI gateの待機者にならず、LibraryやKnowledgeなど他featureの推論枠を妨げない。

優先度はADR-0071どおりAI permit取得直前に現在の「あとで読む」所属状態から再評価する。本文取得時点の優先度を推論順序として固定しない。

### 5. 本文取得も高優先度記事を先に準備する

本文取得Worker内の選択順にも既存のsummary priority orderingを利用する。「あとで読む」の記事を通常記事より先に準備し、高優先度タスクが本文取得待ちのために遅れる可能性を減らす。

ただし本文取得自体はAI gateを利用しないため、この順序はローカルAIの排他制御を変更しない。

### 6. 先読み本文は最大2件に制限する

準備済み本文のうち `queued` または `running` タスクに属するものを最大2件まで先読みする。

推論が1件完了して準備済み本文が削除されたら、推論Workerから本文取得Workerを再度kickしてバッファを補充する。これにより、推論中に次の記事を取得しつつ、大量の本文を先に保存することを避ける。

初期値2件は、1件を推論中、もう1件を次の推論待ちとして確保する最小限のパイプライン深度として採用する。実端末計測で不足または過剰と判断した場合は後続ADRで変更する。

### 7. WorkManagerのunique workを取得用と推論用に分ける

Summary featureは次の2本のunique workを所有する。

- `article-summary-content-fetch-queue`: `SummaryContentFetchWorker`
- `article-summary-queue`: `SummaryWorker`

enqueue、kick、全体再開では両方をスケジュールする。AI全体の一時停止では両方をキャンセルし、新しい本文取得も開始しない。充電時自動再開では両方を再開する。

個別タスクの停止・キャンセルが本文取得中に発生した場合、取得結果は保存直前にタスクがまだ `queued` か確認する。停止済みまたはキャンセル済みなら取得結果を破棄する。

### 8. DBバージョンを24へ更新する

`summary_article_content` 追加のためアプリDBバージョンを23から24へ更新する。`YomitoriDatabase.onUpgrade()` は既存のfeature schema contributionを再適用するため、`CREATE TABLE IF NOT EXISTS` により新テーブルを追加し、既存の `articles`、`article_summaries`、`summary_tasks` は保持する。

### 9. 公開リポジトリへ記事本文やユーザーデータを追加しない

`summary_article_content` に保存される記事本文は実行時の端末DBだけに存在する。テスト・fixture・ADRには実在する記事本文、URL、タイトル、タグ、フォルダ名、認証情報を固定値として追加しない。テスト本文は `prepared body` など一般化した文字列、URLは `example.com` を使用する。

## Consequences

### Positive

- 記事取得待ちの間にローカルAI Engineを遊ばせず、別の準備済みタスクを推論できる。
- 記事取得はSummary固有のI/OとしてAI gateから分離され、LibraryやKnowledgeの推論待ちを不要に増やさない。
- 推論失敗後は準備済み本文を再利用でき、ネットワークアクセスを繰り返さない。
- 「あとで読む」の動的優先度を推論開始直前まで維持できる。
- 少量の先読みバッファにより通信量とDB使用量を制限できる。
- モデル未選択時に不要な記事取得を行わない。
- 共通AIタスクキューでは1記事1タスクという既存の操作意味論を維持できる。

### Negative

- Summary feature内にWorkManagerのunique workが2本存在し、相互にkickする制御が増える。
- 準備済み本文を一時的に端末DBへ保存するため、推論中は従来よりDB使用量が増える。
- 推論失敗したタスクの準備済み本文は再試行のためタスクログ削除まで保持される場合がある。
- 本文取得失敗と推論失敗が異なるWorkerで発生するため、障害解析ではprogress stageとtask stateを合わせて確認する必要がある。
- 初回の1件は本文取得完了まで推論を開始できないため、単発要約のレイテンシ自体は基本的に変わらない。本変更の主目的は連続処理時のスループット向上である。

## Relationship to existing ADRs

- ADR-0029: `summary_tasks` のdurable stateと進捗表示を維持し、本文取得中はqueued taskのprogressとして表現する。
- ADR-0071: AI permitの優先度・非プリエンプティブ実行・タスク境界での再評価を維持し、permit対象を実際の推論部分へ狭める。
- ADR-0079: Engine lifecycleとfeature固有準備処理を分離する判断に合わせ、ネットワークI/Oをローカル推論sessionの外へ置く。
- ADR-0104: Summaryが自身のdurable stateとWorkManager実装を所有し、`:feature:ai-task-queue` は集約表示と横断操作だけを担当する境界を維持する。
