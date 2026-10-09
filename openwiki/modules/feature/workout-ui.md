---
type: module
title: Workout UI：実績入力、タイマー、AI 結果
description: 当日の運動状態、保存後の export、メニュー取り込みと AI task の回収。
tags:
  - workout
  - ui
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-3db5400475f27b166717e654
    resource: repo://feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutViewModel.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---
# Workout UI：実績入力、タイマー、AI 結果

`:feature:workout:ui` はWorkoutRoute と Compose 画面、当日の入力、タイマー、履歴、メニュー管理、AI チャット表示を所有する。`WorkoutViewModel` は Domain の Repository と `WorkoutHistoryExporter` を受け取り、`WorkoutAiViewModel` は設定・メモ・レビュー・task controller を使う。AI と通常の実績入力を別の状態として扱い、画面が推論エンジンを直接実行する構成にはしない。


## 主要な構成要素

| 構成要素・種類 | 責務・主要 API と関係 | 実装 |
| --- | --- | --- |
| WorkoutRoute（Composable） | 通常入力・AI の Factory と書込 permission 集合を受け、権限結果を通常 ViewModel へ返す。 | [WorkoutRoute.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutRoute.kt) |
| WorkoutTab（enum）、WorkoutUiState（data class） | snapshot と選択・入力・タイマー・export / menu 通知。activeMenu / menuExercises / activeExercise / activeSets / nextTarget は派生値、detailsValid / selectedLoad が詳細入力検査。 | [WorkoutViewModel.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutViewModel.kt) |
| WorkoutViewModel / Factory（class） | Repository / Exporter と monotonic clock を受け、種目・menu・set・完了・タイマーを扱う。 | [WorkoutViewModel.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutViewModel.kt) |
| WorkoutAiUiState / WorkoutAiViewModel / Factory（data class・class） | 設定・メモ・レビューと AI request / response を通常入力から独立して保持する。 | [WorkoutAiViewModel.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAiViewModel.kt) |
| WorkoutScreen（Composable） | 記録・タイマー・履歴・チャット・設定を切り替え、intervalCompletionToken の変化で音と haptic を出す。 | [WorkoutScreen.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutScreen.kt) |
| WorkoutLogScreen / WorkoutSetDetailInputs / StopwatchControls / WorkoutTimerScreen / WorkoutHistoryScreen / WorkoutSettingsScreen（private Composable） | 入力・任意詳細・時間計測・履歴コピー・menu 管理を通常 ViewModel の API に接続する。 | [WorkoutScreen.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutScreen.kt) |
| WorkoutAiChatScreen / WorkoutAiSettingsSection（Composable） | 提案とレビュー、回収結果、実行先と cloud 送信対象の表示。提案の適用・保存は callback で通常入力へ戻す。 | [WorkoutAiPanel.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAiPanel.kt) |
| WorkoutMenuImportExercise / WorkoutMenuImportDraft（data class）、parseWorkoutMenuImport（関数） | 構造化 JSON を種目ごとの ID・単位・種別・目標へ正規化し、不正 version・空種目・非正目標を拒否する。 | [WorkoutMenuImport.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutMenuImport.kt) |
| formatWorkoutHistoryExercise / formatWorkoutHistoryForCopy（internal 関数） | 種目別総計と入力された set 詳細をコピー文にまとめる。 | [WorkoutHistoryCopyText.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutHistoryCopyText.kt) |
| WORKOUT_ROUTE / WORKOUT_TITLE（定数） | navigation metadata。 | [NavigationDestination.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/NavigationDestination.kt) |

## 主要 API と接続

`WorkoutViewModel` の API は以下の責務に分かれる。

- 選択・メニュー: `selectTab / selectExercise / selectMenu / importMenu / saveTodayMenuAsPreset / removeMenu`。import は parser で検査後、既存 ID または名前で種目に対応付け、重複種目を拒否し、当日 menu と必要なら preset を更新する。
- set 入力: `updateAmount / updateMemo / updateStepCount / updateRpe / updateFormQuality / updateLoadKind / updateLoadValue / updateRestSeconds / reuseLastLoad / adjustAmount`。値の制限・詳細検査を行い、`recordSet / undoActiveSet` で snapshot を変える。
- 実績と種目: `startWorkout / finishWorkout / onExportPermissionResult / resetToday / addExercise / removeExercise / restoreDefaultExercises`。削除は menu 参照も更新し、完了は保存後に Exporter を呼ぶ。
- タイマー: `setIntervalDuration / startInterval / pauseInterval / resetInterval`、`startPlank / pausePlank / resetPlank / recordPlank`、`startStepUp / pauseStepUp / resetStepUp / recordStepUp`。private `tick` は経過時計から計測し、`appendSet` が UI の任意詳細を追加して保存する。

`WorkoutAiViewModel` は `updateMemo / setProvider / updateWorkoutPolicy` で保存契約へ書き、`requestMenuSuggestion / requestPostWorkoutReview` で task を登録、`clearResponse` で回収参照を閉じる。`AppSupportingRouteDependencies.workout` が Data capability と writePermissions を二つの Factory / Route に接続する。

## 代表的な処理フロー

AI 提案の「今日使う」 → `WorkoutAiChatScreen.onApplyMenu` → `WorkoutViewModel.importMenu(source=GENERATED)` → `parseWorkoutMenuImport` → 種目と menu の snapshot 更新 → 非同期 save → 通常の set 記録。AI 実行自体は `WorkoutAiViewModel.request` → Controller enqueue → private `observeTask` の snapshot polling で追跡する。ViewModel 再作成時は `resumeRecoverableTask` が回収を再開し、レビュー成功では保存レビューを再読込する。

`updateSnapshot` と初期 load/save は専用の error state へ一括変換していない。`finishWorkout` の save/export は同じ runCatching に入り、保存の失敗も export の FAILED 表示になるため、表示だけで端末保存成功を保証しない。

接続先: [runtime の生成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)、[画面 Factory の注入](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)。

## 完了と外部連携

完了操作はセットなしなら何も行わない。セットがあれば当日実績と予定メニューを履歴 snapshot にし、新しい空の当日を用意してタイマーを reset する。次の snapshot を端末内 Repository に保存してから履歴 exporter を呼ぶ。外部書込の権限不足や失敗は結果メッセージとして表示し、記録の正本は Workout に残る。権限を許可した後の再試行は最新履歴の export をやり直し、保存済み実績を再生成しない。

メニュー JSON の入力は UI の import parser で構造を検査し、当日利用とプリセット保存を分ける。各セットの回数・秒数、間隔や種目タイマー、メモはそれぞれ UI 操作から snapshot や一時状態へ反映する。コピー用履歴文もこの層の表示責務である。

AI ViewModel は task を登録し、recoverable task を再取得して状態と結果を表示する。レビュー成功後は保存済みレビューを再読込し、提案結果は通常メニューの取り込み経路へ渡す。provider 選択と cloud 送信の表示を変える際は `WorkoutAiPanel` も確認する。`WorkoutViewModelExportTest` は保存と権限再試行、`WorkoutAiViewModelTest` は画面の寿命を越えた task 回収、コピー文 test は表示内容の検証入口となる。
## 変更時の調査先

- [WorkoutRoute.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutRoute.kt)
- [WorkoutViewModel.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutViewModel.kt)
- [WorkoutAiViewModel.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAiViewModel.kt)
- [WorkoutMenuImport.kt](../../../feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutMenuImport.kt)
- [WorkoutViewModelExportTest.kt](../../../feature/workout/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutViewModelExportTest.kt)
- [WorkoutAiViewModelTest.kt](../../../feature/workout/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutAiViewModelTest.kt)
- [WorkoutHistoryCopyTextTest.kt](../../../feature/workout/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutHistoryCopyTextTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [domain 層](workout-domain.md)、[data 層](workout-data.md)、[システム全体](../../architecture/system.md)。
