---
type: module
title: "Workout Data：保存、AI Worker、外部 export"
description: "snapshot の互換性、実行時 prompt 再構築と Workout-owned Health Connect export。"
tags: [workout, data, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-03e97711729f1e7bfc34591a
    resource: repo://feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/DefaultWorkoutRepository.kt
  - id: openwiki-source-6429838b62a796633b6ca2d3
    resource: repo://feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/WorkoutAiBackground.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Workout Data：保存、AI Worker、外部 export

`:feature:workout:data` はWorkout の保存実装、設定とレビューの保存、AI provider adapter、WorkManager task、Health Connect への一方向 export を所有する。`DefaultWorkoutRepository` は SharedPreferences の JSON snapshot を読み書きする。database の健康 read data を source として使わず、アプリ内の Workout を正本とする。外部 export は `HealthConnectWorkoutHistoryExporter` が Workout の履歴を変換する outbound adapter であり、Health feature の読取 Repository は経由しない。

## 保存と互換性

未保存の場合は当日の新しい snapshot を返す。JSON の decode 失敗では新規 snapshot に戻すが、未対応の state version は専用例外を再送出する。保存済み未知形式を現行として解釈して上書きする扱いではない。メニュー・セット・履歴メニューの encode/decode を変える場合は `DefaultWorkoutRepositoryTest` の旧版読込、未知版保持、履歴メニュー round-trip を確認する。

## AI の寿命と結果

enqueue では request type と選択 provider の metadata だけを WorkManager へ渡す。cloud は network constraint を設定し、同じ request type の unique work は置換する。Worker は実行のたびに最新 snapshot、設定、対象日メモ、保存レビューから prompt を再構築する。provider が一時停止中なら retry、local は background gate の permit の中で実行する。取消は再送出し、その他の失敗は task failure と安全なエラー表示へ変換する。レビューは生成後に日付付きで保存する。

`DefaultWorkoutAiAdvisorTest` は選択 provider と prompt budget、`DefaultWorkoutAiReviewRepositoryTest` は同日レビュー置換、export test は時刻補正・権限・失敗結果を検証する。推論を ViewModel 内へ移したり、Health の書込 capability を新設する前に、background と export ownership の ADR を確認する。
## 変更時の調査先

- [DefaultWorkoutRepository.kt](../../../feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/DefaultWorkoutRepository.kt)
- [WorkoutAiBackground.kt](../../../feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/WorkoutAiBackground.kt)
- [HealthConnectWorkoutHistoryExporter.kt](../../../feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/HealthConnectWorkoutHistoryExporter.kt)
- [DefaultWorkoutRepositoryTest.kt](../../../feature/workout/data/src/test/kotlin/dev/terashima/yomitorirss/feature/workout/data/DefaultWorkoutRepositoryTest.kt)
- [DefaultWorkoutAiAdvisorTest.kt](../../../feature/workout/data/src/test/kotlin/dev/terashima/yomitorirss/feature/workout/data/DefaultWorkoutAiAdvisorTest.kt)
- [DefaultWorkoutAiReviewRepositoryTest.kt](../../../feature/workout/data/src/test/kotlin/dev/terashima/yomitorirss/feature/workout/data/DefaultWorkoutAiReviewRepositoryTest.kt)
- [HealthConnectWorkoutHistoryExporterTest.kt](../../../feature/workout/data/src/test/kotlin/dev/terashima/yomitorirss/feature/workout/data/HealthConnectWorkoutHistoryExporterTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [domain 層](workout-domain.md)、[ui 層](workout-ui.md)、[システム全体](../../architecture/system.md)。
