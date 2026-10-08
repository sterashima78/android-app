---
type: module
title: "Workout Domain：記録、メニュー、AI 契約"
description: "Workout snapshot の日付遷移、メニュー制約と background AI capability。"
tags: [workout, domain, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-40c394a2bc84198d73e003e7
    resource: repo://feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutModels.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Workout Domain：記録、メニュー、AI 契約

`:feature:workout:domain` はアプリが所有する運動記録とメニュー、および AI 支援の契約を定義する Kotlin/JVM モジュールである。`WorkoutReader` は snapshot の読取、`WorkoutRepository` は保存を公開する。種目マスタ、プリセットメニュー、当日の記録、完了履歴を一つの snapshot として扱い、Calendar などの参照者へは Reader を渡す。Health Connect の read data をここへ取り込む契約は持たない。

## メニューと日付の遷移

メニュー項目は正のセット数と正の各セット目標を持つ。目標一覧は空か、セット数と同じ長さでなければならない。`effectiveMenu` と関連の projection が、その日に使うメニューから種目表示を組み立てる。種目マスタの既定セット数と、メニューごとの目標は分離している。

`rolloverTo` は日付が変わったとき、当日にセットがあれば実績と当日のメニューを完了履歴に移し、新しい日を空の記録で始める。セットがなければ空の履歴を作らない。既存日と同じなら snapshot を維持する。履歴の件数制限もこの遷移にあるため、UI の表示件数だけを変えて保存履歴の仕様を変えたつもりにしない。

AI の契約は provider、提案とレビューの request type、task state、保存済みレビュー、設定・メモ、prompt 構築に分かれる。`WorkoutAiTaskController` が enqueue と結果参照を公開し、推論の実行寿命を UI から切り離す。`WorkoutModelsTest` は日跨ぎとメニュー、`WorkoutAiPromptBuilderTest` は予定と実績の区別、直近レビューの利用範囲を検証する。
## 変更時の調査先

- [WorkoutModels.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutModels.kt)
- [WorkoutRepository.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutRepository.kt)
- [WorkoutAi.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAi.kt)
- [WorkoutModelsTest.kt](../../../feature/workout/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutModelsTest.kt)
- [WorkoutAiPromptBuilderTest.kt](../../../feature/workout/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAiPromptBuilderTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [data 層](workout-data.md)、[ui 層](workout-ui.md)、[システム全体](../../architecture/system.md)。
