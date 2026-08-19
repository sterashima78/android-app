# ADR-0125: Calendar を複数 Context と Android Calendar Provider の read model として扱う

- Status: Accepted
- Date: 2026-08-19
- Refines: ADR-0103, ADR-0106, ADR-0116, ADR-0120, ADR-0122

## Context

アプリ内の Task は期限、Workout は実績日を持ち、端末には Google Calendar 等の calendar account から同期された予定が存在する。これらを日付軸でまとめて閲覧したい。

Calendar 自身が Task / Workout の状態を複製して永続化したり、各 feature の table / file を直接参照すると、ADR-0106 以降で明確化した Context ownership と persistence ownership を崩す。一方、Google Calendar REST API を Calendar 専用に導入すると、端末に既に同期されている calendar data に対して別の OAuth / synchronization path を持つことになる。

また、Calendar UI は「予定」「期限」「実績」を同じ時間軸へ配置したいが、source 固有の意味や表示情報まで消すと、予定の calendar color、Task の deadline 表示、Workout の activity 表示を適切に区別できない。

## Decision

### 1. Calendar は durable event state を所有しない read model とする

`:feature:calendar:{domain,data,ui}` を追加するが、Calendar 専用の event table / file は作成しない。

Calendar domain は期間を指定して統一イベントを取得する `CalendarRepository` と、表示に必要な `CalendarEvent` を定義する。Data implementation は各 source の現在状態を問い合わせ、その場で `CalendarEvent` へ投影する。

Calendar は read-only projection であり、Task / Workout / external calendar の command ownership は引き続き各 source に残す。

### 2. Domain では全 source を同一の `CalendarEvent` として扱う

`CalendarEvent` は source に関係なく次の共通情報を持つ。

- identity
- title / description / location
- `CalendarEventTime`
  - `Timed(start, endExclusive)`
  - `AllDay(startDate, endDateExclusive)`
- `CalendarEventSource`
- `CalendarEventKind`
- optional `CalendarEventSourceMetadata`

`source` と `kind` は分離する。

初期 source:

- `DEVICE_CALENDAR`
- `TASK`
- `WORKOUT`

初期 kind:

- `SCHEDULE`
- `DEADLINE`
- `ACTIVITY`

これにより UI は source icon と semantic kind の両方を利用でき、将来別 source から同じ kind の event を追加しても model を増殖させずに済む。

### 3. 具体的な Compose color は Domain に持ち込まない

Task / Workout の色は `CalendarEventKind` 等から UI theme が決定する。

端末 calendar が提供する calendar / event color は external source metadata として ARGB 値を保持してよい。ただし Domain は Compose `Color` 等の UI framework type に依存しない。

### 4. 端末 calendar は Android Calendar Provider の `Instances` を読む

外部予定は Android `CalendarContract.Instances` を期間指定で query する。これにより端末へ同期済みの Google Calendar 等を Android の共通 calendar provider 経由で扱い、繰り返し予定も occurrence 単位で投影する。

初期実装は read-only とし、Manifest permission は `READ_CALENDAR` のみに限定する。`WRITE_CALENDAR` は要求しない。

Calendar permission が未許可または失効している場合、device calendar source は空として扱い、Task / Workout event の表示は継続する。

Google Calendar REST API / Calendar 専用 OAuth は、この read-only viewer の初期実装には導入しない。

### 5. Task / Workout は owner Domain API 経由で読む

`:feature:calendar:data` は次を利用する。

- `TaskRepository.listTasks()` から `dueDate` のある Task を `DEADLINE` / all-day event へ投影
- `WorkoutRepository.load()` から日付を持つ workout history / current result を `ACTIVITY` / all-day event へ投影

Calendar data は Task / Workout の table、database connection、private storage file を直接参照しない。

### 6. Composition / navigation は既存 app boundary に従う

- repository lifetime は `AppContainer`
- root `CalendarViewModel.Factory` は `AppRouteDependencies`
- Android runtime permission launcher は app route adapter
- Calendar feature 固有 UI state は `:feature:calendar:ui`
- navigation metadata は `AppNavigationSpec`

とし、ADR-0103 / ADR-0116 の route ownership を維持する。

## Consequences

### Positive

- Calendar のために Task / Workout data を複製しない
- Calendar domain / UI は source ごとの storage implementation を知らない
- Google account の予定は端末の既存同期経路を再利用できる
- Google Calendar API 用 OAuth / token management を追加せずに済む
- `READ_CALENDAR` を拒否してもアプリ内 event source は利用できる
- source と kind を分けることで、色・icon・label を拡張しやすい
- Calendar Provider の recurring event を occurrence 単位で扱える

### Negative

- device calendar の内容は Android 側の同期状態に依存する
- Calendar Provider を持たない / 同期されていない calendar は表示できない
- source repository を期間 query に最適化していないため、Task / Workout は現状全件 load 後に Calendar data layer で期間 filter する
- Calendar UI 自身から予定・Task・Workout を編集する command は初期実装に含まれない

## Testing

- Task due date -> all-day `DEADLINE` mapping
- date-less / range-out Task exclusion
- Workout result -> all-day `ACTIVITY` mapping
- multi-day all-day event の exclusive end
- midnight を跨ぐ timed event の date occurrence
- app navigation mapping は既存 `AppNavigationSpecTest` の exhaustiveness により Calendar 追加後も検証する

Android Calendar Provider の実 provider 内容は端末環境に依存するため、pure mapping / date semantics を unit test し、provider integration は permission と実端末同期済み calendar を用いた manual / device verification の対象とする。

## Public repository consideration

実アカウント名、メールアドレス、calendar ID、event title、OAuth credential 等を repository へ保存しない。実行時に Calendar Provider から取得した値は表示用の in-memory model としてのみ扱う。

## Documentation

この判断に合わせて `docs/architecture/context-map.md` と `docs/architecture/module-map.md` を更新する。
