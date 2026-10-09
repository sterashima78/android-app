---
type: module
title: Workout Domain：記録、メニュー、AI 契約
description: Workout snapshot の日付遷移、メニュー制約と background AI capability。
tags:
  - workout
  - domain
  - modules
sources:
  - id: openwiki-source-4eef3629cfff53442d086f82
    resource: repo://feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAi.kt
  - id: openwiki-source-40c394a2bc84198d73e003e7
    resource: repo://feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutModels.kt
  - id: openwiki-source-d3629e40219840e19d1f59df
    resource: repo://feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutSetDetailText.kt
  - id: openwiki-source-b639649e21b4e09d57c66e26
    resource: repo://feature/workout/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAiPromptBuilderTest.kt
generated: { by: "codex", at: "2026-10-09T08:22:11.354Z" }
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T08:22:11.354Z
---
# Workout Domain：記録、メニュー、AI 契約

`:feature:workout:domain` はアプリが所有する運動記録とメニュー、および AI 支援の契約を定義する Kotlin/JVM モジュールである。`WorkoutReader` は snapshot の読取、`WorkoutRepository` は保存を公開する。種目マスタ、プリセットメニュー、当日の記録、完了履歴を一つの snapshot として扱い、Calendar などの参照者へは Reader を渡す。Health Connect の read data をここへ取り込む契約は持たない。


## 主要な構成要素

| 構成要素・種類 | 責務・主要 API と関係 | 実装 |
| --- | --- | --- |
| WorkoutExerciseType / WorkoutUnit / WorkoutMenuSource（enum）、WorkoutExercise / WorkoutMenuItem / WorkoutMenu（data class） | 種目マスタ、単位・種別、予定のセット数・目標とメニュー由来を分ける。 | [WorkoutModels.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutModels.kt) |
| WorkoutFormQuality / WorkoutLoadKind（enum）、WorkoutLoad / WorkoutSet（data class） | 任意のフォーム・負荷・RPE・実測休憩を記録する。重量負荷は有限非負値、RPE は許容範囲、休憩は非負を要求する。 | [WorkoutModels.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutModels.kt) |
| WorkoutDay / WorkoutHistory / WorkoutSnapshot（data class） | 当日と完了実績、種目・プリセット、前回入力値を集約する。History は実施時 menu を保持する。 | [WorkoutModels.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutModels.kt) |
| defaultWorkoutExercises / defaultWorkoutMenu / newWorkoutSnapshot / inferWorkoutExerciseType（関数） | 初期値と名前・単位からの種別補完。 | [WorkoutModels.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutModels.kt) |
| WorkoutSnapshot.effectiveMenu / menuExercises / menuItem / rolloverTo（拡張） | 当日・preset・既定 menu の優先、種目への投影、日付移行の純粋処理。 | [WorkoutModels.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutModels.kt) |
| WorkoutReader / WorkoutRepository（interface） | load による snapshot 読取と save による更新を分離する。 | [WorkoutRepository.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutRepository.kt) |
| WorkoutHistoryExporter（fun interface）、WorkoutExportResult（enum） | export(history) が成功・権限不足・利用不可・失敗を返す outbound 契約。 | [WorkoutHistoryExporter.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutHistoryExporter.kt) |
| WorkoutAiProvider / WorkoutAiRequestType / WorkoutAiTaskState（enum） | 実行先、提案・レビューの依頼種別、queued から終端までのタスク状態。 | [WorkoutAi.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAi.kt) |
| WorkoutAiSettings / WorkoutAiReview / WorkoutAiTaskSnapshot / WorkoutAiTaskReference（data class） | provider と方針、日付付き応答、タスク状態と回収用参照。 | [WorkoutAi.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAi.kt) |
| WorkoutAiSettingsRepository / WorkoutAiReviewRepository / WorkoutAiAdvisor / WorkoutAiTaskController（interface） | 設定・日付メモ、レビュー保存、生成、enqueue / snapshot / recoverableTask / dismiss の capability。 | [WorkoutAi.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAi.kt) |
| WorkoutAiPromptBuilder（object） | build が一次記録と二次レビューを区別した prompt を作る。recentDates は取得するメモの日付集合を選ぶ。 | [WorkoutAi.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAi.kt) |
| formatWorkoutSetDetails（関数） | 入力された詳細だけを文字列化し、タイマーや時刻から実測休憩を推定しない。 | [WorkoutSetDetailText.kt](../../../feature/workout/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutSetDetailText.kt) |

## 主要 API と接続

`WorkoutRepository` は Data の `DefaultWorkoutRepository`、`WorkoutHistoryExporter` は `HealthConnectWorkoutHistoryExporter` に接続される。AI 設定契約は loadSettings / saveSettings / loadMemo / saveMemo / loadMemos、レビューは save / loadAll、Advisor は generate(provider,prompt)。TaskController は request ID を返し、結果と回収を UI の寿命から分離する。

## 代表的な処理フロー

`newWorkoutSnapshot` で初期種目と menu → `effectiveMenu` が当日の入力対象 → UI が WorkoutSet を追加 → `rolloverTo` または完了で History を形成 → Repository save。AI は `recentDates` でメモを選び、`build` が当日・過去のセットと実施時 menu を組み立て、MENU_SUGGESTION にだけ参考レビューを加える。

`WorkoutAiPromptBuilder` は各セットを `formatSetForAi` で整形し、`formatWorkoutSetDetails` を通じて記録済みの RPE・フォーム・負荷・直前の休憩を prompt に含める。未入力の詳細は出力せず、休憩をタイマー設定や時刻から推定しない。[WorkoutAiPromptBuilderTest.kt](../../../feature/workout/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAiPromptBuilderTest.kt) は `POST_WORKOUT_REVIEW` の prompt に各項目が含まれることを確認する。

接続先: [runtime の生成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)、[画面 Factory の注入](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)。

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
