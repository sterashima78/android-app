---
type: module
title: X UI：専用WebViewとカスタマイズの実行
description: X限定のCSS/JavaScript注入、外部リンク分岐、renderer回復と全画面メディアを説明する。
tags:
  - x
  - ui
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-cddac7e11298ec5e5eb06085
    resource: repo://feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XMediaWebChromeClient.kt
  - id: openwiki-source-4c47c0705be37cba23fa159a
    resource: repo://feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XViewerMediaHost.kt
  - id: openwiki-source-7b54b8be8bd08a042beb8489
    resource: repo://feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XViewerScreen.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# X UI：専用WebViewとカスタマイズの実行

`:feature:x:ui` は専用WebView、CSS/JavaScript editor、要素picker、全画面メディアの表示を所有する。`XViewerRoute`はDomain Repositoryを注入し、設定画面とWebViewを組み合わせる。保存はDomain契約へ渡し、SharedPreferencesを直接読まない。

## 主要な構成要素

| 実装・公開契約のまとまり | 種類・責務と主要なAPI |
| --- | --- |
| [NavigationDestination](../../../feature/x/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/x/NavigationDestination.kt) | `X_ROUTE`と`X_TITLE`がアプリのX表示routeと表示名を公開する。 |
| [XViewerRoute](../../../feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XViewerRoute.kt) | `XViewerRoute`（Composable）はRepositoryを受け取り`XViewerMediaHost`と設定dialogを接続する。設定ボタンとdialogはfullscreen中に表示せず、dialog表示状態はComposeが所有する。 |
| [XViewerScreen](../../../feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XViewerScreen.kt) | `XViewerScreen`（Composable）はWebView、ページ遷移、CSS/JavaScript注入と要素pickerを扱う。内部関数`toBrowserCompatibleUserAgent`はUAを整え、`parentTouchInterceptionRequest`はタッチの親への引渡し、`shouldOpenXNavigationExternally`はmain-frame外部URLの分岐を決める。`isPersistableElementPickerSelector`、`appendHiddenElementRule`、`decodeElementPickerSelectorResult`がpicker結果の検証とCSS追記を行う。内部renderer lifecycleが破棄済みWebViewの再利用を避ける。 |
| [XViewerCssSettingsUi](../../../feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XViewerCssSettingsUi.kt) | `XViewerCustomizationDialog`（Composable）はCSSセット切替・コピー・既定値復元とJavaScript編集をDomainモデル経由で保存する。WebViewのcookieやアカウント状態をこの設定モデルへ移さない。 |
| [XViewerMediaHost](../../../feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XViewerMediaHost.kt) | `XViewerMediaHost`（internal Composable）はfullscreen Viewとcallbackを所有し、戻る操作をfullscreen終了→WebView履歴→親dispatcherの順に処理する。disposeでchrome clientとfullscreen callbackを解放する。 |
| [XMediaWebChromeClient](../../../feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XMediaWebChromeClient.kt) | `XMediaWebChromeClient`（internal class）は`onShowCustomView`/`onHideCustomView`をhostへ委譲し、`onProgressChanged`の読込完了時にviewport回復scriptを入れる。 |
| [XMediaViewportHeightRecovery](../../../feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XMediaViewportHeightRecovery.kt) | `WebView.installMediaViewportHeightRecovery`（internal拡張関数）はvideo祖先のinline dvhがゼロ高さになる場合のscriptを導入する。viewport変更に追従し、ページ自身が別の値を書いたpropertyは上書き対象から外す。 |

## 公開APIと構成要素間の接続

[AppSupportingRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)が`SharedPreferencesXViewerCssRepository(application)`を`XViewerCssRepository`として公開する。[AppSupportingRouteDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)が同じ設定契約をroute側へ渡す。DomainモデルがCSS編集状態、Dataが永続設定、UIがWebViewとfullscreenの一時状態を所有する。

## 読込とカスタマイズ

WebViewは実寸layoutが得られた後に初回URLを読み込む。`onPageFinished`でX/Twitter hostの場合だけ最新設定をloadしてCSSと有効JavaScriptを注入する。手動reload後も同じ経路を使う。外部hostへのmain-frame遷移はブラウザへ渡し、subframeまで外部起動しない。pickerは選択した要素を隠すCSSを生成するが、不安定なnth-of-type selectorを保存対象から除く。

## rendererとmediaの寿命

renderer終了時はcrashと非crashを分け、非crashはgenerationを進めて再読込する。Composeのdispose時は有効なrendererへstopLoadingを行い、WebViewをdestroyする。X login向けのthird-party Cookieはこの専用WebViewで有効化する。`XMediaWebChromeClient`は全画面表示・終了を表示hostへ委譲し、進捗100%でviewport高さの回復処理を設置する。

`XViewerScreenTest`はhost判定、CSS注入、selector、touch伝播を、`XMediaWebChromeClientTest`は全画面委譲を確認する。JavaScript設定変更では今後の読込への適用と既存pageの副作用を区別し、editorとreloadの導線を一緒に確認する。

## 調査・変更の入口

[XViewerRoute](../../../feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XViewerRoute.kt)、[XViewerScreen](../../../feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XViewerScreen.kt)、[XMediaWebChromeClient](../../../feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XMediaWebChromeClient.kt)、[XViewerScreenTest](../../../feature/x/ui/src/test/java/dev/terashima/yomitorirss/feature/x/XViewerScreenTest.kt)、[XMediaWebChromeClientTest](../../../feature/x/ui/src/test/java/dev/terashima/yomitorirss/feature/x/XMediaWebChromeClientTest.kt) を起点に責務と呼び出し側を確認する。

関連: [x domain](x-domain.md) / [x data](x-data.md) / [全体構成](../../architecture/system.md)。
