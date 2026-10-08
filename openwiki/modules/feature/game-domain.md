---
type: module
title: "Game Domain：端末内ゲームの状態遷移"
description: "Kotlinゲームの盤面・移動・勝敗と暴走炉の設備生産・周回効果・Prestigeを説明する。"
tags: [game, domain, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-bdb1df132e2c7696fe9b5013
    resource: repo://feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Game2048.kt
  - id: openwiki-source-ded7a0793c0fe45f179edafb
    resource: repo://feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Minesweeper.kt
  - id: openwiki-source-9c0212431d7ff6d6b6ff9867
    resource: repo://feature/game/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGameTest.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Game Domain：端末内ゲームの状態遷移

`:feature:game:domain` は2048、ノノグラム、マインスイーパー、スパイダー、暴走炉のKotlin状態モデルと遷移を持つJVMモジュールである。Android、画面、network、databaseに依存せず、渡されたstateから次stateを計算する。数独とクロンダイクの現在の実装はUI同梱Godot scene側にあり、このKotlin Domainへ一律に集約されていない。

## パズルとカードの規則

2048は通常の次stateに加えて移動元・移動先・結合・生成tileを含むtransition結果を返し、UI animationへ渡す。動かない入力ではtileを追加しない。マインスイーパーは初めて開く時に地雷を配置し、最初のマスと可能な範囲で周囲を除外する。ノノグラムは塗りとmarkの入力、行列hintと完成判定を管理する。スパイダーは同一suitの降順列、合法target、stock配布、完成run回収と伏せ札公開をstate遷移として扱う。

## 暴走炉の進行

設備は他設備を生むのではなくenergyを直接生産し、種類ごとにtap・暴走・milestone・多様性への寄与を変える。tapと時間経過、購入、周回効果選択、Prestige、恒久upgradeを独立操作として公開する。Prestigeは周回stateを作り直し、取得coreを消費型upgradeへ利用する。永続化はこのモジュールの責務に含まれていない。

`Game2048Test`、`MinesweeperTest`、`NonogramTest`、`SpiderTest`と暴走炉の2つのtest群が主要な規則確認先である。ゲーム規則変更はrandom依存を制御したstate testから確認し、表示animationはUIと別に検証する。

## 調査・変更の入口

[Game2048](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Game2048.kt)、[Minesweeper](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Minesweeper.kt)、[Spider](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/Spider.kt)、[IncrementalGame](../../../feature/game/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGame.kt)、[Game2048Test](../../../feature/game/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/game/Game2048Test.kt)、[IncrementalGameTest](../../../feature/game/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/game/IncrementalGameTest.kt) を起点に責務と呼び出し側を確認する。

関連: [game ui](game-ui.md) / [全体構成](../../architecture/system.md)。
