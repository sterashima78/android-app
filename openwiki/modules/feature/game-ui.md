---
type: module
title: "Game UI：ComposeとGodotの実行・表示境界"
description: "ゲーム選択、ViewModel内の進行、animation入力制御、Godot scene選択と画面方向を説明する。"
tags: [game, ui, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-a2d12c467a837542d2038d95
    resource: repo://feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Game2048ViewModel.kt
  - id: openwiki-source-77f52d689f17d87e2a1b8fe5
    resource: repo://feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/GameRoute.kt
  - id: openwiki-source-1e55c3cec48ec8fc53de2152
    resource: repo://feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/GodotKlondikeActivity.kt
  - id: openwiki-source-4422b1e876d2d61a827b773a
    resource: repo://feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGameViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Game UI：ComposeとGodotの実行・表示境界

`:feature:game:ui` はGame一覧、Composeゲーム画面とViewModel、同梱Godot assets、数独・クロンダイクActivityを所有する。Domain規則を表示と入力へ接続し、Godot Android runtimeもこのモジュールに依存する。GameにData moduleはなく、進行を保存するRepositoryは設けていない。

## ゲーム選択と進行

`GameRoute`は画面選択をrememberSaveableで持ち、KotlinゲームにはViewModelのStateFlowを渡す。数独とクロンダイクは専用Activityを起動する。2048 ViewModelはtransition中の追加moveを拒否し、animation完了のidが現在値と一致するとtransitionを解除する。暴走炉ViewModelは周回効果候補が出ている間のtick/tap/purchaseを止め、tick時間を上限付きにして大きな時間飛びを抑える。

## Godotと表示寿命

Godotの共通bootstrapはuser argumentの`--game=`からsceneを選び、既定を数独とする。クロンダイクActivityは`-- --game=klondike`を追加して同じbootstrapへ入り、engineのorientation要求を横向きへ補正する。Compose内のスパイダーは横向き・全画面を要求し、その他のCompose画面は縦向き・標準表示を要求する。画面選択が復元されることとゲーム進行のdurable保存は別である。

`GameOrientationTest`は画面方向・chromeとGodot引数、`IncrementalGameViewModelTest`は選択待ち中の進行停止を確認する。新ゲーム追加ではroute分岐、direction/chrome、Godotならbootstrapとmanifestを合わせて確認する。

## 調査・変更の入口

[GameRoute](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/GameRoute.kt)、[Game2048ViewModel](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Game2048ViewModel.kt)、[IncrementalGameViewModel](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGameViewModel.kt)、[GodotKlondikeActivity](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/GodotKlondikeActivity.kt)、[GameOrientationTest](../../../feature/game/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/game/GameOrientationTest.kt)、[IncrementalGameViewModelTest](../../../feature/game/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGameViewModelTest.kt) を起点に責務と呼び出し側を確認する。

関連: [game domain](game-domain.md) / [全体構成](../../architecture/system.md)。
