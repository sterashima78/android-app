---
type: module
title: App shell・navigation と route の接続
description: Compose の drawer、top / bottom bar、navigation、feature route と外部 action callback を接続する。
tags: [compose, navigation, presentation, module]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-d3c58638caf6fd74f5638024
    resource: repo://app/presentation/build.gradle.kts
  - id: openwiki-source-5caebea6adf147a65bdf9136
    resource: repo://app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/RootBackAction.kt
  - id: openwiki-source-fa94c0fbaaa0d7674b16244c
    resource: repo://app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/YomitoriApp.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# App shell・navigation と route の接続

## 画面 shell の責務

`:app:presentation` は App shell と feature 画面を接続する Compose Android library である。[YomitoriApp](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/YomitoriApp.kt) は NavHostController、AppRouteDependencies、navigation request Flow と platform callback を受け取る。drawer、snackbar、top / bottom bar を配置し、選択 route に応じた feature NavGraph と overlay を呼ぶ。AppRouteDependencies は [composition](composition.md) が作り、外部 URL、Web server、終了、生体ロック変更などは [app](app.md) の callback で実行する。

## navigation と lifetime

navigation request を LaunchedEffect で収集して top-level route へ移動し、Web server target では dialog callback を呼ぶ。current back-stack entry を ViewModelStoreOwner として feature message / route に渡すので、feature ViewModel の状態・message は該当画面の host で扱う。root は feature の repository や Worker を構築しない。

[RootBackAction](../../../app/presentation/src/main/kotlin/dev/terashima/yomitorirss/ui/RootBackAction.kt) は drawer が開いていれば終了、閉じていて履歴があれば pop、root なら drawer を開くという優先順を持つ。game fullscreen 時の app chrome、X の inset など画面横断の shell 調整もここに置く。個別 feature の一覧・編集判断は feature UI 側を追う。

## 依存境界と変更の確認

[verifyPresentationBoundary](../../../app/presentation/build.gradle.kts) は実行 app / feature Data への Gradle dependency、concrete Data import、DB / WorkManager infrastructure、executable platform 型を拒否する。YomitoriApp の feature Activity Result launcher 所有と feature state の直接 collection も検査する。

[RootBackActionTest](../../../app/presentation/src/test/kotlin/dev/terashima/yomitorirss/ui/RootBackActionTest.kt) は戻る優先順位、AppNavigationTargetTest と AppNavigationSpecTest は route mapping、YomitoriAppLayoutTest は shell の確認入口になる。新しい画面は feature Domain / UI 契約と route factory を接続し、platform 処理は callback を追加して境界検証を通す。

仕様・設計の正本: [architecture/module-map.md](../../../docs/architecture/module-map.md)、[architecture/code-organization.md](../../../docs/architecture/code-organization.md)。
