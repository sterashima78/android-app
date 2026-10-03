// Natural-language specifications:
// - docs/spec/08-task-calendar-workout-health.md
//
// Covers:
// - CALENDAR-PROJECTION-OWNERSHIP-001@sha256:62c9087e42510ce72c3bb9e3e032df4cfd195ecfd695306b5821ec2d947f8112 -> NoCalendarDurableOwnership
// - CALENDAR-PROJECTION-OWNERSHIP-001@sha256:62c9087e42510ce72c3bb9e3e032df4cfd195ecfd695306b5821ec2d947f8112 -> NoCalendarCommandOwnership
// - CALENDAR-PROJECTION-OWNERSHIP-001@sha256:62c9087e42510ce72c3bb9e3e032df4cfd195ecfd695306b5821ec2d947f8112 -> EveryProjectionHasExactlyOneSource
// - CALENDAR-PROJECTION-OWNERSHIP-001@sha256:62c9087e42510ce72c3bb9e3e032df4cfd195ecfd695306b5821ec2d947f8112 -> SourceKindMatches
//
// Scope:
// The model represents ownership and projection relations, not date-range
// filtering or Android provider query semantics. CalendarEvent is ephemeral
// read-model output. Durable state and command ownership remain with Task,
// Workout, or the external device-calendar platform.

module calendar_read_model_ownership

abstract sig Owner {}
one sig CalendarOwner, TaskOwner, WorkoutOwner, DeviceCalendarPlatform extends Owner {}

abstract sig EventKind {}
one sig Schedule, Deadline, Activity extends EventKind {}

abstract sig SourceRecord {
  owner: one Owner
}
sig DeviceCalendarRecord extends SourceRecord {}
sig TaskDeadlineRecord extends SourceRecord {}
sig WorkoutActivityRecord extends SourceRecord {}

sig CalendarEvent {
  source: one SourceRecord,
  kind: one EventKind
}

abstract sig DurableState {
  owner: one Owner
}
sig TaskDurableState extends DurableState {}
sig WorkoutDurableState extends DurableState {}
sig DeviceCalendarState extends DurableState {}

abstract sig CommandCapability {
  owner: one Owner
}
one sig TaskCommand extends CommandCapability {}
one sig WorkoutCommand extends CommandCapability {}

fact SourceOwnership {
  all record: DeviceCalendarRecord | record.owner = DeviceCalendarPlatform
  all record: TaskDeadlineRecord | record.owner = TaskOwner
  all record: WorkoutActivityRecord | record.owner = WorkoutOwner
}

fact DurableOwnership {
  all state: TaskDurableState | state.owner = TaskOwner
  all state: WorkoutDurableState | state.owner = WorkoutOwner
  all state: DeviceCalendarState | state.owner = DeviceCalendarPlatform
  no state: DurableState | state.owner = CalendarOwner
}

fact CommandOwnership {
  TaskCommand.owner = TaskOwner
  WorkoutCommand.owner = WorkoutOwner
  no command: CommandCapability | command.owner = CalendarOwner
}

fact ProjectionKinds {
  all event: CalendarEvent |
    (event.source in DeviceCalendarRecord implies event.kind = Schedule) and
    (event.source in TaskDeadlineRecord implies event.kind = Deadline) and
    (event.source in WorkoutActivityRecord implies event.kind = Activity)
}

assert NoCalendarDurableOwnership {
  no state: DurableState | state.owner = CalendarOwner
}

assert NoCalendarCommandOwnership {
  no command: CommandCapability | command.owner = CalendarOwner
}

assert EveryProjectionHasExactlyOneSource {
  all event: CalendarEvent | one event.source
}

assert SourceKindMatches {
  all event: CalendarEvent |
    (event.source in DeviceCalendarRecord implies event.kind = Schedule) and
    (event.source in TaskDeadlineRecord implies event.kind = Deadline) and
    (event.source in WorkoutActivityRecord implies event.kind = Activity)
}

check NoCalendarDurableOwnership for 10 expect 0
check NoCalendarCommandOwnership for 10 expect 0
check EveryProjectionHasExactlyOneSource for 10 expect 0
check SourceKindMatches for 10 expect 0

run RepresentativeCalendarProjection {
  some DeviceCalendarRecord
  some TaskDeadlineRecord
  some WorkoutActivityRecord
  some TaskDurableState
  some WorkoutDurableState
  some DeviceCalendarState
  some event: CalendarEvent | event.source in DeviceCalendarRecord
  some event: CalendarEvent | event.source in TaskDeadlineRecord
  some event: CalendarEvent | event.source in WorkoutActivityRecord
} for 10 expect 1
