---
type: module
title: Calendar Data：三つの source の期間投影
description: 端末 Calendar Provider、TaskReader、WorkoutReader の統合と権限拒否時の縮退。
tags:
  - calendar
  - data
  - modules
verified:
  - by: openwiki/0.7.1
    at: 2026-10-09T02:32:57.934Z
sources:
  - id: openwiki-source-cd6bf1b1367680c6f6f06262
    resource: repo://feature/calendar/data/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/data/DefaultCalendarRepository.kt
generated: { by: "codex", at: "2026-10-09T02:32:57.934Z" }
---
# Calendar Data：三つの source の期間投影

`:feature:calendar:data` は端末カレンダー、タスク期限、運動実績を `CalendarEvent` へ投影する。`DefaultCalendarRepository` は `TaskReader` と `WorkoutReader` を受け取り、Android の `CalendarContract.Instances` を期間指定で読む。独自 event table や元 feature の private storage は利用せず、読取能力を経由して現在状態を統合する。処理は IO dispatcher で実行する。


## 主要な構成要素

| 構成要素・種類 | 責務・主要 API と関係 | 実装 |
| --- | --- | --- |
| DefaultCalendarRepository（class） | CalendarRepository 実装。events は IO dispatcher で端末予定・タスク・運動を順に集めて並べる。 | [DefaultCalendarRepository.kt](../../../feature/calendar/data/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/data/DefaultCalendarRepository.kt) |
| AndroidCalendarEventSource（private class） | ContentResolver を受け、events で CalendarContract.Instances を occurrence 単位に問い合わせる。 | [DefaultCalendarRepository.kt](../../../feature/calendar/data/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/data/DefaultCalendarRepository.kt) |
| taskCalendarEvents / workoutCalendarEvents（internal 関数） | TaskItem の期限、WorkoutDay のセット実績を終日イベントへ写す。期限なし・無効な日付・範囲外・セットなしを除外する。 | [DefaultCalendarRepository.kt](../../../feature/calendar/data/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/data/DefaultCalendarRepository.kt) |
| CalendarEvent.overlapsDateRange（internal 拡張）、calendarEventSortKey / workoutDescription（private 関数） | 端末 timezone で区間重なりを検査し、開始値の並べ替えと種目ごとの説明文を作る。 | [DefaultCalendarRepository.kt](../../../feature/calendar/data/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/data/DefaultCalendarRepository.kt) |

## 主要 API と接続

`events` は空・逆向き区間を `require` で拒否する。端末読取後に `taskReader.listTasks()`、`workoutReader.load()` を呼び、履歴とセットを持つ当日を `WorkoutDay` にして投影する。`AppSupportingRuntimeDependencies.calendarRepository` は同じ application scope の Task / Workout Repository を Reader として渡す。

## 代表的な処理フロー

`AndroidCalendarEventSource.events` が Provider の終日値を UTC の日付に戻す → `overlapsDateRange` で絞る → `taskCalendarEvents` が期日を一日区間へ変換 → `workoutCalendarEvents` が運動日を活動へ変換 → `calendarEventSortKey` と title で並ぶ。Task / Workout 取得例外は伝播し、Provider query の SecurityException だけが端末予定の空一覧へ縮退する。

接続先: [runtime の生成](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/supporting/AppSupportingRuntimeDependencies.kt)、[画面 Factory の注入](../../../app/composition/src/main/java/dev/terashima/yomitorirss/composition/route/AppSupportingRouteDependencies.kt)。

## 取得と変換

空または逆向きの期間を拒否する。Task は期日があるレコードだけを期限の終日イベントへ変換し、完了済みも含めて説明に完了を付ける。Workout は履歴とセットを持つ当日の記録を活動の終日イベントへ変換する。無効日付、範囲外、セットなしは除外する。結果は開始時刻に相当する値とタイトルで並ぶ。

端末予定は Instances から occurrence 単位で取得する。終日データは UTC で日付へ戻し、終了日が開始日以下なら翌日に補正する。時刻付きイベントは実時刻を保持する。返却前には端末の timezone を使って要求日付範囲との重なりを確かめる。

Provider query の `SecurityException` は端末 source の空一覧に変換するため、権限がなくても Task と Workout の取得は続く。これは全ての例外を握り潰す扱いではなく、元 Repository の失敗は呼出し側へ伝わる。`CalendarEventMappingTest` が期限なし・範囲外の除外と重なりを検証する。端末の同期済み予定と権限の実動作は Android Provider を使った端末確認も必要になる。
## 変更時の調査先

- [DefaultCalendarRepository.kt](../../../feature/calendar/data/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/data/DefaultCalendarRepository.kt)
- [CalendarEventMappingTest.kt](../../../feature/calendar/data/src/test/kotlin/dev/terashima/yomitorirss/feature/calendar/data/CalendarEventMappingTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [domain 層](calendar-domain.md)、[ui 層](calendar-ui.md)、[システム全体](../../architecture/system.md)。
