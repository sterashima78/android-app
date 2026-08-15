# ADR-0068: 要約キューを一時停止し充電時に自動再開できるようにする

- Status: Accepted
- Date: 2026-08-16

## Context

記事要約、ブックマークのAIタグ付け、未要約ブックマークのバックフィルは、端末内LLMを利用するためCPU・メモリ負荷と電池消費が大きい。ADR-0051 と ADR-0067 により処理は WorkManager の永続キューへ集約されているが、ユーザーが電池残量を優先したいときにキュー全体の自動実行を止める手段がなかった。

既存のタスク単位の「停止」は個別ジョブの状態を変更する操作であり、後から追加されるタスクまで止めるものではない。電池消費を抑える目的では、タスクデータを破棄せず、キューの実行だけを一時的に抑止する別の状態が必要である。

また、ユーザーが端末を充電器へ接続したあとまで手動で再開操作を要求すると、停止状態が不要に長く残りやすい。Android の WorkManager は充電中を実行制約として扱えるため、常駐サービスや独自の電源接続 BroadcastReceiver を追加せずに再開契機を永続化できる。

## Decision

### 1. タスク状態とは独立したキュー全体の一時停止状態を持つ

`:feature:summary:data` が SharedPreferences に次を保持する。

- `paused`: 要約キュー全体の自動実行を一時停止しているか
- `resume_when_charging`: 一時停止中に充電状態になったら自動再開するか

初期値は `paused = false`、`resume_when_charging = true` とする。既存ユーザーは更新後も従来どおり自動実行され、ユーザーが一時停止した場合だけ新しい制御が働く。

一時停止中も `summary_tasks` への enqueue は受理する。タスクは queued のまま保持し、要約結果や履歴を削除しない。これにより、RSS の「あとで読む」や未要約ブックマークのバックフィルから新しい要求が来ても失われない。

### 2. 一時停止時は実行中の unique work をキャンセルし、running タスクを queued に戻す

一時停止を有効にしたら、`article-summary-queue` の unfinished work を `cancelUniqueWork` でキャンセルする。`SummaryWorker` は CoroutineWorker のキャンセルを伝播する既存実装を維持し、キャンセル後に `requeueInterruptedSummaryTasks()` で running タスクを queued に戻す。

個々のタスクを stopped に変更しない。stopped はユーザーが特定タスクを明示的に停止した状態として意味を維持し、キュー全体の一時停止とは区別する。

一時停止中に `enqueue`、`kick`、個別タスクの resume が呼ばれても通常の `SummaryWorker` は新規スケジュールしない。

### 3. 充電時の自動再開は充電制約付き OneTimeWorkRequest で行う

`resume_when_charging` が有効な一時停止状態では、`SummaryResumeOnChargingWorker` を unique work として登録する。WorkRequest には `Constraints.Builder().setRequiresCharging(true)` を設定し、端末が充電状態になったときだけ実行可能にする。

再開 Worker は実行直前にも `paused` と `resume_when_charging` を確認し、設定が変更済みなら何もしない。条件が有効なら `paused` を解除し、既存の `SummaryWorker` を再度 kick する。

充電状態は「再開の契機」として扱う。充電後に電源から外れた際、自動的に再停止はしない。継続して充電中だけ実行するポリシーとは分ける。

再開 Worker の登録は best effort とし、タスク enqueue の成否とは分離する。登録に失敗してもタスク自体を failed にしない。次回の enqueue、kick、設定変更時に再登録を試みる。

### 4. タスクキュー画面でキュー全体の実行方針を操作する

既存のタスクキュー画面上部に次の2設定を追加する。

- 「要約タスクを一時停止」
- 「充電時に自動再開」

画面はタスク一覧と同じ1秒ポーリングの中で実行方針も再読込し、WorkManager や Activity の寿命に依存しない状態表示を維持する。

一時停止中であることは個別タスクの stopped 表示とは別に、キュー全体の状態として表示する。

### 5. feature ownership を維持する

実行方針、SharedPreferences、充電再開 Worker、WorkManager のスケジューリングは `:feature:summary:data` が所有する。UI は `SummaryTaskQueueRepository` の domain 契約を通して状態取得と変更を行い、WorkManager や SharedPreferences を直接参照しない。

DBスキーマ変更は行わない。キュー全体の一時的な実行方針は要約タスクそのものの永続ドメイン状態ではないため、既存の `summary_tasks` に列を追加しない。

## Consequences

### Positive

- 電池消費を優先したいとき、要約・タグ付け処理をキュー全体で止められる。
- 一時停止中に追加されたタスクも失われず、後でまとめて処理できる。
- 充電器へ接続するとユーザー操作なしで処理を再開できる。
- 常駐サービスや独自 BroadcastReceiver を増やさず、WorkManager の永続制約を利用できる。
- 個別タスクの stopped とキュー全体の paused の意味を分離できる。

### Negative

- SharedPreferences と WorkManager の2箇所に実行制御状態が存在するため、再開 Worker は実行時に設定を再確認する必要がある。
- 充電中に一時停止し、充電時自動再開が有効な場合は再開 Worker がすぐ実行可能になる。
- 一時停止操作は実行中 Worker のキャンセルを伴うため、処理途中の推論結果は破棄され、再開後にそのタスクを先頭から実行する場合がある。

## Relationship to existing ADRs

- ADR-0029: 要約タスクの durable state とUIポーリング方式を維持し、キュー全体の実行方針だけを追加する。
- ADR-0051: WorkManager の永続要約キューを維持し、そのスケジューリングに一時停止ゲートを追加する。
- ADR-0067: 未要約ブックマークのバックフィルは一時停止中もタスク投入まで行い、実際のローカルAI処理だけを待機させる。
- ADR-0055: 新規ADRとして現時点の最大番号より大きい一意な番号を割り当てる。
