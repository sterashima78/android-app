---
type: module
title: Health Domain：健康情報の read model
description: Health Connect 可用性、読取権限、日別・栄養・運動表示の契約。
tags:
  - health
  - domain
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-b94c03c9eb713924e27cf1ea
    resource: repo://feature/health/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthModels.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---
# Health Domain：健康情報の read model

`:feature:health:domain` はHealth Connect の内容を画面へ投影するための Kotlin/JVM read model を定義する。`HealthRepository` は利用可能性、必要読取権限、履歴への access、期間の overview を公開する。健康情報の書込や Workout import の command は提供しない。取得した値は表示のための入力で、アプリ database に複製する aggregate ではない。外部への運動履歴 export は Workout 側に置く。


## 主要な構成要素

| 構成要素・種類 | 責務・主要 API と関係 | 実装 |
| --- | --- | --- |
| HealthAvailability / HealthHistoryAccess（enum） | provider の可用性・更新必要状態と、過去データ読取の権限・対応状態を別々に表す。 | [HealthModels.kt](../../../feature/health/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthModels.kt) |
| BodyFatMeasurement / DailyHealthSummary / DailyNutritionIntake（data class） | 時刻付き体脂肪と暦日の健康・栄養集計。健康指標の nullable は欠測を表す。 | [HealthModels.kt](../../../feature/health/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthModels.kt) |
| HealthExerciseSegmentSummary / HealthExerciseSessionSummary（data class） | session と segment の期間・種目・回数、session の任意活動指標を分けて保持する。 | [HealthModels.kt](../../../feature/health/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthModels.kt) |
| NutritionReferenceRange / NutritionReferenceProfile（data class）、AdultMaleNutritionReference（object） | 参考の min/max と栄養 profile。standard / weightLoss が UI の比較表示の入力になる。 | [HealthModels.kt](../../../feature/health/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthModels.kt) |
| HealthOverview（data class）、HealthRepository（interface） | 期間全体の指標と詳細一覧をまとめ、availability / hasRequiredPermissions / historyAccess / readOverview を公開する。 | [HealthModels.kt](../../../feature/health/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthModels.kt) |

## 主要 API と接続

`availability()` は同期の enum、`hasRequiredPermissions()` は suspend Boolean、`historyAccess()` は suspend な対応状態を返す。`readOverview(startTime, endTime)` は Instant の期間を受ける。具体実装は `HealthConnectHealthRepository`、利用者は `HealthViewModel`。platform record・permission launcher・database を Domain 型へ混ぜない。

## 代表的な処理フロー

`HealthViewModel.refresh` → availability → 必要権限 → 古い範囲なら historyAccess → readOverview → HealthOverview → UI の期間集計・栄養・運動・体組成表示。AdultMaleNutritionReference はこの readOverview の取得結果ではなく、独立した参考 profile として UI へ渡す。

接続先: [runtime の生成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)、[画面 Factory の注入](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)。Health Repository の生成は [AppHealthRuntimeDependencies](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/health/AppHealthRuntimeDependencies.kt) を参照する。

## 不可用と欠測を区別する

provider の利用可否は AVAILABLE、UNAVAILABLE、更新要求に分ける。履歴 access も利用可能・権限必要・非対応を分け、画面で更新案内と権限案内を取り違えないようにする。Overview は歩数、消費熱量、運動時間、心拍、睡眠、体重に加え、体脂肪測定、日別栄養、運動 session、日別 summary を保持する。主要集計の nullable 値は未取得を表し、常に 0 と同じ意味にしない。

運動 session は開始終了、種目名、notes、segments と任意の活動集計を持つ。platform の record 型を直接 UI に渡さず、この契約を通じて表示を組み立てる。栄養参考値は年齢・活動水準のラベル付き標準と減量参考 profile として定義され、範囲は負の最小値と上下逆転を拒否する。個人に合わせた診断や provider 由来の値という扱いにはしない。

モデル追加では Data の変換と UI の欠測表示を一緒に追う。`AdultMaleNutritionReferenceTest` が参考 profile のエネルギー・栄養比率を検証する。read/write ownership は [Alloy model](../../../spec-models/alloy/workout_health_data_boundary.als) と仕様を確認する。
## 変更時の調査先

- [HealthModels.kt](../../../feature/health/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthModels.kt)
- [AdultMaleNutritionReferenceTest.kt](../../../feature/health/domain/src/test/kotlin/dev/terashima/yomitorirss/feature/health/AdultMaleNutritionReferenceTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [data 層](health-data.md)、[ui 層](health-ui.md)、[システム全体](../../architecture/system.md)。
