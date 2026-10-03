// Natural-language specifications:
// - docs/spec/06-mail.md
//
// Covers:
// - MAIL-INITIAL-SYNC-001@sha256:49e25050adb79f82fb88a588b757cc80e09cd97e64f845f3897fe2cdad1632d0 -> startInitialSync
// - MAIL-INITIAL-SYNC-001@sha256:49e25050adb79f82fb88a588b757cc80e09cd97e64f845f3897fe2cdad1632d0 -> persistNextCheckpoint
// - MAIL-INITIAL-SYNC-001@sha256:49e25050adb79f82fb88a588b757cc80e09cd97e64f845f3897fe2cdad1632d0 -> reconcileStaleWorker
// - MAIL-INITIAL-SYNC-001@sha256:49e25050adb79f82fb88a588b757cc80e09cd97e64f845f3897fe2cdad1632d0 -> retryableFailure
// - MAIL-INITIAL-SYNC-001@sha256:49e25050adb79f82fb88a588b757cc80e09cd97e64f845f3897fe2cdad1632d0 -> resumeRetryableWait
// - MAIL-INITIAL-SYNC-001@sha256:49e25050adb79f82fb88a588b757cc80e09cd97e64f845f3897fe2cdad1632d0 -> terminalFailure
// - MAIL-INITIAL-SYNC-001@sha256:49e25050adb79f82fb88a588b757cc80e09cd97e64f845f3897fe2cdad1632d0 -> retryAfterTerminalFailure
// - MAIL-INITIAL-SYNC-001@sha256:49e25050adb79f82fb88a588b757cc80e09cd97e64f845f3897fe2cdad1632d0 -> completeFinalPage
// - MAIL-INITIAL-SYNC-001@sha256:49e25050adb79f82fb88a588b757cc80e09cd97e64f845f3897fe2cdad1632d0 -> staleWindowIsBounded
//
// Abstraction:
// One account and a two-page initial synchronization are modeled. FIRST and
// NEXT represent arbitrary consecutive page checkpoints. The transient state
// where the durable checkpoint has advanced but the continuation work has not
// yet been enqueued is kept explicitly because process interruption can occur
// there. Thread contents, API payloads, and concrete backoff timing remain
// implementation concerns.

module mail_initial_sync_lifecycle {
  val IDLE = 0
  val SYNCING = 1
  val WAITING_FOR_NETWORK = 2
  val ERROR = 3

  val NO_PAGE = -1
  val FIRST = 0
  val NEXT = 1

  var state: int
  var checkpoint: int
  var workerExpected: int
  var generationActive: bool
  var historyCommitted: bool

  action init = all {
    state' = IDLE,
    checkpoint' = NO_PAGE,
    workerExpected' = NO_PAGE,
    generationActive' = false,
    historyCommitted' = true,
  }

  action startInitialSync = all {
    state == IDLE,
    state' = SYNCING,
    checkpoint' = FIRST,
    workerExpected' = FIRST,
    generationActive' = true,
    historyCommitted' = false,
  }

  action persistNextCheckpoint = all {
    state == SYNCING,
    checkpoint == FIRST,
    workerExpected == FIRST,
    state' = SYNCING,
    checkpoint' = NEXT,
    workerExpected' = FIRST,
    generationActive' = true,
    historyCommitted' = false,
  }

  action reconcileStaleWorker = all {
    state == SYNCING,
    checkpoint == NEXT,
    workerExpected == FIRST,
    state' = SYNCING,
    checkpoint' = NEXT,
    workerExpected' = NEXT,
    generationActive' = true,
    historyCommitted' = false,
  }

  action retryableFailure = all {
    state == SYNCING,
    workerExpected == checkpoint,
    checkpoint != NO_PAGE,
    state' = WAITING_FOR_NETWORK,
    checkpoint' = checkpoint,
    workerExpected' = workerExpected,
    generationActive' = true,
    historyCommitted' = false,
  }

  action resumeRetryableWait = all {
    state == WAITING_FOR_NETWORK,
    workerExpected == checkpoint,
    checkpoint != NO_PAGE,
    state' = SYNCING,
    checkpoint' = checkpoint,
    workerExpected' = workerExpected,
    generationActive' = true,
    historyCommitted' = false,
  }

  action terminalFailure = all {
    state == SYNCING or state == WAITING_FOR_NETWORK,
    workerExpected == checkpoint,
    checkpoint != NO_PAGE,
    state' = ERROR,
    checkpoint' = checkpoint,
    workerExpected' = NO_PAGE,
    generationActive' = true,
    historyCommitted' = false,
  }

  action retryAfterTerminalFailure = all {
    state == ERROR,
    generationActive,
    checkpoint != NO_PAGE,
    state' = SYNCING,
    checkpoint' = checkpoint,
    workerExpected' = checkpoint,
    generationActive' = true,
    historyCommitted' = false,
  }

  action completeFinalPage = all {
    state == SYNCING,
    checkpoint == NEXT,
    workerExpected == NEXT,
    state' = IDLE,
    checkpoint' = NO_PAGE,
    workerExpected' = NO_PAGE,
    generationActive' = false,
    historyCommitted' = true,
  }

  action idle = all {
    state' = state,
    checkpoint' = checkpoint,
    workerExpected' = workerExpected,
    generationActive' = generationActive,
    historyCommitted' = historyCommitted,
  }

  action step = any {
    startInitialSync,
    persistNextCheckpoint,
    reconcileStaleWorker,
    retryableFailure,
    resumeRetryableWait,
    terminalFailure,
    retryAfterTerminalFailure,
    completeFinalPage,
    idle,
  }

  val knownState =
    state == IDLE or
    state == SYNCING or
    state == WAITING_FOR_NETWORK or
    state == ERROR

  val knownCheckpoint =
    checkpoint == NO_PAGE or checkpoint == FIRST or checkpoint == NEXT

  val knownWorkerExpected =
    workerExpected == NO_PAGE or workerExpected == FIRST or workerExpected == NEXT

  val idleHasNoActiveCheckpoint =
    not(state == IDLE) or
    (
      checkpoint == NO_PAGE and
      workerExpected == NO_PAGE and
      not(generationActive) and
      historyCommitted
    )

  val activeSyncHasGeneration =
    state == IDLE or generationActive

  val retryableWaitKeepsCurrentCheckpoint =
    not(state == WAITING_FOR_NETWORK) or
    (
      checkpoint != NO_PAGE and
      workerExpected == checkpoint and
      not(historyCommitted)
    )

  val terminalErrorKeepsDurableCheckpoint =
    not(state == ERROR) or
    (
      checkpoint != NO_PAGE and
      workerExpected == NO_PAGE and
      generationActive and
      not(historyCommitted)
    )

  val staleWindowIsBounded =
    not(state == SYNCING and workerExpected != checkpoint) or
    (checkpoint == NEXT and workerExpected == FIRST)

  val uncommittedHistoryWhileActive =
    state == IDLE or not(historyCommitted)

  val safety =
    knownState and
    knownCheckpoint and
    knownWorkerExpected and
    idleHasNoActiveCheckpoint and
    activeSyncHasGeneration and
    retryableWaitKeepsCurrentCheckpoint and
    terminalErrorKeepsDurableCheckpoint and
    staleWindowIsBounded and
    uncommittedHistoryWhileActive
}
