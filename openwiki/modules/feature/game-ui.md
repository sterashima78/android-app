---
type: module
title: Game UI：ComposeとGodotの実行・表示境界
description: ゲーム選択、ViewModel内の進行、animation入力制御、Godot scene選択と画面方向を説明する。
tags:
  - game
  - ui
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-a2d12c467a837542d2038d95
    resource: repo://feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Game2048ViewModel.kt
  - id: openwiki-source-77f52d689f17d87e2a1b8fe5
    resource: repo://feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/GameRoute.kt
  - id: openwiki-source-1e55c3cec48ec8fc53de2152
    resource: repo://feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/GodotKlondikeActivity.kt
  - id: openwiki-source-4422b1e876d2d61a827b773a
    resource: repo://feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGameViewModel.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Game UI：ComposeとGodotの実行・表示境界

`:feature:game:ui` はGame一覧、Composeゲーム画面とViewModel、同梱Godot assets、数独・クロンダイクActivityを所有する。Domain規則を表示と入力へ接続し、Godot Android runtimeもこのモジュールに依存する。GameにData moduleはなく、進行を保存するRepositoryは設けていない。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `CardEmphasis`（enum class）、`GameTableSurface`（関数）、`PlayingCardView`（関数）、`CardBackView`（関数）、`CardSlotView`（関数）、`PlayingCard.rankLabel`（関数）、`CardSuit.symbol`（関数）、`CardSuit.displayName`（関数）、`PlayingCard.description`（関数） | table surface、card face/back/slotとsuit/rank/アクセシビリティ説明の共通表示。 | [CardVisuals.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/CardVisuals.kt) |
| `Game2048Screen`（関数） | 盤面state/transitionを描画し、gestureを方向callbackへ、animation完了をidへ戻す。 | [Game2048Screen.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Game2048Screen.kt) |
| `Game2048UiState`（data class）、`Game2048ViewModel`（class） | Domain move resultをUI stateへ保存し、transition idで古いanimation完了を拒否する。 主なメソッド: `newGame`、`move`、`completeTransition`。 | [Game2048ViewModel.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Game2048ViewModel.kt) |
| `GameScreen`（enum class）、`GameOrientationPreference`（enum class）、`GameChromePreference`（enum class）、`GameRoute`（関数）、`orientationPreferenceFor`（関数）、`chromePreferenceFor`（関数） | 選択画面stateを所有し、各ViewModel/ComposeとGodot Activityへ入力を配線する公開入口。orientation/chrome enumはappへの表示要求。 | [GameRoute.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/GameRoute.kt) |
| `klondikeGodotCommandLine`（関数）、`GodotKlondikeActivity`（class） | Godot引数へklondike選択を追加しorientation要求を固定横向きへ変換する。 主なメソッド: `getCommandLine`、`setRequestedOrientation`。 | [GodotKlondikeActivity.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/GodotKlondikeActivity.kt) |
| `GodotSudokuActivity`（class） | GodotActivityの標準bootstrapから数独sceneを起動する。 | [GodotSudokuActivity.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/GodotSudokuActivity.kt) |
| `IncrementalGameScreen`（関数）、`formatIncrementalNumber`（関数） | energy/設備/周回選択/Prestigeを描画しtickと操作callbackを発行する。数値整形は内部補助。 | [IncrementalGameScreen.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGameScreen.kt) |
| `IncrementalGameUiState`（data class）、`IncrementalGameViewModel`（class） | tick/tap/purchaseと周回pick待ちを管理し、Domainへ実行可能な入力を送る。 主なメソッド: `tick`、`tap`、`setPurchaseAmount`、`purchase`、`prestige`、`selectRunPick`、`purchasePrestigeUpgrade`。 | [IncrementalGameViewModel.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGameViewModel.kt) |
| `MinesweeperScreen`（関数） | cell状態を描画し短押しrevealと長押しflagをcallbackへ送る。 | [MinesweeperScreen.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/MinesweeperScreen.kt) |
| `MinesweeperViewModel`（class） | MinesweeperGameへnew/reveal/flagを委譲しStateFlowを更新する。 主なメソッド: `newGame`、`reveal`、`toggleFlag`。 | [MinesweeperViewModel.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/MinesweeperViewModel.kt) |
| `GAME_ROUTE`（定数）、`GAME_TITLE`（定数） | appのnavigationに渡すroute/title定数。 | [NavigationDestination.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/NavigationDestination.kt) |
| `NonogramScreen`（関数） | 行列hintとcellを表示しfill/mark callbackへ渡す。 | [NonogramScreen.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/NonogramScreen.kt) |
| `NonogramViewModel`（class） | factoryのpuzzleからstateを作り、fill/mark拡張関数をStateFlowへ反映する。 主なメソッド: `newGame`、`fill`、`mark`。 | [NonogramViewModel.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/NonogramViewModel.kt) |
| `SpiderRoute`（関数） | SpiderRouteがViewModelを購読し、カード選択・合法target・移動・配布を表示する。 | [SpiderScreen.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/SpiderScreen.kt) |
| `SpiderViewModel`（class） | factoryのdifficulty/stateを持ち選択・移動・配布の拡張関数を呼ぶ。 主なメソッド: `newGame`、`selectTableau`、`moveSelectedToTableau`、`dealStock`。 | [SpiderViewModel.kt](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/SpiderViewModel.kt) |


## 公開入口・メソッドと配線

`GAME_ROUTE/GAME_TITLE` はnavigation定数、`GameRoute` は5つの公開ViewModelとorientation/chrome callbackを受け取るCompose入口です。`GameOrientationPreference` と `GameChromePreference` はappへ渡す表示要求、内部 `GameScreen` が選択先です。ViewModelは直接Domain game/factoryを生成するため、このmoduleにRepositoryのcomposition bindingはありません。

`Game2048ViewModel.newGame/move/completeTransition` は盤面と `Game2048UiState` の遷移IDを更新します。`NonogramViewModel.newGame/fill/mark`、`MinesweeperViewModel.newGame/reveal/toggleFlag`、`SpiderViewModel.newGame/selectTableau/moveSelectedToTableau/dealStock` はDomainの次stateをStateFlowへ保存します。内部 Screen群はstateとcallbackを受け、SpiderRouteだけViewModelを直接購読します。`CardVisuals` のtable/card/back/slot関数とrank/suit/description拡張は共通の見た目と説明を作ります。

`IncrementalGameViewModel.tick/tap/setPurchaseAmount/purchase/prestige/selectRunPick/purchasePrestigeUpgrade` は `IncrementalGameUiState` を更新します。周回候補の選択待ち中は主要な進行入力を拒否し、tap結果の報酬/jackpot/event IDを演出へ渡します。`IncrementalGameScreen` のtick callbackと操作callbackがViewModelへ戻り、`formatIncrementalNumber` は表示整形だけを担います。

## ゲーム選択と進行

`GameRoute`は画面選択をrememberSaveableで持ち、KotlinゲームにはViewModelのStateFlowを渡す。数独とクロンダイクは専用Activityを起動する。2048 ViewModelはtransition中の追加moveを拒否し、animation完了のidが現在値と一致するとtransitionを解除する。暴走炉ViewModelは周回効果候補が出ている間のtick/tap/purchaseを止め、tick時間を上限付きにして大きな時間飛びを抑える。

## Godotと表示寿命

Godotの共通bootstrapはuser argumentの`--game=`からsceneを選び、既定を数独とする。クロンダイクActivityは`-- --game=klondike`を追加して同じbootstrapへ入り、engineのorientation要求を横向きへ補正する。Compose内のスパイダーは横向き・全画面を要求し、その他のCompose画面は縦向き・標準表示を要求する。画面選択が復元されることとゲーム進行のdurable保存は別である。

`GameOrientationTest`は画面方向・chromeとGodot引数、`IncrementalGameViewModelTest`は選択待ち中の進行停止を確認する。新ゲーム追加ではroute分岐、direction/chrome、Godotならbootstrapとmanifestを合わせて確認する。

## Godotの構成と失敗境界

[project.godot](../../../feature/game/ui/src/main/assets/project.godot) のentrysceneは [game_bootstrap.tscn](../../../feature/game/ui/src/main/assets/game_bootstrap.tscn)。[game_bootstrap.gd](../../../feature/game/ui/src/main/assets/game_bootstrap.gd) の `game_key_for_user_args` と `_ready` が引数からsceneを選び、未対応keyはerrorを出して終了します。`GodotSudokuActivity` は標準bootstrap、`GodotKlondikeActivity.getCommandLine` は `klondikeGodotCommandLine` で選択引数を加えます。

[klondike.gd](../../../feature/game/ui/src/main/assets/klondike.gd) は表示・safe area・操作と再描画を所有し、[KlondikeModel](../../../feature/game/ui/src/main/assets/klondike_model.gd) のstock/waste/foundations/tableau/selectionとreset/draw/selection/move APIへ委譲します。[sudoku.gd](../../../feature/game/ui/src/main/assets/sudoku.gd) はpuzzle/solution/values、入力・timer・completion表示を同じsceneで所有します。これらはKotlin Domainのstateを共有しません。Compose/Godotとも進行はruntime内の状態で、画面選択のrememberSaveableをdurableゲーム保存と解釈しないでください。

## 調査・変更の入口

[GameRoute](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/GameRoute.kt)、[Game2048ViewModel](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Game2048ViewModel.kt)、[IncrementalGameViewModel](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGameViewModel.kt)、[GodotKlondikeActivity](../../../feature/game/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/game/GodotKlondikeActivity.kt)、[GameOrientationTest](../../../feature/game/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/game/GameOrientationTest.kt)、[IncrementalGameViewModelTest](../../../feature/game/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGameViewModelTest.kt) を起点に責務と呼び出し側を確認する。

関連: [game domain](game-domain.md) / [全体構成](../../architecture/system.md)。
