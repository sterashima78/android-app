---
type: module
title: Game Domain：端末内ゲームの状態遷移
description: Kotlinゲームの盤面・移動・勝敗と暴走炉の設備生産・周回効果・Prestigeを説明する。
tags:
  - game
  - domain
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T04:30:12.060Z
sources:
  - id: openwiki-source-bdb1df132e2c7696fe9b5013
    resource: repo://feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Game2048.kt
  - id: openwiki-source-ded7a0793c0fe45f179edafb
    resource: repo://feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Minesweeper.kt
  - id: openwiki-source-4646c0fca5a13666eb3023cd
    resource: repo://feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Spider.kt
  - id: openwiki-source-9c0212431d7ff6d6b6ff9867
    resource: repo://feature/game/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGameTest.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---

# Game Domain：端末内ゲームの状態遷移

`:feature:game:domain` は2048、ノノグラム、マインスイーパー、スパイダー、暴走炉のKotlin状態モデルと遷移を持つJVMモジュールである。Android、画面、network、databaseに依存せず、渡されたstateから次stateを計算する。数独とクロンダイクの現在の実装はUI同梱Godot scene側にあり、このKotlin Domainへ一律に集約されていない。


## 主要な構成要素

| 型・関数 | 役割と関係 | 実装 |
| --- | --- | --- |
| `Game2048Direction`（enum class）、`Game2048State`（data class）、`Game2048TileMovement`（data class）、`Game2048MoveResult`（data class） | 入力・結果・盤面の値モデル。盤面/方向/animation遷移モデルと初期化・移動・結合規則。 | [Game2048.kt](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Game2048.kt) |
| `Game2048`（class）、`merge2048Line`（関数） | 値モデルを受け取り次stateや判断値を返す規則・factory・拡張関数。盤面/方向/animation遷移モデルと初期化・移動・結合規則。 主なメソッド: `newGame`、`move`、`moveWithTransition`。 | [Game2048.kt](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Game2048.kt) |
| `IncrementalGeneratorType`（enum class）、`IncrementalPurchaseAmount`（enum class）、`IncrementalRunPick`（enum class）、`IncrementalPrestigeUpgrade`（enum class）、`IncrementalGameState`（data class）、`IncrementalTapResult`（data class） | 入力・結果・盤面の値モデル。generator/購入量/周回pick/恒久upgrade/state/resultとenergy生産・購入・Prestige計算。 | [IncrementalGame.kt](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGame.kt) |
| `IncrementalGame`（object） | 値モデルを受け取り次stateや判断値を返す規則・factory・拡張関数。generator/購入量/周回pick/恒久upgrade/state/resultとenergy生産・購入・Prestige計算。 主なメソッド: `newGame`、`tap`、`tick`、`productionPerSecond`、`generatorProductionPerSecond`、`isRunPickDue`、`availableRunPicks`、`selectRunPick`、`purchaseCost`、`purchase`、`prestigeReward`、`nextPrestigeRewardEnergy`、`prestige`、`prestigeUpgradeLevel`、`prestigeUpgradeCost`、`isPrestigeUpgradeUnlocked`、`purchasePrestigeUpgrade`、`totalPrestigeUpgradeLevels`、`milestoneInterval`、`milestoneProductionMultiplier`、`overdriveDurationSeconds`、`outputMultiplier`、`specializationMultiplier`、`tapMultiplier`、`overdriveProductionMultiplier`。 | [IncrementalGame.kt](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGame.kt) |
| `MinesweeperVisibility`（enum class）、`MinesweeperStatus`（enum class）、`MinesweeperCell`（data class）、`MinesweeperState`（data class） | 入力・結果・盤面の値モデル。cell可視性/勝敗/stateと初手安全な初期化・旗・revealの規則。 | [Minesweeper.kt](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Minesweeper.kt) |
| `MinesweeperGame`（class）、`neighbors`（関数） | 値モデルを受け取り次stateや判断値を返す規則・factory・拡張関数。cell可視性/勝敗/stateと初手安全な初期化・旗・revealの規則。 主なメソッド: `newGame`、`toggleFlag`、`reveal`。 | [Minesweeper.kt](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Minesweeper.kt) |
| `NonogramCellState`（enum class）、`NonogramPuzzle`（data class）、`NonogramGameState`（data class） | 入力・結果・盤面の値モデル。solution/hint・cell/state・puzzle factory、塗り/markの純粋遷移。 | [Nonogram.kt](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Nonogram.kt) |
| `NonogramPuzzleFactory`（class）、`NonogramPuzzle.newGameState`（関数）、`NonogramGameState.fill`（関数）、`NonogramGameState.mark`（関数）、`nonogramClues`（関数） | 値モデルを受け取り次stateや判断値を返す規則・factory・拡張関数。solution/hint・cell/state・puzzle factory、塗り/markの純粋遷移。 主なメソッド: `create`。 | [Nonogram.kt](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Nonogram.kt) |
| `CardSuit`（enum class）、`PlayingCard`（data class） | suit/赤色とrank範囲を持つカード値。Spiderとcard UIが共有する。 | [PlayingCard.kt](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/PlayingCard.kt) |
| `SpiderDifficulty`（enum class）、`SpiderTableauCard`（data class）、`SpiderSelection`（data class）、`SpiderGameState`（data class） | 入力・結果・盤面の値モデル。difficulty/場札/選択/stateとshuffle factory、選択・合法移動・stock配布の拡張関数。 | [Spider.kt](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Spider.kt) |
| `SpiderGameFactory`（class）、`SpiderGameState.selectTableau`（関数）、`SpiderGameState.selectedCardCount`（関数）、`SpiderGameState.validTableauTargets`（関数）、`SpiderGameState.moveSelectedToTableau`（関数）、`SpiderGameState.dealStock`（関数） | 値モデルを受け取り次stateや判断値を返す規則・factory・拡張関数。difficulty/場札/選択/stateとshuffle factory、選択・合法移動・stock配布の拡張関数。 主なメソッド: `create`。 | [Spider.kt](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Spider.kt) |


## 公開 APIと状態モデルの関係

`Game2048Direction/State/TileMovement/MoveResult` は盤面とUI遷移で、`Game2048.newGame/move/moveWithTransition` は初期状態、次state、animation付き結果を返します。内部 `merge2048Line` が一列の結合を検証可能にします。`MinesweeperVisibility/Status/Cell/State` は可視性と勝敗、`MinesweeperGame.newGame/toggleFlag/reveal` は初期化・旗・開示を返し、範囲外/終了後/許可されない操作は元stateです。

`NonogramCellState/Puzzle/GameState` はsolution/hintと入力、`NonogramPuzzleFactory.create` は変形したpuzzle、`newGameState/fill/mark` 拡張関数は初期化・塗り・markを返します。`PlayingCard/CardSuit` はカード値、`SpiderDifficulty/TableauCard/Selection/GameState` は難易度・伏せ札・選択・盤面。`SpiderGameFactory.create` が配り、`selectTableau/selectedCardCount/validTableauTargets/moveSelectedToTableau/dealStock` が選択/表示可能な移動先/移動/配布を扱います。内部の完成run回収が移動後に伏せ札を公開します。

`IncrementalGeneratorType/PurchaseAmount/RunPick/PrestigeUpgrade` は設備・購入単位・周回効果・恒久upgrade、`IncrementalGameState` は資産とlevel、`IncrementalTapResult` はstateと演出情報です。`IncrementalGame.newGame/tap/tick/purchase/selectRunPick/prestige/purchasePrestigeUpgrade` が遷移を返し、production/cost/reward/unlock/milestone/multiplier系APIがUIへ判断値を返します。恒久upgradeのlevelと周回stateを分け、Prestigeはrewardを加えて周回を作り直します。

## UIから戻る代表的フロー

[Game UI](game-ui.md) のViewModelが現在stateと入力をDomainへ渡し、戻った不変stateをStateFlowへ保存します。たとえばSpiderは `selectTableau` → 合法runの選択 → `moveSelectedToTableau` → source先頭公開と完成run回収 → 新stateです。不正なtargetは元stateを返し、UIの失敗をnetwork errorのようなmessageとして管理しません。モデルのconstructorの `require` は不正盤面/rankなどを例外で拒否します。randomを注入するfactory/classと純粋state遷移を分け、UIのStateFlowやanimationはDomainに持ち込みません。

## パズルとカードの規則

2048は通常の次stateに加えて移動元・移動先・結合・生成tileを含むtransition結果を返し、UI animationへ渡す。動かない入力ではtileを追加しない。マインスイーパーは初めて開く時に地雷を配置し、最初のマスと可能な範囲で周囲を除外する。ノノグラムは塗りとmarkの入力、行列hintと完成判定を管理する。スパイダーは同一suitの降順列、合法target、stock配布、完成run回収と伏せ札公開をstate遷移として扱う。

## 暴走炉の進行

設備は他設備を生むのではなくenergyを直接生産し、種類ごとにtap・暴走・milestone・多様性への寄与を変える。tapと時間経過、購入、周回効果選択、Prestige、恒久upgradeを独立操作として公開する。Prestigeは周回stateを作り直し、取得coreを消費型upgradeへ利用する。永続化はこのモジュールの責務に含まれていない。

`Game2048Test`、`MinesweeperTest`、`NonogramTest`、`SpiderTest`と暴走炉の2つのtest群が主要な規則確認先である。ゲーム規則変更はrandom依存を制御したstate testから確認し、表示animationはUIと別に検証する。

## 調査・変更の入口

[Game2048](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Game2048.kt)、[Minesweeper](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Minesweeper.kt)、[Spider](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Spider.kt)、[IncrementalGame](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGame.kt)、[Game2048Test](../../../feature/game/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/game/Game2048Test.kt)、[IncrementalGameTest](../../../feature/game/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGameTest.kt) を起点に責務と呼び出し側を確認する。

関連: [game ui](game-ui.md) / [全体構成](../../architecture/system.md)。
