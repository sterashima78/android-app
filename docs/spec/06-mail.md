# 6. メール

- Gmail連携を提供する。
- 認証済みアカウントのメールを取得し、未読、スター、アーカイブ等の状態を扱う。
- HTMLメールを表示できる。
- メールのlocal cache / stateは Mail Context が所有する。
- OAuth credentialやtokenを通常のdatabase backupへ含めない。

<!-- formal-requirement
id: MAIL-INITIAL-SYNC-CHECKPOINT-001
models:
  - spec-models/quint/mail_initial_sync_checkpoint.qnt
-->
- Mail初回同期はアカウントごとにdurableなpage checkpoint、同期generation、開始時の履歴checkpoint、同期状態を保持し、processや画面のlifecycleを越えて継続できる。
- 各初回同期workerはenqueue時に期待するpage checkpointだけをbounded metadataとして持ち、実行時の期待checkpointがdatabase上の現在checkpointと一致する場合だけそのpageを取得・反映する。不一致workerは古いworkとしてpage処理を行わず、database上の現在checkpointから継続workを再調整する。
- 次pageがある場合は次checkpointをdatabaseへ先に保存し、そのcheckpointを使う継続workを予約する。checkpoint保存後にprocess interruptionが発生して古いworkerが再実行されても、stale判定から現在checkpointへ復旧できる。
- 一時的なnetwork / server failureでは現在checkpointとgenerationを保持してretry可能にし、認証解決が必要または非retryable errorではerror状態を表示してcheckpointを保持する。再開時は既存generationがあれば同じcheckpointから続行し、generationがなければ新しい初回同期を開始する。
- 最終pageの完了時だけgenerationに属さない古いlocal threadを整理し、履歴checkpointを確定して初回同期用page checkpoint・開始履歴・generationを消去し、IDLEへ遷移する。
<!-- /formal-requirement -->

## 形式モデル

- [Quint: `mail_initial_sync_checkpoint.qnt`](../../spec-models/quint/mail_initial_sync_checkpoint.qnt) — 初回同期のdurable page checkpoint、stale work reconciliation、一時失敗からの再開、最終pageでのgeneration完了を検査する。
