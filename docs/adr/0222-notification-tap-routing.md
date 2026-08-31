# ADR-0222: notification tap を意味的な app navigation request へ統一する

- Status: Accepted
- Date: 2026-08-31
- Refines: [ADR-0200](0200-app-composition-module-boundary.md), [ADR-0220](0220-integrated-background-refresh-and-notification.md)

## Context

Mosaic には background refresh の新着通知と LAN Web サーバの foreground-service 通知がある。新着通知は launcher entry point を開くだけで、ユーザーが通知内容に対応する統合ビューへ到達する保証がなかった。一方、Web サーバ通知は notification 本体の tap を停止操作へ割り当てており、通知本体を「状態に対応する画面を開く入口」として扱う操作モデルと一致していなかった。

executable `:app` は ADR-0200 により external Intent routing を所有し、widget 等の entry point は `IncomingIntentHandler` で `AppNavigationTarget` へ変換して app presentation に渡している。notification producer が Compose route 文字列や Activity 内部状態を直接知るのではなく、この既存境界を再利用する必要がある。

## Decision

### 1. notification 本体の tap は通知対象に対応する view を開く

notification の `contentIntent` は破壊的・状態変更操作に使わず、通知が表す状態を確認・操作できる view を開く。

- 統合 refresh の新着通知: 統合ビューを開く。
- LAN Web サーバ通知: 設定画面へ遷移したうえで既存の Web サーバ管理 dialog を開く。

Web サーバ停止は notification action の「停止」に残し、notification 本体の tap からは停止しない。

### 2. notification producer は semantic Intent action だけを公開する

producer と executable app entry routing の間は narrow launch contract を共有する。

- `IntegratedRefreshNotificationContract.ACTION_OPEN_INTEGRATED`
- `LanWebServerLaunchContract.ACTION_OPEN_SERVER`

producer は feature route 文字列、Compose navigation controller、Activity の overlay state を参照しない。launcher Intent に semantic action を設定した explicit `PendingIntent` を `contentIntent` に使用する。

`IntegratedRefreshNotificationContract` は application-scope producer と executable shell の間だけで共有する固定文字列 contract として `:app:composition` から公開する。これは ADR-0200 の公開面制限に対する narrow exception とし、runtime dependency graph、Repository、Data implementation、UI implementation を追加公開しない。feature-owned producer である LAN Web サーバは同じ contract を `:feature:web:domain` が所有する。

### 3. `IncomingIntentHandler` が notification action を `AppNavigationTarget` へ解決する

`IncomingIntentHandler` が notification action を一度だけ consume し、次の semantic target へ変換する。

- `ACTION_OPEN_INTEGRATED` -> `AppNavigationTarget.INTEGRATED`
- `ACTION_OPEN_SERVER` -> `AppNavigationTarget.WEB_SERVER`

`AppNavigationTarget` から concrete feature route への解決は `:app:presentation` が所有する。Web サーバの場合は settings route へ遷移後、app-shell が既存 `onOpenWebServer` capability を呼び出して管理 dialog を表示する。

### 4. cold start / warm start / app lock 後を同じ entry routing で扱う

notification tap は launcher Activity への explicit `PendingIntent` とし、`MainActivity.onCreate()` / `onNewIntent()` の既存 `IncomingIntentHandler` 経路を利用する。app lock 中は既存の unlock 後 intent consumption に従い、解除後に対象 view を開く。

### 5. notification payload にユーザー content を追加しない

routing のために追加するのは固定 semantic action のみとする。記事タイトル、メール件名、URL、LAN bootstrap token、OAuth credential 等を Intent extra や repository source に追加しない。

## Consequences

### Positive

- 通知を tap した直後に通知内容へ対応する view へ到達できる。
- notification producer が presentation route や Activity implementation へ依存しない。
- widget と notification が同じ executable external Intent routing 境界を利用する。
- Web サーバ停止のような破壊的操作を notification 本体の tap から分離できる。

### Negative

- notification producer と app entry routing の間に small launch contract が増える。
- `:app:composition` の公開面に固定 semantic action contract が1つ増える。
- route だけでは表現できない Web サーバ dialog は app-shell callback との組み合わせが必要になる。

## Verification

- `NotificationLaunchRoutingTest` で notification action から semantic target への解決を検証する。
- `AppNavigationTargetTest` で integrated / Web server target の concrete route 解決と Web server dialog request 判定を検証する。
- existing widget Intent routing tests を維持し、notification action 追加で既存 action を誤 consume しないことを確認する。
- `verifyArchitecture`、unit tests、Lint、public repository verifier を通す。
- release candidate APK で cold start、warm start、app lock 後の notification tap を確認する。
- Web サーバ通知では本体 tap で管理 dialog が開き、「停止」action だけが service を停止することを確認する。

## Public repository review

追加する Intent action は固定の application contract 文字列だけである。通知 routing に実ユーザー content、URL、LAN bootstrap token、credential、private endpoint、diagnostic artifact を追加しない。

## References

- [ADR-0200](0200-app-composition-module-boundary.md)
- [ADR-0220](0220-integrated-background-refresh-and-notification.md)
- [Android Developers: Create a notification](https://developer.android.com/develop/ui/views/notifications/build-notification)
- [Android Developers: PendingIntent](https://developer.android.com/reference/android/app/PendingIntent)
