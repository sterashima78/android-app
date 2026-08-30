# ADR-0220: 統合ビューの定期更新と新着通知を application-scope WorkManager に集約する

- Status: Accepted
- Date: 2026-08-30
- Refines: [ADR-0006](0006-durable-background-sync.md), [ADR-0192](0192-settings-feature-owns-cross-feature-presentation.md), [ADR-0200](0200-app-composition-module-boundary.md)

## Context

統合ビューは RSS、Reddit、YouTube、Gmail の未読を一つの画面へ投影しているが、これまで更新は画面上の ViewModel から明示的に実行する経路が中心だった。そのためアプリを開いていない期間には統合ビューの情報源を同じ周期で更新できず、新しい未読が追加されても統合された通知を出せなかった。

ADR-0006 は画面 lifecycle を越える durable background work に WorkManager を利用する方針を採用している。ADR-0200 は application-scope WorkerFactory と startup scheduler wiring を `:app:composition` が所有し、Worker から Application を service locator として利用せず constructor injection する方針を定めている。設定 UI は ADR-0192 により `:feature:settings:ui` が所有する。

Gmail は従来 `gmail-mail-sync` という独立した30分周期 WorkManager job を持っていた。統合更新 Worker と併存させると同じ Gmail データを別 job から重複取得し、さらに統合通知の「更新直前と直後の未読差分」を正確に判定できないため、周期同期の owner を一本化する必要がある。初回 Gmail 同期はページング・再開 checkpoint を持つ別責務なので統合しない。

Android WorkManager の periodic work は15分が最短周期で、Doze や電池最適化、constraints により実行時刻は遅延し得る。したがってユーザー設定は「正確な時刻」ではなく「おおよその更新間隔」として扱う。

## Decision

### 1. 統合ビューの周期更新を application-scope unique periodic work とする

`:app:composition` に `IntegratedRefreshWorker` と `IntegratedRefreshScheduler` を置き、`integrated-view-periodic-refresh` という unique periodic work として登録する。

- Worker は `AppWorkerFactory` から application-scope `AppContainer` を constructor injection される。
- Worker 内で Repository / DB / HTTP client の parallel graph を構築しない。
- UI ViewModel を background entry point から参照しない。
- startup 時と更新間隔・Wi-Fi制約の変更時に `ExistingPeriodicWorkPolicy.UPDATE` で同一 unique work を更新する。
- `BackgroundDataFetchPreferences` と `backgroundDataFetchConstraints()` を再利用し、既存の「Wi-Fi 接続中のみ取得」設定を統合更新にも適用する。

### 2. 更新間隔は15分以上の固定候補から選択する

設定画面の「バックグラウンド取得」に統合ビュー更新間隔を追加し、次から選択する。

- 15分
- 30分
- 1時間
- 3時間
- 6時間
- 12時間
- 24時間

既定値は1時間とする。保存値が候補外の場合は1時間へフォールバックする。設定変更後は unique periodic work を更新し、アプリ再起動を要求しない。

### 3. 周期更新は RSS、Reddit、YouTube、Gmail を一つの実行単位から開始する

`IntegratedRefreshWorker` は各 owner が公開する既存の更新 contract を呼び出す。

- RSS: Reddit feed を除く通常 feed を `RefreshFeedsUseCase` で更新する。
- Reddit: `RedditRepository.refreshAll()` を利用する。
- YouTube: `YouTubeRepository.refresh()` を利用する。
- Gmail: `MailRepository.sync(null)` を利用する。

個別 source の失敗で他 source の更新を中断しない。周期 Worker 自体は次回周期で再試行できるため、source 単位の失敗を理由に全体を retry loop へ入れない。

### 4. Gmail の従来周期 job を廃止し、初回同期だけ feature/mail に残す

既存の `gmail-mail-sync` periodic work は application startup で明示的に cancel する。`MailSyncScheduler.schedulePeriodic()` / `refreshPeriodicNetworkPolicy()` は既存 caller と upgrade 時の durable job cleanup を壊さない互換 entry point として、旧 periodic work の cancel を行う。

Gmail アカウント接続時の初回ページ同期、checkpoint、network retry は引き続き `MailSyncWorker` と `MailSyncScheduler.scheduleInitialPage()` が所有する。統合 Worker は初回同期の continuation を置き換えない。

### 5. 新着判定は同期前後の未読 identity 差分で行う

周期更新の直前と直後に次の未読 identity を取得し、`after - before` のみを新着とする。

- RSS / Reddit article: `article:<articleId>`
- YouTube: `youtube:<videoId>`
- Gmail: `mail:<accountId>:<threadId>`

未読総数の増減だけでは判定しない。同じ周期で既読化と新規追加が相殺されても、新しい identity を検出できるようにする。既存未読だけが残っている場合は再通知しない。

同期前または同期後の未読 snapshot 取得に失敗した場合、その実行回では新着差分を信頼できないため通知しない。失敗した snapshot を空集合へ置換すると既存未読を新着と誤判定し得るため、通知の false positive 回避を優先する。source refresh 自体は継続し、次回周期で再び差分判定する。

### 6. 通知と launcher badge は notification channel で表現する

新着 identity が1件以上ある場合だけ `integrated_view_updates` channel へ通知する。

- channel は badge 表示を許可する。
- notification の number には同期後の統合未読数を設定する。
- 本文には今回の新着件数と現在の未読件数だけを表示し、記事タイトル、メール件名、URL 等のユーザー content を通知生成コードやログへ固定値として保存しない。
- notification tap はアプリの launcher entry point を開く。
- `POST_NOTIFICATIONS` が未許可の場合、同期と未読保存は成功させ、通知だけを省略する。

launcher が数値 badge を表示するか dot として表示するかは launcher 実装に依存するため、Mosaic は channel badge 許可と notification number までを contract とする。

### 7. 通知 permission UX は app presentation、表示は settings feature が所有する

manifest の `POST_NOTIFICATIONS` 宣言は維持する。設定画面の「バックグラウンド取得」に新着通知の状態を表示し、未許可時のユーザー操作から `ActivityResultContracts.RequestPermission()` を起動する。

Android runtime permission API の呼び出しは app presentation に置き、`feature/settings:ui` は permission state と request callback だけを受け取る。これにより ADR-0192 の settings presentation ownership と、platform permission を app shell 側で扱う既存方針を維持する。

## Consequences

### Positive

- アプリを開いていない期間も統合ビューの主要 source を同じ設定周期で更新できる。
- 新着通知と badge が統合未読状態を基準に一度だけ更新される。
- 未読 snapshot の部分失敗を大量誤通知へ変換しない。
- Gmail の二重周期取得を避けられる。
- UI lifecycle と background execution が分離され、既存 application-scope WorkerFactory 方針を維持できる。
- Wi-Fi 制約と周期設定を一つの background preference に集約できる。

### Negative

- WorkManager の periodic work は exact timer ではないため、選択した間隔より実行が遅れることがある。
- 一つの Worker が複数 source を逐次更新するため、source 数やネットワーク状況によって1回の実行時間が伸びる。
- launcher によって badge の見え方が異なる。
- snapshot 取得に失敗した実行回では、実際に新着があっても通知を見送る。
- Gmail の周期同期 owner が feature/mail 単独から application-scope integrated refresh へ移るため、旧 durable work の cleanup を維持する必要がある。

## Verification

- `BackgroundDataFetchPreferencesTest` で既定1時間と interval persistence を検証する。
- `IntegratedRefreshWorkerTest` で同期前後の identity 差分だけが新着になること、既存未読だけでは新着が空になること、前後いずれかの snapshot が欠落した場合は通知対象を作らないことを検証する。
- settings UI / app presentation の compile と既存 UI tests を通す。
- `MailSyncScheduler` の初回同期 continuation が維持され、旧 `gmail-mail-sync` periodic work が startup で cancel されることを source/worker tests で確認する。
- `verifyArchitecture`、unit tests、Lint、public repository verifier を通す。
- 実端末で通知 permission を許可し、新着取得後に notification shade と launcher badge/dot が更新されることを確認する。
- 実端末で permission 未許可時にも background sync 自体が継続することを確認する。

## Public repository review

本変更が保存・表示する設定は更新間隔、Wi-Fi制約、OS notification permission state のみである。notification 本文には件数だけを使用し、メール本文・件名、記事本文・タイトル、購読 URL、OAuth token、認証情報、private path、診断 artifact を新規にリポジトリへ追加しない。

ソースコード、テスト、ADR、current architecture documentation の差分について credential-like literal、実ユーザーデータ、private endpoint が含まれないことを PR 作成前に独立レビューする。

## References

- [ADR-0006](0006-durable-background-sync.md)
- [ADR-0192](0192-settings-feature-owns-cross-feature-presentation.md)
- [ADR-0200](0200-app-composition-module-boundary.md)
- [Android Developers: Define work requests](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- [Android Developers: PeriodicWorkRequest](https://developer.android.com/reference/androidx/work/PeriodicWorkRequest)
- [Android Developers: Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Android Developers: NotificationChannel.setShowBadge](https://developer.android.com/reference/android/app/NotificationChannel#setShowBadge(boolean))
- [Android Developers: Notification.Builder.setNumber](https://developer.android.com/reference/android/app/Notification.Builder#setNumber(int))
