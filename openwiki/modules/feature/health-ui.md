---
type: module
title: "Health UI：期間閲覧と権限状態"
description: "日・週・月の表示、過去履歴 permission gating、チャートと欠測表示。"
tags: [health, ui, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-1bd862c2675f9e97f83f0587
    resource: repo://feature/health/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/health/HealthViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Health UI：期間閲覧と権限状態

`:feature:health:ui` はHealthRoute、Compose の健康情報カード、期間選択と権限案内を所有する。Domain の `HealthRepository` と Clock/timezone を使う ViewModel が overview を取得し、運動、栄養、体脂肪を各カードや chart へ渡す。Health Connect の permission rationale activity と Activity Result の画面接続もここで調べられる。platform record の取得規則は Data にある。

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
