// Natural-language specifications:
// - docs/spec/13-background-execution.md
//
// Covers:
// - PODCAST-SCHEDULE-CHAIN-001@sha256:4df2098c47fdfb9aaa5a562cec6866976e9b0fb70a7c9cfc6236c96f82addd4e -> enableSchedule
// - PODCAST-SCHEDULE-CHAIN-001@sha256:4df2098c47fdfb9aaa5a562cec6866976e9b0fb70a7c9cfc6236c96f82addd4e -> replaceSchedule
// - PODCAST-SCHEDULE-CHAIN-001@sha256:4df2098c47fdfb9aaa5a562cec6866976e9b0fb70a7c9cfc6236c96f82addd4e -> startScheduledRun
// - PODCAST-SCHEDULE-CHAIN-001@sha256:4df2098c47fdfb9aaa5a562cec6866976e9b0fb70a7c9cfc6236c96f82addd4e -> scheduledSuccess
// - PODCAST-SCHEDULE-CHAIN-001@sha256:4df2098c47fdfb9aaa5a562cec6866976e9b0fb70a7c9cfc6236c96f82addd4e -> scheduledFailure
// - PODCAST-SCHEDULE-CHAIN-001@sha256:4df2098c47fdfb9aaa5a562cec6866976e9b0fb70a7c9cfc6236c96f82addd4e -> scheduledProviderPaused
// - PODCAST-SCHEDULE-CHAIN-001@sha256:4df2098c47fdfb9aaa5a562cec6866976e9b0fb70a7c9cfc6236c96f82addd4e -> scheduledCancellationHandledExternally
// - PODCAST-SCHEDULE-CHAIN-001@sha256:4df2098c47fdfb9aaa5a562cec6866976e9b0fb70a7c9cfc6236c96f82addd4e -> startManualRun
// - PODCAST-SCHEDULE-CHAIN-001@sha256:4df2098c47fdfb9aaa5a562cec6866976e9b0fb70a7c9cfc6236c96f82addd4e -> completeManualRun
// - PODCAST-SCHEDULE-CHAIN-001@sha256:4df2098c47fdfb9aaa5a562cec6866976e9b0fb70a7c9cfc6236c96f82addd4e -> enabledScheduleAlwaysHasChainState
// - PODCAST-SCHEDULE-CHAIN-001@sha256:4df2098c47fdfb9aaa5a562cec6866976e9b0fb70a7c9cfc6236c96f82addd4e -> scheduledStateHasRescheduleSource
//
// Abstraction:
// One Podcast program is modeled. The automatic chain is separate from manual
// generation/regeneration work. Concrete wall-clock arithmetic, network
// constraints, WorkManager backoff, and episode generation are implementation
// concerns covered by unit tests and other formal models.

module podcast_schedule_chain {
  val AUTO_NONE = 0
  val AUTO_SCHEDULED = 1
  val AUTO_RUNNING = 2

  val SOURCE_NONE = 0
  val SOURCE_WORKER = 1
  val SOURCE_EXTERNAL = 2

  var scheduleEnabled: bool
  var autoState: int
  var rescheduleSource: int
  var manualRunning: bool

  action init = all {
    scheduleEnabled' = false,
    autoState' = AUTO_NONE,
    rescheduleSource' = SOURCE_NONE,
    manualRunning' = false,
  }

  action enableSchedule = all {
    not(scheduleEnabled),
    scheduleEnabled' = true,
    autoState' = AUTO_SCHEDULED,
    rescheduleSource' = SOURCE_EXTERNAL,
    manualRunning' = manualRunning,
  }

  action replaceSchedule = all {
    scheduleEnabled,
    scheduleEnabled' = true,
    autoState' = AUTO_SCHEDULED,
    rescheduleSource' = SOURCE_EXTERNAL,
    manualRunning' = manualRunning,
  }

  action disableSchedule = all {
    scheduleEnabled,
    scheduleEnabled' = false,
    autoState' = AUTO_NONE,
    rescheduleSource' = SOURCE_NONE,
    manualRunning' = manualRunning,
  }

  action startScheduledRun = all {
    scheduleEnabled,
    autoState == AUTO_SCHEDULED,
    scheduleEnabled' = true,
    autoState' = AUTO_RUNNING,
    rescheduleSource' = SOURCE_NONE,
    manualRunning' = manualRunning,
  }

  action scheduledSuccess = all {
    scheduleEnabled,
    autoState == AUTO_RUNNING,
    scheduleEnabled' = true,
    autoState' = AUTO_SCHEDULED,
    rescheduleSource' = SOURCE_WORKER,
    manualRunning' = manualRunning,
  }

  action scheduledFailure = all {
    scheduleEnabled,
    autoState == AUTO_RUNNING,
    scheduleEnabled' = true,
    autoState' = AUTO_SCHEDULED,
    rescheduleSource' = SOURCE_WORKER,
    manualRunning' = manualRunning,
  }

  action scheduledProviderPaused = all {
    scheduleEnabled,
    autoState == AUTO_RUNNING,
    scheduleEnabled' = true,
    autoState' = AUTO_SCHEDULED,
    rescheduleSource' = SOURCE_WORKER,
    manualRunning' = manualRunning,
  }

  action scheduledCancellationHandledExternally = all {
    scheduleEnabled,
    autoState == AUTO_RUNNING,
    scheduleEnabled' = true,
    autoState' = AUTO_SCHEDULED,
    rescheduleSource' = SOURCE_EXTERNAL,
    manualRunning' = manualRunning,
  }

  action startManualRun = all {
    not(manualRunning),
    scheduleEnabled' = scheduleEnabled,
    autoState' = autoState,
    rescheduleSource' = rescheduleSource,
    manualRunning' = true,
  }

  action duplicateManualStartIgnored = all {
    manualRunning,
    scheduleEnabled' = scheduleEnabled,
    autoState' = autoState,
    rescheduleSource' = rescheduleSource,
    manualRunning' = true,
  }

  action completeManualRun = all {
    manualRunning,
    scheduleEnabled' = scheduleEnabled,
    autoState' = autoState,
    rescheduleSource' = rescheduleSource,
    manualRunning' = false,
  }

  action idle = all {
    scheduleEnabled' = scheduleEnabled,
    autoState' = autoState,
    rescheduleSource' = rescheduleSource,
    manualRunning' = manualRunning,
  }

  action step = any {
    enableSchedule,
    replaceSchedule,
    disableSchedule,
    startScheduledRun,
    scheduledSuccess,
    scheduledFailure,
    scheduledProviderPaused,
    scheduledCancellationHandledExternally,
    startManualRun,
    duplicateManualStartIgnored,
    completeManualRun,
    idle,
  }

  val knownAutoState =
    autoState == AUTO_NONE or
    autoState == AUTO_SCHEDULED or
    autoState == AUTO_RUNNING

  val knownRescheduleSource =
    rescheduleSource == SOURCE_NONE or
    rescheduleSource == SOURCE_WORKER or
    rescheduleSource == SOURCE_EXTERNAL

  val disabledScheduleHasNoAutomaticWork =
    scheduleEnabled or autoState == AUTO_NONE

  val enabledScheduleAlwaysHasChainState =
    not(scheduleEnabled) or
    autoState == AUTO_SCHEDULED or
    autoState == AUTO_RUNNING

  val scheduledStateHasRescheduleSource =
    not(autoState == AUTO_SCHEDULED) or
    rescheduleSource == SOURCE_WORKER or rescheduleSource == SOURCE_EXTERNAL

  val runningStateHasNoRescheduleSource =
    not(autoState == AUTO_RUNNING) or rescheduleSource == SOURCE_NONE

  val safety =
    knownAutoState and
    knownRescheduleSource and
    disabledScheduleHasNoAutomaticWork and
    enabledScheduleAlwaysHasChainState and
    scheduledStateHasRescheduleSource and
    runningStateHasNoRescheduleSource
}
