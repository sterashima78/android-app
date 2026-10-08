---
type: module
title: "Health Data：Health Connect 読取と重複除去"
description: "期間集計、page token 読取、栄養の日別集約と運動 session の代表選択。"
tags: [health, data, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-2d30eb295dced2580bd4cdde
    resource: repo://feature/health/data/src/main/kotlin/dev/terashima/yomitorirss/feature/health/data/ExerciseSessionDeduplication.kt
  - id: openwiki-source-cd3da51008ca8af7e121f19a
    resource: repo://feature/health/data/src/main/kotlin/dev/terashima/yomitorirss/feature/health/data/HealthConnectHealthRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Health Data：Health Connect 読取と重複除去

`:feature:health:data` はHealthRepository を Health Connect client に接続する Android library である。application context から client を遅延作成し、provider 状態、読取 permission と履歴 feature の対応を調べる。保存先の database を持たず、platform の record を Domain の read model に変換して返す。Workout の exporter が同じ platform を利用していても、ここは健康情報の読取を担当する。

## 期間集計と詳細

`readOverview` は start より end が後であることを検査し、期間集計と詳細の読取を組み合わせる。歩数・消費熱量・運動時間・心拍・睡眠・体重は aggregate API、日別 summary は期間 group の集計から得る。体脂肪、栄養、運動 session は page token をたどって record を取得し、表示用の値へ変換する。運動 session の活動詳細集計は短い要求期間に限定し、長い期間で各 session の追加取得を増やし過ぎない。

運動 session は情報の豊富な候補を優先して重複除去する。運動種別と開始終了の完全一致は同一候補と扱う。同じ既知提供元の非完全一致は時間が重なっても別として保持する。異なる提供元では強い時間重複と長さの一致、または詳細 segment と単独 session の一致を比較する。単に開始時刻が近いという条件だけで実績を消さない。

権限を取得する UI や取消表示はこのモジュールの責務ではなく、呼出し側の permission gating と組み合わせる。`HealthConnectPermissionsTest` は権限集合、`NutritionAggregationTest` は日付単位集約、`ExerciseSessionMappingTest` は変換と重複候補の境界を検証する。閾値や record 種類を変える際は仕様と形式モデルも確認する。
## 変更時の調査先

- [HealthConnectHealthRepository.kt](../../../feature/health/data/src/main/kotlin/dev/terashima/yomitorirss/feature/health/data/HealthConnectHealthRepository.kt)
- [ExerciseSessionDeduplication.kt](../../../feature/health/data/src/main/kotlin/dev/terashima/yomitorirss/feature/health/data/ExerciseSessionDeduplication.kt)
- [HealthConnectPermissionsTest.kt](../../../feature/health/data/src/test/kotlin/dev/terashima/yomitorirss/feature/health/data/HealthConnectPermissionsTest.kt)
- [NutritionAggregationTest.kt](../../../feature/health/data/src/test/kotlin/dev/terashima/yomitorirss/feature/health/data/NutritionAggregationTest.kt)
- [ExerciseSessionMappingTest.kt](../../../feature/health/data/src/test/kotlin/dev/terashima/yomitorirss/feature/health/data/ExerciseSessionMappingTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [domain 層](health-domain.md)、[ui 層](health-ui.md)、[システム全体](../../architecture/system.md)。
