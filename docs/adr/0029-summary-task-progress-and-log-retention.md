# ADR-0029: 要約タスクの進捗を永続化し完了ログを期限削除する

- Status: Accepted
- Date: 2026-08-13

## Context

記事要約は `SummaryWorker` が WorkManager 上でバックグラウンド実行し、タスクキュー画面は `summary_tasks` を1秒間隔で再読込して状態を表示している。従来の `summary_tasks` は queued / running / completed などの状態と開始・終了時刻だけを保持していたため、長文記事で ADR-0027 の階層要約が複数回の推論を行っていても、ユーザーには長時間「実行中」としか見えなかった。

また、完了・失敗・キャンセル済みのタスクは履歴として残り続ける。タスクキューは運用状況を確認するためのジョブログであり、記事要約そのものは `article_summaries` に保存されるため、ジョブログを無期限に保持する必要はない。一方、停止中タスクは再開可能な作業状態なので削除してはいけない。

ADR-0006 では、バックグラウンド処理の進捗をDBへcheckpointし、UIが短い間隔で再読込する方式を採用している。要約キューも既に同じDBポーリング構成なので、別の監視基盤を導入せずこの方式を適用できる。

## Decision

### 1. 実行中の進捗を `summary_tasks` に保存する

`summary_tasks` に次のnullable列を追加する。

- `progress_stage`: 現在の処理フェーズ
- `progress_current`: フェーズ内の現在位置
- `progress_total`: フェーズ内の総数

DBバージョンを12へ上げ、既存データを保持したまま列追加で移行する。

進捗は running のタスクにだけ意味を持つ。enqueue、再開、中断からの再キュー、完了、失敗、停止、キャンセルの状態遷移では以前の進捗値をクリアし、古い進捗が別の実行へ漏れないようにする。

### 2. 実処理に対応したフェーズを表示する

タスクキューでは次のフェーズを表示する。

- 記事本文を取得中
- AIモデルを読み込み中
- 通常の要約を生成中
- 長文の分割要約 `n/N`
- 中間要約の統合 `n/N`
- 最終要約を生成中

記事取得、モデル読込、通常生成、最終生成は処理量を事前に確定できないため不定進捗バーとする。

ADR-0027 の分割要約と中間統合は処理対象数を確定できるため、`n/N` と確定進捗バーを表示する。推論中の「現在処理している要素」を `n` として表示し、確定進捗バーはその直前までに完了した要素数を表す。実際の推論単位に基づかない時間推定パーセンテージは表示しない。

モデル読込フェーズは `LocalModelManager.summaryProgress` の既存状態を利用する。階層要約側は観測用の progress callback を追加するが、要約アルゴリズム、生成結果、キャッシュ世代 `hierarchical-v1` は変更しない。

### 3. UIは既存の1秒ポーリングを継続する

タスクキュー画面は既存どおり `SummaryTaskQueueRepository.listTasks()` を1秒ごとに再読込する。進捗更新のためだけに WorkManager の `WorkInfo` 監視や別のFlow基盤を追加しない。

これにより、プロセス再生成後もDBに保存された現在フェーズを表示でき、UIとWorkerのライフサイクルを分離したままにする。

### 4. 完了ジョブログは30日保持する

`summary_tasks` のうち、次の終端状態で `finished_at` が30日より古い行を削除する。

- completed
- failed
- cancelled

stopped は再開可能なので削除しない。queued / running も削除対象外とする。

要約結果を保存する `article_summaries` はこの削除の対象にしない。ジョブログの削除によって要約本文が失われることはない。

### 5. クリーンアップはSummary feature所有のWorkManagerジョブとする

`SummaryTaskLogCleanupWorker` を `:feature:summary:data` に置き、1日ごとの unique periodic work として `ExistingPeriodicWorkPolicy.KEEP` で登録する。

要約タスクのenqueue、再開、またはタスクキューのkick時にスケジュールを保証する。一度登録されたPeriodicWorkはWorkManagerが永続化するため、アプリ起動ごとに別ジョブを増やさない。

クリーンアップの登録は保守処理としてベストエフォートにし、登録失敗だけを理由に要約タスクの受理や実行を失敗扱いにしない。次回のenqueue、再開、kick時に再び登録を試みる。

クリーンアップ実行時の一時的なDBアクセス失敗は `Result.retry()` とする。ただしWorkManagerからのキャンセルはCoroutineのキャンセルとして伝播させ、retryへ変換しない。

## Consequences

### Positive

- 長時間の要約でも現在どこまで処理しているか確認できる
- 長文要約では実際のチャンク数に基づく進捗を表示できる
- UIがWorkerやActivityの生存期間に依存しない
- ジョブログが無期限に増えない
- 停止中タスクと要約結果は保持される
- 既存のDBポーリングとWorkManagerのownershipを再利用できる

### Negative

- `summary_tasks` に進捗列が3つ増える
- 実行中に進捗更新のDB writeが増える。ただし更新はフェーズ変更や推論単位ごとであり、生成トークンごとには行わない
- 短い記事の生成量は事前に確定できないため、確定パーセンテージは表示できない
- 完了後30日を超えた失敗履歴はタスク画面から参照できなくなる

## Relationship to existing ADRs

- ADR-0006 の「バックグラウンド処理の進捗をDBへcheckpointし、UIが再読込する」方針を要約キューへ適用する
- ADR-0027 の階層要約アルゴリズムは維持し、処理単位を進捗として観測可能にするだけである
- ADR-0020 の推論Engine再利用は変更しない
