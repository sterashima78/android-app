# 6. メール

- Gmail連携を提供する。
- 認証済みアカウントのメールを取得し、未読、スター、アーカイブ等の状態を扱う。
- HTMLメールを表示できる。
<!-- formal-requirement
id: MAIL-INITIAL-SYNC-001
models:
  - spec-models/quint/mail_initial_sync_lifecycle.qnt
-->
- Mailの初回同期は画面lifecycleに依存しないページ単位のdurable background workとして実行し、同期状態、処理済み件数、次page checkpoint、同期開始時の履歴checkpoint、同期generationをMail-owned durable stateへ保存する。
- 各page workはenqueue時の期待checkpointを持ち、durable checkpointと一致する場合だけそのpageを処理する。一致しない古いworkは同じpageを重複適用せず、現在のdurable checkpointから継続workを再構築する。
- page処理で次checkpointを保存してから次workをenqueueするまでにprocess interruptionが起きても、stale workまたは次回同期入口から現在checkpointを再予約して継続できる。
- 一時的なnetwork / retryable API failureではcheckpointを維持して待機状態とし、同じpageを再試行する。ユーザー操作が必要な認可失敗またはnon-retryable failureはerrorとして確定する。
- 最終pageだけが今回generationで確認できなかった非ローカル保持threadを整理し、履歴checkpointと完了時刻を保存してsync stateをidleへ戻す。
<!-- /formal-requirement -->
- メールのlocal cache / stateは Mail Context が所有する。
- OAuth credentialやtokenを通常のdatabase backupへ含めない。


## 形式モデル

- [Quint: `mail_initial_sync_lifecycle.qnt`](../../spec-models/quint/mail_initial_sync_lifecycle.qnt) — page checkpoint、checkpoint保存後の中断window、stale work reconciliation、一時障害retry、terminal error、最終page完了を検査する。
