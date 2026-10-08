---
type: module
title: "Calendar Domain：読み取り専用イベント契約"
description: "予定、タスク期限、運動実績を統一する時間表現と read-only capability。"
tags: [calendar, domain, modules]
verified:
  - by: openwiki/0.7.1
    at: 2026-10-08T13:53:53.288Z
sources:
  - id: openwiki-source-cac35e08be646cf23f29b8d7
    resource: repo://feature/calendar/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarModels.kt
  - id: openwiki-source-b48f432a8167a53c9276f91f
    resource: repo://feature/calendar/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarRepository.kt
generated: { by: "codex", at: "2026-10-08T13:53:53.288Z" }
---

# Calendar Domain：読み取り専用イベント契約

`:feature:calendar:domain` は各 source の情報を同じ時間軸に置くための Kotlin/JVM 契約を所有する。`CalendarRepository.events` は開始日を含み終了日を含まない期間を受け取り、`CalendarEvent` の一覧を返す。追加・編集・削除 command は公開しない。Calendar 自身の保存形式ではなく、その時点の情報を表示する read model である。元の Task、Workout、端末予定の ownership は各 source に残る。

## 時間と意味を分ける

イベントはタイトル、説明、場所に加え、source と kind を別々に持つ。source は端末カレンダー・Task・Workout の由来、kind は予定・期限・活動という意味を表す。この区別により、UI は由来のアイコンと意味の色を別々に判断できる。source を増やすときに時間表現まで個別 DTO に分裂させる必要はない。

時間は `Timed` と `AllDay` に分かれる。前者は Instant の開始と任意の終了、後者は LocalDate の開始と排他的な終了日を持つ。端末予定の色は任意 metadata の ARGB 値であり、Compose の Color 型を Domain へ持ち込まない。期間の正当性の検査や Provider からの変換は Data、ある日に出現するかの表示判定は UI を追う。

このモジュールに専用 unit test は現在なく、時間契約は `CalendarEventMappingTest` と `CalendarEventOccurrenceTest` で使われる。read-only ownership の変更は [ADR-0128](../../../docs/adr/0128-calendar-read-model-and-android-calendar-provider.md) と [Alloy model](../../../spec-models/alloy/calendar_read_model_ownership.als) も確認する。
## 変更時の調査先

- [CalendarRepository.kt](../../../feature/calendar/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarRepository.kt)
- [CalendarModels.kt](../../../feature/calendar/domain/src/main/kotlin/dev/terashima/yomitorirss/feature/calendar/CalendarModels.kt)

仕様の正本は [機能仕様](../../../docs/spec/08-task-calendar-workout-health.md)、依存・所有権は [Context map](../../../docs/architecture/context-map.md) を参照する。

関連: [data 層](calendar-data.md)、[ui 層](calendar-ui.md)、[システム全体](../../architecture/system.md)。
