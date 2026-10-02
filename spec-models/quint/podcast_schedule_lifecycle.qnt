// Natural-language specifications:
// - docs/spec/13-background-execution.md
//
// Covers:
// - PODCAST-SCHEDULE-LIFECYCLE-001@sha256:8d54b16b2c5fc7087e9ee66bb0fd729c752218fd6e7850ab2e11d9f80cc1f344 -> enableAndSync
// - PODCAST-SCHEDULE-LIFECYCLE-001@sha256:8d54b16b2c5fc7087e9ee66bb0fd729c752218fd6e7850ab2e11d9f80cc1f344 -> ensureExistingSchedule
// - PODCAST-SCHEDULE-LIFECYCLE-001@sha256:8d54b16b2c5fc7087e9ee66bb0fd729c752218fd6e7850ab2e11d9f80cc1f344 -> scheduledWorkMissing
// - PODCAST-SCHEDULE-LIFECYCLE-001@sha256:8d54b16b2c5fc7087e9ee66bb0fd729c752218fd6e7850ab2e11d9f80cc1f344 -> ensureMissingSchedule
// - PODCAST-SCHEDULE-LIFECYCLE-001@sha256:8d54b16b2c5fc7087e9ee66bb0fd729c752218fd6e7850ab2e11d9f80cc1f344 -> scheduledSuccess
// - PODCAST-SCHEDULE-LIFECYCLE-001@sha256:8d54b16b2c5fc7087e9ee66bb0fd729c752218fd6e7850ab2e11d9f80cc1f344 -> scheduledPausedSkip
// - PODCAST-SCHEDULE-LIFECYCLE-001@sha256:8d54b16b2c5fc7087e9ee66bb0fd729c752218fd6e7850ab2e11d9f80cc1f344 -> scheduledGenerationFailure
// - PODCAST-SCHEDULE-LIFECYCLE-001@sha256:8d54b16b2c5fc7087e9ee66bb0fd729c752218fd6e7850ab2e11d9f80cc1f344 -> alreadyRunningRetry
// - PODCAST-SCHEDULE-LIFECYCLE-001@sha256:8d54b16b2c5fc7087e9ee66bb0fd729c752218fd6e7850ab2e11d9f80cc1f344 -> retrySameOccurrence
// - PODCAST-SCHEDULE-LIFECYCLE-001@sha256:8d54b16b2c5fc7087e9ee66bb0fd729c752218fd6e7850ab2e11d9f80cc1f344 -> cancelSchedule
// - PODCAST-SCHEDULE-LIFECYCLE-001@sha256:8d54b16b2c5fc7087e9ee66bb0fd729c752218fd6e7850ab2e11d9f80cc1f344 -> manualGenerationDoesNotAdvanceSchedule
// - PODCAST-SCHEDULE-LIFECYCLE-001@sha256:8d54b16b2c5fc7087e9ee66bb0fd729c752218fd6e7850ab2e11d9f80cc1f344 -> disabledHasNoScheduledWork
//
// Abstraction:
// One program's scheduled chain is modeled. occurrence distinguishes the
// currently scheduled local-wall-clock occurrence from its successor; values 1
// and 2 alternate so the finite model can verify "same occurrence" vs "next
// occurrence" without modeling unbounded dates. Manual generation shares the
// generation worker implementation but is not part of this schedule chain.

module podcast_schedule_lifecycle {
  val NONE = 0
  val ARMED = 1
  val RUNNING = 2
  val RETRYING = 3

  var state: int
  var enabled: bool
  var occurrence: int

  action init = all {
    state' = NONE,
    enabled' = false,
    occurrence' = 0,
  }

  action enableAndSync = all {
    state' = ARMED,
    enabled' = true,
    occurrence' = if (occurrence == 1) 2 else 1,
  }

  action ensureExistingSchedule = all {
    enabled,
    state == ARMED,
    state' = ARMED,
    enabled' = true,
    occurrence' = occurrence,
  }

  action scheduledWorkMissing = all {
    enabled,
    state == ARMED,
    state' = NONE,
    enabled' = true,
    occurrence' = 0,
  }

  action ensureMissingSchedule = all {
    enabled,
    state == NONE,
    state' = ARMED,
    enabled' = true,
    occurrence' = 1,
  }

  action startScheduledOccurrence = all {
    enabled,
    state == ARMED,
    state' = RUNNING,
    enabled' = true,
    occurrence' = occurrence,
  }

  action scheduledSuccess = all {
    enabled,
    state == RUNNING,
    state' = ARMED,
    enabled' = true,
    occurrence' = if (occurrence == 1) 2 else 1,
  }

  action scheduledPausedSkip = all {
    enabled,
    state == RUNNING,
    state' = ARMED,
    enabled' = true,
    occurrence' = if (occurrence == 1) 2 else 1,
  }

  action scheduledGenerationFailure = all {
    enabled,
    state == RUNNING,
    state' = ARMED,
    enabled' = true,
    occurrence' = if (occurrence == 1) 2 else 1,
  }

  action alreadyRunningRetry = all {
    enabled,
    state == RUNNING,
    state' = RETRYING,
    enabled' = true,
    occurrence' = occurrence,
  }

  action retrySameOccurrence = all {
    enabled,
    state == RETRYING,
    state' = RUNNING,
    enabled' = true,
    occurrence' = occurrence,
  }

  action manualGenerationDoesNotAdvanceSchedule = all {
    state' = state,
    enabled' = enabled,
    occurrence' = occurrence,
  }

  action cancelSchedule = all {
    state' = NONE,
    enabled' = false,
    occurrence' = 0,
  }

  action idle = all {
    state' = state,
    enabled' = enabled,
    occurrence' = occurrence,
  }

  action step = any {
    enableAndSync,
    ensureExistingSchedule,
    scheduledWorkMissing,
    ensureMissingSchedule,
    startScheduledOccurrence,
    scheduledSuccess,
    scheduledPausedSkip,
    scheduledGenerationFailure,
    alreadyRunningRetry,
    retrySameOccurrence,
    manualGenerationDoesNotAdvanceSchedule,
    cancelSchedule,
    idle,
  }

  val knownState =
    state == NONE or state == ARMED or state == RUNNING or state == RETRYING

  val knownOccurrence =
    occurrence == 0 or occurrence == 1 or occurrence == 2

  val disabledHasNoScheduledWork =
    enabled or (state == NONE and occurrence == 0)

  val activeScheduleRequiresEnabled =
    state == NONE or enabled

  val activeScheduleHasOccurrence =
    state == NONE or occurrence == 1 or occurrence == 2

  val safety =
    knownState and
    knownOccurrence and
    disabledHasNoScheduledWork and
    activeScheduleRequiresEnabled and
    activeScheduleHasOccurrence
}
