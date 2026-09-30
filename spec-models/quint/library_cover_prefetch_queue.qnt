// Natural-language specifications:
// - docs/spec/07-library-reader.md
//
// Covers:
// - LIBRARY-COVER-PREFETCH-QUEUE-001@sha256:4f18cd7fde6f96c80884a661dd4c30e7bd96b3a1a951a64e439b91eb7a9c36ff -> claimPending
// - LIBRARY-COVER-PREFETCH-QUEUE-001@sha256:4f18cd7fde6f96c80884a661dd4c30e7bd96b3a1a951a64e439b91eb7a9c36ff -> recoverInterrupted
// - LIBRARY-COVER-PREFETCH-QUEUE-001@sha256:4f18cd7fde6f96c80884a661dd4c30e7bd96b3a1a951a64e439b91eb7a9c36ff -> retryFailed
// - LIBRARY-COVER-PREFETCH-QUEUE-001@sha256:4f18cd7fde6f96c80884a661dd4c30e7bd96b3a1a951a64e439b91eb7a9c36ff -> retrySkipped
// - LIBRARY-COVER-PREFETCH-QUEUE-001@sha256:4f18cd7fde6f96c80884a661dd4c30e7bd96b3a1a951a64e439b91eb7a9c36ff -> cacheEvictedAfterCompletion
// - LIBRARY-COVER-PREFETCH-QUEUE-001@sha256:4f18cd7fde6f96c80884a661dd4c30e7bd96b3a1a951a64e439b91eb7a9c36ff -> schedulerWaitKeepsPending
// - LIBRARY-COVER-PREFETCH-QUEUE-001@sha256:4f18cd7fde6f96c80884a661dd4c30e7bd96b3a1a951a64e439b91eb7a9c36ff -> waitReasonRequiresPending
//
// Abstraction:
// One Library-owned cover-prefetch queue item is modeled. Durable queue state
// and WorkManager/runtime wait observation are separate variables. Transfer
// byte progress and history trimming are intentionally outside this model.

module library_cover_prefetch_queue {
  val PENDING = 0
  val RUNNING = 1
  val FAILED = 2
  val COMPLETED = 3
  val SKIPPED = 4

  val NO_WAIT = 0
  val WIFI_WAIT = 1
  val BATTERY_WAIT = 2
  val SCHEDULER_WAIT = 3

  var status: int
  var waitReason: int

  action init = all {
    status' = PENDING,
    waitReason' = NO_WAIT,
  }

  action schedulerWaitKeepsPending = all {
    status == PENDING,
    status' = PENDING,
    waitReason' = SCHEDULER_WAIT,
  }

  action wifiWaitKeepsPending = all {
    status == PENDING,
    status' = PENDING,
    waitReason' = WIFI_WAIT,
  }

  action batteryWaitKeepsPending = all {
    status == PENDING,
    status' = PENDING,
    waitReason' = BATTERY_WAIT,
  }

  action clearWait = all {
    status == PENDING,
    status' = PENDING,
    waitReason' = NO_WAIT,
  }

  action claimPending = all {
    status == PENDING,
    status' = RUNNING,
    waitReason' = NO_WAIT,
  }

  action completeRunning = all {
    status == RUNNING,
    status' = COMPLETED,
    waitReason' = NO_WAIT,
  }

  action failRunning = all {
    status == RUNNING,
    status' = FAILED,
    waitReason' = NO_WAIT,
  }

  action skipRunning = all {
    status == RUNNING,
    status' = SKIPPED,
    waitReason' = NO_WAIT,
  }

  action recoverInterrupted = all {
    status == RUNNING,
    status' = PENDING,
    waitReason' = NO_WAIT,
  }

  action retryFailed = all {
    status == FAILED,
    status' = PENDING,
    waitReason' = NO_WAIT,
  }

  action retrySkipped = all {
    status == SKIPPED,
    status' = PENDING,
    waitReason' = NO_WAIT,
  }

  action cacheEvictedAfterCompletion = all {
    status == COMPLETED,
    status' = SKIPPED,
    waitReason' = NO_WAIT,
  }

  action idle = all {
    status' = status,
    waitReason' = waitReason,
  }

  action step = any {
    schedulerWaitKeepsPending,
    wifiWaitKeepsPending,
    batteryWaitKeepsPending,
    clearWait,
    claimPending,
    completeRunning,
    failRunning,
    skipRunning,
    recoverInterrupted,
    retryFailed,
    retrySkipped,
    cacheEvictedAfterCompletion,
    idle,
  }

  val knownStatus =
    status == PENDING or
    status == RUNNING or
    status == FAILED or
    status == COMPLETED or
    status == SKIPPED

  val knownWaitReason =
    waitReason == NO_WAIT or
    waitReason == WIFI_WAIT or
    waitReason == BATTERY_WAIT or
    waitReason == SCHEDULER_WAIT

  val waitReasonRequiresPending =
    waitReason == NO_WAIT or status == PENDING

  val runningIsNotWaiting =
    not(status == RUNNING) or waitReason == NO_WAIT

  val terminalStateIsNotWaiting =
    not(status == FAILED or status == COMPLETED or status == SKIPPED) or
    waitReason == NO_WAIT

  val safety =
    knownStatus and
    knownWaitReason and
    waitReasonRequiresPending and
    runningIsNotWaiting and
    terminalStateIsNotWaiting
}
