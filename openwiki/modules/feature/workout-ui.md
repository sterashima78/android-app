---
type: module
title: "Workout UI：実績入力、タイマー、AI 結果"
description: "当日の運動状態、保存後の export、メニュー取り込みと AI task の回収。"
tags: [workout, ui, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-3db5400475f27b166717e654
    resource: repo://feature/workout/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/workout/WorkoutViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Workout UI：実績入力、タイマー、AI 結果

`:feature:workout:ui` はWorkoutRoute と Compose 画面、当日の入力、タイマー、履歴、メニュー管理、AI チャット表示を所有する。`WorkoutViewModel` は Domain の Repository と `WorkoutHistoryExporter` を受け取り、`WorkoutAiViewModel` は設定・メモ・レビュー・task controller を使う。AI と通常の実績入力を別の状態として扱い、画面が推論エンジンを直接実行する構成にはしない。

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
