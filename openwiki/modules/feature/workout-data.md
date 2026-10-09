---
type: module
title: Workout Data：保存、AI Worker、外部 export
description: snapshot の互換性、実行時 prompt 再構築と Workout-owned Health Connect export。
tags:
  - workout
  - data
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-03e97711729f1e7bfc34591a
    resource: repo://feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/DefaultWorkoutRepository.kt
  - id: openwiki-source-6429838b62a796633b6ca2d3
    resource: repo://feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/WorkoutAiBackground.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---
# Workout Data：保存、AI Worker、外部 export

`:feature:workout:data` はWorkout の保存実装、設定とレビューの保存、AI provider adapter、WorkManager task、Health Connect への一方向 export を所有する。`DefaultWorkoutRepository` は SharedPreferences の JSON snapshot を読み書きする。database の健康 read data を source として使わず、アプリ内の Workout を正本とする。外部 export は `HealthConnectWorkoutHistoryExporter` が Workout の履歴を変換する outbound adapter であり、Health feature の読取 Repository は経由しない。


## 主要な構成要素

| 構成要素・種類 | 責務・主要 API と関係 | 実装 |
| --- | --- | --- |
| DefaultWorkoutRepository（class）、UnsupportedWorkoutStateVersionException（internal class） | load / save が SharedPreferences JSON を扱い、encode / decode が互換性を管理する。未知版を新規状態へ黙って落とさない。 | [DefaultWorkoutRepository.kt](../../../feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/DefaultWorkoutRepository.kt) |
| DefaultWorkoutAiSettingsRepository（class） | loadSettings / saveSettings、loadMemo / saveMemo / loadMemos を同じ preferences の設定・日付別キーへ接続する。 | [DefaultWorkoutAiSettingsRepository.kt](../../../feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/DefaultWorkoutAiSettingsRepository.kt) |
| DefaultWorkoutAiReviewRepository（class） | save は日付キーのレビューを置換し、loadAll は不正項目を除外して日付・生成時刻の降順へ並べる。 | [DefaultWorkoutAiReviewRepository.kt](../../../feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/DefaultWorkoutAiReviewRepository.kt) |
| DefaultWorkoutAiAdvisor（class） | generate は provider で BackgroundAiTextInference を選び、選択モデルの prompt 予算に収める。 | [DefaultWorkoutAiAdvisor.kt](../../../feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/DefaultWorkoutAiAdvisor.kt) |
| WorkManagerWorkoutAiTaskController（class） | enqueue / snapshot / recoverableTask / dismiss が WorkManager と回収用 Store を橋渡しする。 | [WorkoutAiBackground.kt](../../../feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/WorkoutAiBackground.kt) |
| WorkoutAiWorker / WorkoutAiWorkerFactory（class） | doWork が実行制御、private generate が最新入力読取・prompt・推論・レビュー保存を担当する。Factory が Domain capability を Worker に注入する。 | [WorkoutAiBackground.kt](../../../feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/WorkoutAiBackground.kt) |
| WorkoutAiTaskStore（private class） | noBackupFilesDir の requestId / type / response を同期して保存する。旧タスクの結果が現在の参照を上書きしないよう ID を検査する。 | [WorkoutAiBackground.kt](../../../feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/WorkoutAiBackground.kt) |
| HealthConnectWorkoutHistoryExporter（class）、WorkoutHealthConnectGateway（internal interface）、AndroidWorkoutHealthConnectGateway（private class） | export を record 変換、platform 可用性・書込許可、insert に分ける。Gateway はテスト用接点。 | [HealthConnectWorkoutHistoryExporter.kt](../../../feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/HealthConnectWorkoutHistoryExporter.kt) |
| WorkoutHistory.toHealthConnectExerciseSessionRecord（internal 拡張） | set を segment へ変換し、重なりを補正した session と安定した clientRecordId を作る。 | [HealthConnectWorkoutHistoryExporter.kt](../../../feature/workout/data/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/data/HealthConnectWorkoutHistoryExporter.kt) |

## 主要 API と接続

`AppSupportingRuntimeDependencies` が Repository / Settings / Review / Advisor / Controller / Exporter を application scope に生成する。WorkerFactory には capability を返す provider を渡し、`AppWorkerFactory` がその Factory を登録する。Advisor は local / cloud の選択を固定し、自動 fallback をしない。Exporter の WRITE_PERMISSIONS は WorkoutRoute へ渡す。

## 代表的な処理フロー

`WorkoutAiTaskController.enqueue` → 設定から provider を確定 → request metadata と unique work を登録 → Worker が pause を検査し foreground 通知 → 最新 snapshot / 設定 / メモ / レビューで prompt → Advisor → レビューなら保存 → taskStore に応答 → UI が snapshot で回収。enqueue 失敗時は対応参照を削除、欠落・不正 ID は FAILED とし、recoverableTask の一時的な lookup 失敗では参照を残す。

Exporter は session 変換失敗を FAILED、利用不可を UNAVAILABLE、書込許可不足を PERMISSION_REQUIRED、insert の失敗を FAILED と返す。可用性・許可検査自体の例外はここで一括捕捉しておらず、UI の呼出し経路でも確認する。

接続先: [runtime の生成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)、[画面 Factory の注入](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)。

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
