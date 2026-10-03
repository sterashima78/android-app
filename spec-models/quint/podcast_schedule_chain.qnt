// Natural-language specifications:
// - docs/spec/13-background-execution.md
//
// Covers:
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> enableSchedule
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> replaceSchedule
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> ensureExistingSchedule
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> ensureRecoveredSchedule
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> disableSchedule
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> startScheduledRun
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> scheduledSuccess
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> scheduledFailure
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> scheduledProviderPaused
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> scheduledAlreadyRunning
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> retryScheduledRun
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> scheduledCancellationNeedsExternalRecovery
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> startManualRun
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> completeManualRun
// - PODCAST-SCHEDULE-CHAIN-001@sha256:3bbfbb56d5bf7bc27654dff342de5802cb0f878a00302be1a936371bd0d688e4 -> retryDoesNotAdvanceSchedule
//
// Abstraction:
// One Podcast program is modeled. The automatic chain is separate from manual
// generation/regeneration work. AUTO_RETRY_WAIT represents WorkManager retry of
// the same scheduled occurrence after the program-level generation guard rejects
// a concurrent start. AUTO_RECOVERY_REQUIRED represents cancellation: the Worker
// itself does not append the next occurrence and later schedule reconciliation
// must restore the chain. Concrete wall-clock arithmetic, WorkManager backoff,
// network constraints, and episode generation are implementation concerns.

module podcast_schedule_chain {
  val AUTO_NONE = 0
  val AUTO_SCHEDULED = 1
  val AUTO_RUNNING = 2
  val AUTO_RETRY_WAIT = 3
  val AUTO_RECOVERY_REQUIRED = 4

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

  action ensureExistingSchedule = all {
    scheduleEnabled,
    autoState != AUTO_RECOVERY_REQUIRED,
    scheduleEnabled' = scheduleEnabled,
    autoState' = autoState,
    rescheduleSource' = rescheduleSource,
    manualRunning' = manualRunning,
  }

  action ensureRecoveredSchedule = all {
    scheduleEnabled,
    autoState == AUTO_RECOVERY_REQUIRED,
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

  action scheduledAlreadyRunning = all {
    scheduleEnabled,
    autoState == AUTO_RUNNING,
    scheduleEnabled' = true,
    autoState' = AUTO_RETRY_WAIT,
    rescheduleSource' = SOURCE_NONE,
    manualRunning' = manualRunning,
  }

  action retryScheduledRun = all {
    scheduleEnabled,
    autoState == AUTO_RETRY_WAIT,
    scheduleEnabled' = true,
    autoState' = AUTO_RUNNING,
    rescheduleSource' = SOURCE_NONE,
    manualRunning' = manualRunning,
  }

  action scheduledCancellationNeedsExternalRecovery = all {
    scheduleEnabled,
    autoState == AUTO_RUNNING,
    scheduleEnabled' = true,
    autoState' = AUTO_RECOVERY_REQUIRED,
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
    ensureExistingSchedule,
    ensureRecoveredSchedule,
    disableSchedule,
    startScheduledRun,
    scheduledSuccess,
    scheduledFailure,
    scheduledProviderPaused,
    scheduledAlreadyRunning,
    retryScheduledRun,
    scheduledCancellationNeedsExternalRecovery,
    startManualRun,
    duplicateManualStartIgnored,
    completeManualRun,
    idle,
  }

  val knownAutoState =
    autoState == AUTO_NONE or
    autoState == AUTO_SCHEDULED or
    autoState == AUTO_RUNNING or
    autoState == AUTO_RETRY_WAIT or
    autoState == AUTO_RECOVERY_REQUIRED

  val knownRescheduleSource =
    rescheduleSource == SOURCE_NONE or
    rescheduleSource == SOURCE_WORKER or
    rescheduleSource == SOURCE_EXTERNAL

  val disabledScheduleHasNoAutomaticWork =
    scheduleEnabled or autoState == AUTO_NONE

  val enabledScheduleHasKnownChainState =
    not(scheduleEnabled) or autoState != AUTO_NONE

  val scheduledStateHasRescheduleSource =
    not(autoState == AUTO_SCHEDULED) or
    rescheduleSource == SOURCE_WORKER or rescheduleSource == SOURCE_EXTERNAL

  val runningOrRetryHasNoNextSchedule =
    not(autoState == AUTO_RUNNING or autoState == AUTO_RETRY_WAIT) or
    rescheduleSource == SOURCE_NONE

  val recoveryRequiresExternalScheduler =
    not(autoState == AUTO_RECOVERY_REQUIRED) or
    rescheduleSource == SOURCE_EXTERNAL

  val retryDoesNotAdvanceSchedule =
    not(autoState == AUTO_RETRY_WAIT) or rescheduleSource == SOURCE_NONE

  val safety =
    knownAutoState and
    knownRescheduleSource and
    disabledScheduleHasNoAutomaticWork and
    enabledScheduleHasKnownChainState and
    scheduledStateHasRescheduleSource and
    runningOrRetryHasNoNextSchedule and
    recoveryRequiresExternalScheduler and
    retryDoesNotAdvanceSchedule
}
