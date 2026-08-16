# ADR-0070: 蔵書AI整理を1冊単位のタスクとして表示しバックグラウンドAI推論を直列化する

- Status: Accepted
- Date: 2026-08-16

## Context

ADR-0066では蔵書AI整理をdurable batchとして永続化し、batch workerが対象本を1冊ずつclaimして候補を生成する方式を採用した。ADR-0069では要約と蔵書整理を設定画面の共通AIタスクキューへ集約したが、蔵書整理についてはbatch全体を1つのタスクとして投影していた。

この表示では、実際には各本が独立した `QUEUED` / `PROCESSING` / `FAILED` 状態を持っているにもかかわらず、ユーザーからは1つの巨大なジョブに見える。また、summary workerとlibrary workerは別々のWorkManager unique workとして実行されるため、両方が同時に起動すると、それぞれが `LocalModelManager` を生成して端末内LLMを同時利用する可能性がある。ローカルLLMはCPU/GPU・メモリ・電力負荷が大きいため、バックグラウンドAI処理を並列化する利点より負荷増大のリスクが大きい。

一方、ADR-0056とADR-0069で定めたとおり、summaryとlibraryのdurable task stateや操作意味論は各featureが所有し、`core` に記事・蔵書などの業務概念を持ち込まない方針は維持する必要がある。

## Decision

### 1. 共通AIタスクキューでは蔵書整理を1冊1タスクとして投影する

最新の `library_organization_batch_items` の各候補を、それぞれ独立した `AiTaskQueueItem` として表示する。

状態は次のように投影する。

- `QUEUED` -> `QUEUED`
- `PROCESSING` -> `RUNNING`
- `FAILED` / `SKIPPED` -> `FAILED`
- `PENDING_REVIEW` / `DEFERRED` / `APPLIED` / `REJECTED` -> `COMPLETED`
- AI全体停止中、またはlibrary batch個別停止中の `QUEUED` / `PROCESSING` -> `PAUSED`

タスクのタイトルには現在の蔵書snapshotから解決した書名を使い、sourceにはKindle、Audible、Google Play Books、ファイルサーバなどの蔵書sourceを表示する。蔵書がすでに同期対象から消えている場合のみsource IDへフォールバックする。

失敗またはスキップした候補は、共通キューからその1冊だけ再試行できる。候補レビュー自体は従来どおりlibrary featureが所有する。

### 2. feature固有のdurable queueは統合しない

summaryの `summary_tasks` とlibraryのbatch/item tableは統合しない。タスク状態、再試行、候補レビューなどfeature固有の意味論は各featureに残す。

したがって「同一キュー」は、ユーザーから見た共通AIタスク一覧と、後述する共通実行制御を意味する。異なるfeatureの永続データを1つの巨大な共通テーブルへ移行しない。

### 3. バックグラウンドのローカルAI処理はアプリプロセス内で同時に1つだけ実行する

`:core:background` に `LocalAiBackgroundTaskGate` を置き、`Mutex` により高負荷なバックグラウンドAI workerを直列化する。

summary workerとlibrary organization workerは、DBをclaimしてローカルモデルを利用する処理全体をこのgateのpermit内で実行する。これにより、両WorkManager jobが同時に起動しても、実際にAI処理を行うworkerは常に1つだけになる。

このgateはdurable task state、優先順位、記事ID、蔵書IDを知らない。プロセス再生成後はmutex自体も再生成されるが、同一プロセス内のWorkManager workerを直列化するという目的には十分である。永続的な中断・再試行は従来どおり各featureのDB状態で扱う。

### 4. 全体停止・充電時再開は既存の共有実行ポリシーを維持する

`LocalAiBackgroundExecutionPreferences` による全体停止と充電時自動再開は変更しない。全体停止時は各featureのWorkManager jobを停止し、durable itemを再開可能な状態へ戻す。

共通gateは「実行してよい場合に複数featureが同時推論しない」ための制御であり、pause/resume policyの代替ではない。

### 5. 公開リポジトリへユーザーデータを追加しない

テストでは架空の書名・IDだけを使用する。実在する蔵書名、ASIN、Personal Document ID、ユーザー固有タグ、AI生成候補、認証情報をfixture、ADR、ログへ追加しない。

## Consequences

### Positive

- 蔵書AI整理の待ち・実行・失敗状態を1冊単位で確認できる。
- 1冊の失敗をbatch全体の失敗として扱わず、その本だけ再試行できる。
- 要約と蔵書整理が同時に端末内LLMを利用せず、ピークCPU/GPU・メモリ・電力負荷を抑えられる。
- feature固有の永続状態とレビューworkflowを維持できる。
- `core:background` は業務概念を持たず、横断的な実行制御だけを担当する。

### Negative

- summaryとlibraryのWorkManager queue自体は別のため、厳密なFIFO順序を1つのDBで保証する構造ではない。
- 片方のworkerが複数タスクを連続処理している間、もう片方はgate待ちになる可能性がある。現在は端末負荷抑制を公平性より優先する。
- タスク一覧生成時に書名解決のためlibrary snapshotも読む。

## Supersedes

ADR-0069 section 2のうち、library organization queueを「1回のdurable batchを1タスクとして表示する」とした判断を廃止し、1冊1タスクの投影へ置き換える。

ADR-0069 section 5の「SummaryとLibraryの両workerが共通実行ゲートを守る」という方針を拡張し、pause gateだけでなく同時AI実行を防ぐ排他gateも共有する。

ADR-0066のdurable batch/item table、1冊ずつclaimする処理、候補レビュー方式は維持する。

## Relationship to existing ADRs

- ADR-0056: `core:ai-runtime` とfeature固有AI policyの境界を維持する。排他制御は業務概念を持たない `core:background` に置く。
- ADR-0066: libraryのdurable batch/item stateと候補レビューを維持し、共通キューへの投影粒度だけ変更する。
- ADR-0068: pause/resume policyはADR-0069によるsupersede後の共有方針を引き続き利用する。
- ADR-0069: 共通AI設定・タスクキューの基本方針を維持し、libraryの表示粒度とバックグラウンドAI同時実行制御を本ADRで更新する。
