---
type: module
title: Health UI：期間閲覧と権限状態
description: 日・週・月の表示、過去履歴 permission gating、チャートと欠測表示。
tags:
  - health
  - ui
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-8644c9f81264e3481c173ed5
    resource: repo://feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthRoute.kt
  - id: openwiki-source-1bd862c2675f9e97f83f0587
    resource: repo://feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthViewModel.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---
# Health UI：期間閲覧と権限状態

`:feature:health:ui` はHealthRoute、Compose の健康情報カード、期間選択と権限案内を所有する。Domain の `HealthRepository` と Clock/timezone を使う ViewModel が overview を取得し、運動、栄養、体脂肪を各カードや chart へ渡す。Health Connect の permission rationale activity と Activity Result の画面接続もここで調べられる。platform record の取得規則は Data にある。


## 主要な構成要素

| 構成要素・種類 | 責務・主要 API と関係 | 実装 |
| --- | --- | --- |
| HealthRoute（Composable） | Factory と readPermissions を受け、Activity Result の権限結果を ViewModel に返す。 | [HealthRoute.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthRoute.kt) |
| HealthPeriod（enum）、HealthDateRange（data class）、HealthUiState（sealed interface） | 日・週・月、排他的日付区間、loading / 許可不足 / 非対応 / content / error を区別する。Content と Error は data class、各静的状態は data object。 | [HealthViewModel.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthViewModel.kt) |
| HealthViewModel / Factory（class） | selectPeriod / selectDate / movePrevious / moveNext / goToday / refresh / onPermissionResult が期間選択と取得を管理する。 | [HealthViewModel.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthViewModel.kt) |
| HealthPeriod.dateRange / shift、HealthDateRange.requiresHistory（internal 拡張） | 暦の単位と履歴 access の検査対象を決める。 | [HealthViewModel.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthViewModel.kt) |
| HealthScreen / HealthContent / PeriodNavigation / HealthDatePicker / DailySummaryCard（private Composable） | 状態ごとの導線、期間操作、日別指標の表示。Metric / MetricGrid / MetricCard は欠測表示を含む指標カード。 | [HealthRoute.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthRoute.kt) |
| HealthPermissionsRationaleActivity（class） | onCreate で利用目的・読取と Workout 書込の分離を説明する Android Activity。manifest が rationale action と usage alias を登録する。 | [HealthPermissionsRationaleActivity.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthPermissionsRationaleActivity.kt) |
| ExerciseHistoryCard / formatExerciseActivitySummary / formatExerciseSessionTimeRange / formatExerciseTime / formatExerciseDuration（internal 関数） | session の一覧・展開詳細と nullable 活動値、時刻・日跨ぎ・期間表記。 | [ExerciseHistoryCard.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/ExerciseHistoryCard.kt) |
| BodyFatChartBounds / WeightChartBounds（internal data class）、bodyFatChartBounds / weightChartBounds / latestBodyFatPercentage（internal 関数） | 体組成の描画範囲と最新測定を計算する。 | [BodyFatHistoryChart.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/BodyFatHistoryChart.kt) |
| BodyCompositionHistoryChart / formatBodyFatPercentage / formatWeightChartValue（internal 関数） | 体重と体脂肪を別軸で描画し、小数の表示を整える。 | [BodyFatHistoryChart.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/BodyFatHistoryChart.kt) |
| NutritionSummaryCard（internal Composable） | 日表示で栄養合計と欠測を表示する。 | [NutritionSummaryCard.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/NutritionSummaryCard.kt) |
| NutritionMetric（internal enum）、NutritionHistoryCard / metricValue / referenceRange（internal 関数） | 栄養の選択指標、日別実績と Domain の参考帯を対応付ける。 | [NutritionHistoryCard.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/NutritionHistoryCard.kt) |
| formatSleepHours（internal 関数） | nullable な睡眠分を時間へ変換し、欠測はダッシュで表示する。 | [HealthRoute.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthRoute.kt) |
| HEALTH_ROUTE / HEALTH_TITLE（定数） | navigation metadata。 | [NavigationDestination.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/NavigationDestination.kt) |

## 主要 API と接続

`AppHealthRuntimeDependencies` が application scope の `HealthConnectHealthRepository` を生成し、`AppSupportingRouteDependencies.health` が Factory と `requestPermissions()` の集合を渡す。`refresh` は state の owner であり、Activity Result の結果集合を UI が推定するのでなく Repository の権限を再検査する。

## 代表的な処理フロー

`HealthRoute` → `HealthViewModel.refresh` → 可用性と権限 gate → dateRange を Instant に変換し終了を現在時刻に制限 → readOverview → HealthUiState.Content → `HealthContent`。日なら NutritionSummaryCard、週・月なら NutritionHistoryCard と体組成 chart、体重線は週だけ、運動履歴は月には表示しない。

接続先: [runtime の生成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)、[画面 Factory の注入](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)。Health Repository の生成は [AppHealthRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/health/AppHealthRuntimeDependencies.kt) を参照する。

## 可用性と履歴の gating

refresh は前の load job を cancel し、まず provider が利用可能か調べる。更新が必要な場合と利用不可の場合は別 UI state とし、必要読取権限がなければ permission 要求へ進む。選択範囲が古い履歴に入る場合には履歴 access を追加検査し、非対応と権限不足を区別する。権限結果が返ると再取得する。

期間は日、月曜始まりの週、月初から翌月初までの月に分かれる。未来の日付選択を当日へ丸め、実際に読む終了時刻も現在時刻までに制限する。前後移動は選択期間の単位に従う。端末 timezone と Clock を注入できるため、暦境界と未来制限は固定時刻で検証できる。

CancellationException は再送出して通常エラー表示に変えず、SecurityException は権限必要 state、その他の例外は error state にする。UI state は一時的な read model で、Workout への同期や保存に使わない。`HealthViewModelTest` は可用性・権限・期間を、chart と formatting のテストは欠測、単位、履歴表示を確認する。参考栄養 profile の変更は Domain の検証も合わせる。
## 変更時の調査先

- [HealthRoute.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthRoute.kt)
- [HealthViewModel.kt](../../../feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthViewModel.kt)
- [HealthViewModelTest.kt](../../../feature/health/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/health/HealthViewModelTest.kt)
- [BodyFatHistoryChartTest.kt](../../../feature/health/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/health/BodyFatHistoryChartTest.kt)
- [HealthPresentationFormattingTest.kt](../../../feature/health/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/health/HealthPresentationFormattingTest.kt)
- [ExerciseHistoryCardTest.kt](../../../feature/health/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/health/ExerciseHistoryCardTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [domain 層](health-domain.md)、[data 層](health-data.md)、[システム全体](../../architecture/system.md)。
