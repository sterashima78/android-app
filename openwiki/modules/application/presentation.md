---
type: module
title: App shell・navigation と route の接続
description: Compose の drawer、top / bottom bar、navigation、feature route と外部
  action callback を接続する。
tags:
  - compose
  - navigation
  - presentation
  - module
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T04:30:12.060Z
sources:
  - id: openwiki-source-d3c58638caf6fd74f5638024
    resource: repo://app/presentation/build.gradle.kts
  - id: openwiki-source-18cea409f4379ab24890a4f0
    resource: repo://app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppNavHost.kt
  - id: openwiki-source-d3cbca75a5805c2823b92d9e
    resource: repo://app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/LanWebServerDialogHost.kt
  - id: openwiki-source-5caebea6adf147a65bdf9136
    resource: repo://app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/RootBackAction.kt
  - id: openwiki-source-fa94c0fbaaa0d7674b16244c
    resource: repo://app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/YomitoriApp.kt
generated: { by: "codex", at: "2026-10-09T04:30:12.060Z" }
---

# App shell・navigation と route の接続

## 画面 shell の責務

`:app:presentation` は App shell と feature 画面を接続する Compose Android library である。[YomitoriApp](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/YomitoriApp.kt) は NavHostController、AppRouteDependencies、navigation request Flow と platform callback を受け取る。drawer、snackbar、top / bottom bar を配置し、選択 route に応じた feature NavGraph と overlay を呼ぶ。AppRouteDependencies は [composition](composition.md) が作り、外部 URL、Web server、終了、生体ロック変更などは [app](app.md) の callback で実行する。

## 主要な構成要素と公開 API

| 宣言・種類 | 責務・入出力 | 根拠 |
| --- | --- | --- |
| `YomitoriApp` Composable | NavController、route dependencies、semantic navigation Flow、lock 状態、platform callback を受け shell と feature route を接続する。 | [root](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/YomitoriApp.kt)。 |
| `YomitoriTheme` Composable | MaterialTheme の色/typography を適用して content を描画する。 | [theme](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/Theme.kt)。 |
| `LanWebServerDialogHost` Composable | visible、controller、dismiss を受け、LAN server 状態と通知・ローカルネットワーク permission launcher を dialog へ接続する。 | [LAN host](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/LanWebServerDialogHost.kt)。 |
| `AppNavigationTarget` enum | exe 向け INTEGRATED/BOOKMARKS/LIBRARY/TASKS/VIDEO/WEB_SERVER の semantic target。 | [target](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppNavigationTarget.kt)。 |
| `AppSection` enum | drawer の section と label。route identity とは別に section を表現する。 | [section](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppSection.kt)。 |

## 内部構成と API・接続の関係

| 構成要素 | 主要メソッド・関係 | 根拠 |
| --- | --- | --- |
| `AppNavHost` / `navigateTopLevel` | NavHost を生成し graph 登録を呼ぶ。top-level 遷移は許可 route を検査し、同じ route は無操作、singleTop と状態保存/復元を使う。 | [NavHost](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppNavHost.kt)。 |
| `registerHomeDestination` / `registerRssDestinations` / `registerRedditDestinations` / `registerBookmarkDestinations` / `registerSingleFeatureDestinations` | NavGraphBuilder 拡張。composition の factory で destination owner の feature ViewModel を作り、feature Route と navigation/platform callback をつなぐ。 | [home](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppHomeNavGraph.kt)、[RSS](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppRssNavGraph.kt)、[Reddit](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppRedditNavGraph.kt)、[bookmark](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppBookmarkNavGraph.kt)、[単独 feature](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppSingleFeatureNavGraph.kt)。 |
| `AppDrawerContent` / `AppTopBar` / `AppBottomBar` | section/tab 選択と title/progress/unread action を caller の callback に戻す chrome。 | [chrome](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppNavigationChrome.kt)。 |
| `AppTopBarRoute` | destination owner の feature 状態を読み、top bar の引数と操作に射影する。 | [top bar host](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppTopBarRoute.kt)。 |
| `FeatureMessageSource` / route 拡張関数 | `featureMessageSources`、`usesGlobalTopBar`、`usesSummaryOverlay`、`usesBookmarkEditOverlay`、`appSection`、`screenTitle`、`defaultRoute` が message/overlay/chrome の対象を定義する。 | [navigation spec](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/AppNavigationSpec.kt)。 |
| `FeatureMessageEffects` / `FeatureMessageEffect` | 対象 source の message Flow を destination owner の ViewModel から snackbar へ接続する。 | [message effects](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/FeatureMessageEffects.kt)。 |
| `BookmarkEditOverlay` / `SummaryOverlay` | 条件に該当する route だけ feature dialog/controller を表示する。 | [overlays](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/FeatureOverlays.kt)。 |
| `LibraryRoute` / `MailRouteHost` / `CalendarRoute` / `SettingsRoute` | factory/bundle を UI に渡し、authorization/document/calendar/notification の Activity Result を Composable lifetime で接続する。 | [Library](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/LibraryRoute.kt)、[Mail](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/MailRouteHost.kt)、[Calendar](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/CalendarRoute.kt)、[Settings](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/SettingsRoute.kt)。 |
| `VideoRoute` / `GameRouteHost` | playback/feature host と orientation を接続し、dispose 時に元の orientation を戻す。game fullscreen を root callback へ返す。 | [Video](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/VideoRoute.kt)、[Game](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/GameRouteHost.kt)。 |
| `RootBackAction` / `rootBackAction` | drawer open → app終了、履歴あり → pop、root → drawer open の優先度を純粋関数で決める。 | [戻る判定](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/RootBackAction.kt)。 |

## 代表的な遷移フロー・状態と失敗

1. exe が `YomitoriApp` に semantic target Flow を渡す。`LaunchedEffect` が target の `appRoute` を解決して `navigateTopLevel` を呼ぶ。
2. WEB_SERVER は Settings route に移動した上で `opensWebServerDialog` の判定から exe の dialog callback を呼ぶ。
3. current back-stack entry を owner とし、graph は feature factory/Route、message effects は対象 Flow、overlay は route capability を接続する。
4. drawer/tab の操作も同じ top-level 遷移を使う。BackHandler は `rootBackAction` の結果で pop/開く/終了 callback を実行する。

root が保持するのは drawer/snackbar/controller/fullscreen 等の shell state で、feature task や durable repository ではない。`shouldHideAppChrome` は game route と fullscreen の組合せを判定し、X route は inset を個別調整する。不明 route は mapping/遷移検査で失敗するため、新 route の追加では route 集合、section/title、graph、message/overlay 条件を同時に確認する。LAN server host は通知権限に加え SDK 条件に該当するローカルネットワーク権限も検査し、必要権限が揃わなければ server を開始しない。launcher の失敗や permission 拒否は各 route host で feature の結果/message に戻し、exe の lock/Custom Tab lifetime を所有しない。
## navigation と lifetime

navigation request を LaunchedEffect で収集して top-level route へ移動し、Web server target では dialog callback を呼ぶ。current back-stack entry を ViewModelStoreOwner として feature message / route に渡すので、feature ViewModel の状態・message は該当画面の host で扱う。root は feature の repository や Worker を構築しない。

[RootBackAction](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/RootBackAction.kt) は drawer が開いていれば終了、閉じていて履歴があれば pop、root なら drawer を開くという優先順を持つ。game fullscreen 時の app chrome、X の inset など画面横断の shell 調整もここに置く。個別 feature の一覧・編集判断は feature UI 側を追う。

## 依存境界と変更の確認

[verifyPresentationBoundary](../../../app/presentation/build.gradle.kts) は実行 app / feature Data への Gradle dependency、concrete Data import、DB / WorkManager infrastructure、executable platform 型を拒否する。YomitoriApp の feature Activity Result launcher 所有と feature state の直接 collection も検査する。

[RootBackActionTest](../../../app/presentation/src/test/kotlin/dev/terashima/yomitorirss/ui/RootBackActionTest.kt) は戻る優先順位、AppNavigationTargetTest と AppNavigationSpecTest は route mapping、YomitoriAppLayoutTest は shell の確認入口になる。新しい画面は feature Domain / UI 契約と route factory を接続し、platform 処理は callback を追加して境界検証を通す。

仕様・設計の正本: [architecture/module-map.md](../../../docs/architecture/module-map.md)、[architecture/code-organization.md](../../../docs/architecture/code-organization.md)。
