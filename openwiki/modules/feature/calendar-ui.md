---
type: module
title: "Calendar UI：月表示と日付の occurrence"
description: "月移動、選択日、期間取得の競合防止と日跨ぎイベントの表示。"
tags: [calendar, ui, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-03b7ee6f6f85f3f2e19f1d51
    resource: repo://feature/calendar/ui/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarViewModel.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Calendar UI：月表示と日付の occurrence

`:feature:calendar:ui` は月のグリッドと選択日の agenda を表示する。`CalendarScreen` は ViewModel と権限状態・権限要求 callback を受け取り、画面の操作を ViewModel に返す。端末カレンダーの permission launcher そのものや storage の生成をこの画面契約で決めない。Domain の `CalendarEvent` を表示の入力にする。

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
