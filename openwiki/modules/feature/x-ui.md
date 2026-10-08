---
type: module
title: "X UI：専用WebViewとカスタマイズの実行"
description: "X限定のCSS/JavaScript注入、外部リンク分岐、renderer回復と全画面メディアを説明する。"
tags: [x, ui, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-cddac7e11298ec5e5eb06085
    resource: repo://feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XMediaWebChromeClient.kt
  - id: openwiki-source-7b54b8be8bd08a042beb8489
    resource: repo://feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XViewerScreen.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# X UI：専用WebViewとカスタマイズの実行

`:feature:x:ui` は専用WebView、CSS/JavaScript editor、要素picker、全画面メディアの表示を所有する。`XViewerRoute`はDomain Repositoryを注入し、設定画面とWebViewを組み合わせる。保存はDomain契約へ渡し、SharedPreferencesを直接読まない。

## 読込とカスタマイズ

WebViewは実寸layoutが得られた後に初回URLを読み込む。`onPageFinished`でX/Twitter hostの場合だけ最新設定をloadしてCSSと有効JavaScriptを注入する。手動reload後も同じ経路を使う。外部hostへのmain-frame遷移はブラウザへ渡し、subframeまで外部起動しない。pickerは選択した要素を隠すCSSを生成するが、不安定なnth-of-type selectorを保存対象から除く。

## rendererとmediaの寿命

renderer終了時はcrashと非crashを分け、非crashはgenerationを進めて再読込する。Composeのdispose時は有効なrendererへstopLoadingを行い、WebViewをdestroyする。X login向けのthird-party Cookieはこの専用WebViewで有効化する。`XMediaWebChromeClient`は全画面表示・終了を表示hostへ委譲し、進捗100%でviewport高さの回復処理を設置する。

`XViewerScreenTest`はhost判定、CSS注入、selector、touch伝播を、`XMediaWebChromeClientTest`は全画面委譲を確認する。JavaScript設定変更では今後の読込への適用と既存pageの副作用を区別し、editorとreloadの導線を一緒に確認する。

## 調査・変更の入口

[XViewerRoute](../../../feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XViewerRoute.kt)、[XViewerScreen](../../../feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XViewerScreen.kt)、[XMediaWebChromeClient](../../../feature/x/ui/src/main/java/dev/terashima/yomitorirss/feature/x/XMediaWebChromeClient.kt)、[XViewerScreenTest](../../../feature/x/ui/src/test/java/dev/terashima/yomitorirss/feature/x/XViewerScreenTest.kt)、[XMediaWebChromeClientTest](../../../feature/x/ui/src/test/java/dev/terashima/yomitorirss/feature/x/XMediaWebChromeClientTest.kt) を起点に責務と呼び出し側を確認する。

関連: [x domain](x-domain.md) / [x data](x-data.md) / [全体構成](../../architecture/system.md)。
