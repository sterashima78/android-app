---
type: module
title: "Calendar Data：三つの source の期間投影"
description: "端末 Calendar Provider、TaskReader、WorkoutReader の統合と権限拒否時の縮退。"
tags: [calendar, data, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-cd6bf1b1367680c6f6f06262
    resource: repo://feature/calendar/data/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/data/DefaultCalendarRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Calendar Data：三つの source の期間投影

`:feature:calendar:data` は端末カレンダー、タスク期限、運動実績を `CalendarEvent` へ投影する。`DefaultCalendarRepository` は `TaskReader` と `WorkoutReader` を受け取り、Android の `CalendarContract.Instances` を期間指定で読む。独自 event table や元 feature の private storage は利用せず、読取能力を経由して現在状態を統合する。処理は IO dispatcher で実行する。

## 取得と変換

空または逆向きの期間を拒否する。Task は期日があるレコードだけを期限の終日イベントへ変換し、完了済みも含めて説明に完了を付ける。Workout は履歴とセットを持つ当日の記録を活動の終日イベントへ変換する。無効日付、範囲外、セットなしは除外する。結果は開始時刻に相当する値とタイトルで並ぶ。

端末予定は Instances から occurrence 単位で取得する。終日データは UTC で日付へ戻し、終了日が開始日以下なら翌日に補正する。時刻付きイベントは実時刻を保持する。返却前には端末の timezone を使って要求日付範囲との重なりを確かめる。

Provider query の `SecurityException` は端末 source の空一覧に変換するため、権限がなくても Task と Workout の取得は続く。これは全ての例外を握り潰す扱いではなく、元 Repository の失敗は呼出し側へ伝わる。`CalendarEventMappingTest` が期限なし・範囲外の除外と重なりを検証する。端末の同期済み予定と権限の実動作は Android Provider を使った端末確認も必要になる。
## 変更時の調査先

- [DefaultCalendarRepository.kt](../../../feature/calendar/data/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/data/DefaultCalendarRepository.kt)
- [CalendarEventMappingTest.kt](../../../feature/calendar/data/src/test/kotlin/dev/terashima/yomitorirss/feature/calendar/data/CalendarEventMappingTest.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [domain 層](calendar-domain.md)、[ui 層](calendar-ui.md)、[システム全体](../../architecture/system.md)。
