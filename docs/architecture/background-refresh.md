# Background Refresh

この文書は、Mosaic の通常コンテンツ取得における現在の background refresh ownership と通知境界を示す。AI、backup、widget 等の個別 durable work は各 current architecture document と ADR を参照する。

## Integrated periodic refresh

統合ビューの主要 source である RSS、Reddit、YouTube、Gmail の周期更新は `:app:composition` の application-scope WorkManager job が所有する。

```text
Application startup / Settings
          |
          v
IntegratedRefreshScheduler
          |
          v
integrated-view-periodic-refresh
          |
          v
IntegratedRefreshWorker
  |        |        |        |
 RSS     Reddit   YouTube   Gmail
```

- durable job は `integrated-view-periodic-refresh` という unique periodic work とする。
- Worker dependency は `AppWorkerFactory` から constructor injection し、Worker 内で Repository graph を再構築しない。
- UI ViewModel は background entry point から呼ばない。
- source owner が公開する既存 Repository / use case contract を利用する。
- source 単位の失敗は他 source の取得を止めない。
- WorkManager periodic execution は exact timer と扱わない。Doze、battery optimization、network constraints により遅延し得る。

## Refresh interval

更新間隔は `BackgroundDataFetchPreferences` に保存し、既定値を1時間とする。選択可能値は次の通り。

- 15分
- 30分
- 1時間
- 3時間
- 6時間
- 12時間
- 24時間

WorkManager の periodic work の最短周期が15分であるため、それ未満の設定は提供しない。設定変更時は `ExistingPeriodicWorkPolicy.UPDATE` により同じ unique periodic work を更新する。

既存の「Wi-Fi 接続中のみ取得」は統合 refresh にも適用する。network constraint が満たされない期間は、設定した周期を過ぎても取得を開始しない。

## Gmail migration boundary

Gmail の初回同期と周期同期は責務を分ける。

- 初回同期: `feature/mail:data` の `MailSyncWorker` がページング checkpoint、network retry、continuation を所有する。
- 周期同期: application-scope `IntegratedRefreshWorker` が他 source と同じ実行単位で `MailRepository.sync(null)` を呼ぶ。

旧バージョンの `gmail-mail-sync` periodic work は startup で cancel する。`MailSyncScheduler.schedulePeriodic()` と `refreshPeriodicNetworkPolicy()` は upgrade 時の旧 durable work cleanup 用 compatibility entry point とし、新しい periodic work を作成しない。

## New-item detection

通知対象は同期前後の未読 identity の差分 `after - before` とする。件数だけの差では判定しない。

```text
before unread identities
        |
        | refresh RSS / Reddit / YouTube / Gmail
        v
after unread identities
        |
        v
after - before = newly fetched unread identities
```

identity namespace は source 間衝突を避ける。

- RSS / Reddit: `article:<articleId>`
- YouTube: `youtube:<videoId>`
- Gmail: `mail:<accountId>:<threadId>`

新着 identity が空なら通知を更新しない。これにより既存未読だけを周期ごとに再通知しない。

同期前または同期後の未読 snapshot の取得に失敗した場合、その実行回では新着差分を信頼できないため通知しない。同期処理自体は継続し、次回周期で再び差分を判定する。これにより同期前 snapshot の欠落を空集合として扱って既存未読を大量に新着と誤判定することを防ぐ。

## Notification and badge

新着が1件以上ある場合、`integrated_view_updates` notification channel に通知する。

- channel は launcher badge を許可する。
- notification number は同期後の統合未読総数とする。
- notification 本文は今回の新着件数と未読総数だけを表示する。
- 記事タイトル、メール件名、URL、本文、OAuth 情報を通知 payload や固定ログへ追加しない。
- notification tap は launcher entry point を開く。
- launcher が数字 badge と dot のどちらを表示するかは launcher implementation に依存する。

`POST_NOTIFICATIONS` が未許可でも background refresh は停止しない。通知だけを省略する。

## Permission presentation

Android notification runtime permission は app presentation が platform API を所有し、`feature/settings:ui` は permission state と request callback だけを受け取る。

設定画面の「バックグラウンド取得」では次を表示する。

- permission 許可済み: 新着通知が有効であることを表示する。
- permission 未許可: ユーザー操作から `ActivityResultContracts.RequestPermission()` を起動する入口を表示する。

Worker から permission dialog を起動しない。permission request は通知の用途が分かる settings context で明示的なユーザー操作に応じて行う。

## Testing boundary

最低限、次を自動テストする。

- background refresh interval の既定値と永続化。
- 同期前後の identity 差分による新着判定。
- 既存未読だけでは新着が発生しないこと。
- 同期前または同期後の snapshot が欠落した場合は通知対象を作らないこと。
- mail の初回 sync continuation が周期 sync migration 後も維持されること。
- app composition の WorkerFactory / startup scheduler ownership が architecture rule から逸脱していないこと。

notification shade と launcher badge/dot の見え方、WorkManager の実際の遅延挙動は platform / launcher の影響を受けるため、release candidate APK で実端末確認も行う。

## Sources

- [ADR-0006](../adr/0006-durable-background-sync.md)
- [ADR-0192](../adr/0192-settings-feature-owns-cross-feature-presentation.md)
- [ADR-0200](../adr/0200-app-composition-module-boundary.md)
- [ADR-0220](../adr/0220-integrated-background-refresh-and-notification.md)
- [Android Developers: Define work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- [Android Developers: PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)
- [Android Developers: Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Android Developers: NotificationChannel](https://developer.android.com/reference/android/app/NotificationChannel)
- [Android Developers: Notification.Builder](https://developer.android.com/reference/android/app/Notification.Builder)
