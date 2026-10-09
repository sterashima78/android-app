---
type: module
title: Calendar UI：月表示と日付の occurrence
description: 月移動、選択日、期間取得の競合防止と日跨ぎイベントの表示。
tags:
  - calendar
  - ui
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-03b7ee6f6f85f3f2e19f1d51
    resource: repo://feature/calendar/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarViewModel.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---
# Calendar UI：月表示と日付の occurrence

`:feature:calendar:ui` は月のグリッドと選択日の agenda を表示する。`CalendarScreen` は ViewModel と権限状態・権限要求 callback を受け取り、画面の操作を ViewModel に返す。端末カレンダーの permission launcher そのものや storage の生成をこの画面契約で決めない。Domain の `CalendarEvent` を表示の入力にする。


## 主要な構成要素

| 構成要素・種類 | 責務・主要 API と関係 | 実装 |
| --- | --- | --- |
| CalendarUiState（data class） | 表示月・選択日・イベント・loading・error の一時状態。 | [CalendarViewModel.kt](../../../feature/calendar/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarViewModel.kt) |
| CalendarViewModel / Factory（class） | state を公開し、previousMonth / nextMonth / goToToday / selectDate / reload が日付操作と読込を担当する。Factory は Domain Repository を注入する。 | [CalendarViewModel.kt](../../../feature/calendar/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarViewModel.kt) |
| CalendarScreen（Composable 関数） | ViewModel、権限の有無と要求 callback を受け、月グリッドと agenda を表示する。 | [CalendarScreen.kt](../../../feature/calendar/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarScreen.kt) |
| MonthHeader / MonthGrid / DayCell / CalendarEventRow（private Composable） | 月操作、日付選択とイベント点、選択日の行に分割する。CalendarPermissionCard / ErrorState が許可と再試行の導線を持つ。 | [CalendarScreen.kt](../../../feature/calendar/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarScreen.kt) |
| CalendarEvent.occursOn（internal 拡張） | 一覧と月グリッドの双方が使う日付 occurrence 判定。 | [CalendarViewModel.kt](../../../feature/calendar/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarViewModel.kt) |
| CALENDAR_ROUTE / CALENDAR_TITLE（定数） | アプリが使う route と画面タイトル。 | [NavigationDestination.kt](../../../feature/calendar/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/NavigationDestination.kt) |

## 主要 API と接続

`CalendarViewModel.Factory` は `AppSupportingRouteDependencies.calendarViewModelFactory` で `container.calendarRepository` に接続される。`CalendarScreen` は権限 launcher を引数の外に置き、`calendarPermissionGranted` が false でもアプリ内 source のイベントを表示できる。`reload` は状態を loading にして期間取得し、失敗 message を保存する。

## 代表的な処理フロー

`CalendarScreen` の月矢印 → `previousMonth` / `nextMonth` → private `selectMonth` が選択日番号を丸める → `reload` → Repository の期間取得 → state → `occursOn(selectedDate)` で agenda を作る。日付が同月なら `selectDate` は選択日だけを変える。

接続先: [runtime の生成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)、[画面 Factory の注入](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)。

## 月切替の状態管理

ViewModel は Clock から初期の当日と月を決定し、月初から翌月初までのイベントを読み込む。前月・翌月への移動では選択中の日番号を新しい月の末日以内へ丸める。同じ月内の日の選択は一覧を再取得せず、別月の日の選択と「今日へ戻る」は期間読込を行う。

reload は前の load job を cancel する。応答やエラーを反映するときにも現在の月が要求した月と一致するか調べ、古い月の取得結果による上書きを防ぐ。失敗は再試行できるエラー表示へ渡す。月や選択日は UI の状態であり、Calendar の durable な event state ではない。

`occursOn` は終日イベントの終了日を除外し、時刻付きイベントは端末 timezone へ変換する。終了から 1ms を引いて日を求めるため、ちょうど翌日午前0時までの予定が翌日の agenda に余計に表示されない。`CalendarEventOccurrenceTest` は複数日の終日と日跨ぎ時刻イベントを検証する。期間や timezone を変える際は Data の重なり判定との整合も確かめる。
## 変更時の調査先

- [CalendarViewModel.kt](../../../feature/calendar/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarViewModel.kt)
- [CalendarScreen.kt](../../../feature/calendar/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarScreen.kt)
- [CalendarEventOccurrenceTest.kt](../../../feature/calendar/ui/src/test/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarEventOccurrenceTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [domain 層](calendar-domain.md)、[data 層](calendar-data.md)、[システム全体](../../architecture/system.md)。
