// Natural-language specifications:
// - docs/spec/06-mail.md
//
// Covers:
// - MAIL-INITIAL-SYNC-CHECKPOINT-001@sha256:af575f939b2d25d68ba2aa9f7035802b1e8306cabe4fcb9c9c93afcff2e985b7 -> startInitialSync
// - MAIL-INITIAL-SYNC-CHECKPOINT-001@sha256:af575f939b2d25d68ba2aa9f7035802b1e8306cabe4fcb9c9c93afcff2e985b7 -> persistNextCheckpoint
// - MAIL-INITIAL-SYNC-CHECKPOINT-001@sha256:af575f939b2d25d68ba2aa9f7035802b1e8306cabe4fcb9c9c93afcff2e985b7 -> reconcileFromDurableCheckpoint
// - MAIL-INITIAL-SYNC-CHECKPOINT-001@sha256:af575f939b2d25d68ba2aa9f7035802b1e8306cabe4fcb9c9c93afcff2e985b7 -> transientFailure
// - MAIL-INITIAL-SYNC-CHECKPOINT-001@sha256:af575f939b2d25d68ba2aa9f7035802b1e8306cabe4fcb9c9c93afcff2e985b7 -> retryCurrentCheckpoint
// - MAIL-INITIAL-SYNC-CHECKPOINT-001@sha256:af575f939b2d25d68ba2aa9f7035802b1e8306cabe4fcb9c9c93afcff2e985b7 -> terminalError
// - MAIL-INITIAL-SYNC-CHECKPOINT-001@sha256:af575f939b2d25d68ba2aa9f7035802b1e8306cabe4fcb9c9c93afcff2e985b7 -> resumeInitialSync
// - MAIL-INITIAL-SYNC-CHECKPOINT-001@sha256:af575f939b2d25d68ba2aa9f7035802b1e8306cabe4fcb9c9c93afcff2e985b7 -> completeInitialSync
// - MAIL-INITIAL-SYNC-CHECKPOINT-001@sha256:af575f939b2d25d68ba2aa9f7035802b1e8306cabe4fcb9c9c93afcff2e985b7 -> staleWorkerRepresentsAlreadyProcessedPage
// - MAIL-INITIAL-SYNC-CHECKPOINT-001@sha256:af575f939b2d25d68ba2aa9f7035802b1e8306cabe4fcb9c9c93afcff2e985b7 -> pageProgressIsSequential
// - MAIL-INITIAL-SYNC-CHECKPOINT-001@sha256:af575f939b2d25d68ba2aa9f7035802b1e8306cabe4fcb9c9c93afcff2e985b7 -> idleClearsInitialSyncCheckpoint
//
// Abstraction:
// One Mail account initial synchronization is modeled with three finite pages.
// PAGE_NONE represents absence of an initial-sync checkpoint. The scheduled page
// is bounded worker metadata, while durablePage is the source of truth. After a
// page is committed, scheduledPage may temporarily reference the older page
// until normal continuation enqueue or stale-worker reconciliation observes the
// new durable checkpoint. Message contents, remote API semantics, exact backoff,
// and credential material are intentionally outside this model.

module mail_initial_sync_checkpoint {
  val STATE_NONE = 0
  val STATE_SYNCING = 1
  val STATE_WAITING = 2
  val STATE_ERROR = 3
  val STATE_IDLE = 4

  val PAGE_FIRST = 0
  val PAGE_SECOND = 1
  val PAGE_FINAL = 2
  val PAGE_NONE = 3

  var syncState: int
  var durablePage: int
  var scheduledPage: int
  var lastProcessedPage: int
  var generationPresent: bool
  var startHistoryPresent: bool
  var completedHistoryPresent: bool

  action init = all {
    syncState' = STATE_NONE,
    durablePage' = PAGE_NONE,
    scheduledPage' = PAGE_NONE,
    lastProcessedPage' = -1,
    generationPresent' = false,
    startHistoryPresent' = false,
    completedHistoryPresent' = false,
  }

  action startInitialSync = all {
    syncState == STATE_NONE or syncState == STATE_IDLE,
    syncState' = STATE_SYNCING,
    durablePage' = PAGE_FIRST,
    scheduledPage' = PAGE_FIRST,
    lastProcessedPage' = -1,
    generationPresent' = true,
    startHistoryPresent' = false,
    completedHistoryPresent' = false,
  }

  action captureStartHistory = all {
    syncState == STATE_SYNCING,
    scheduledPage == durablePage,
    not(startHistoryPresent),
    syncState' = STATE_SYNCING,
    durablePage' = durablePage,
    scheduledPage' = scheduledPage,
    lastProcessedPage' = lastProcessedPage,
    generationPresent' = true,
    startHistoryPresent' = true,
    completedHistoryPresent' = false,
  }

  action persistNextCheckpoint = all {
    syncState == STATE_SYNCING,
    scheduledPage == durablePage,
    startHistoryPresent,
    durablePage == PAGE_FIRST or durablePage == PAGE_SECOND,
    syncState' = STATE_SYNCING,
    durablePage' = durablePage + 1,
    scheduledPage' = scheduledPage,
    lastProcessedPage' = durablePage,
    generationPresent' = true,
    startHistoryPresent' = true,
    completedHistoryPresent' = false,
  }

  action reconcileFromDurableCheckpoint = all {
    generationPresent,
    durablePage != PAGE_NONE,
    scheduledPage != durablePage,
    syncState' = STATE_SYNCING,
    durablePage' = durablePage,
    scheduledPage' = durablePage,
    lastProcessedPage' = lastProcessedPage,
    generationPresent' = true,
    startHistoryPresent' = startHistoryPresent,
    completedHistoryPresent' = false,
  }

  action transientFailure = all {
    syncState == STATE_SYNCING,
    scheduledPage == durablePage,
    syncState' = STATE_WAITING,
    durablePage' = durablePage,
    scheduledPage' = scheduledPage,
    lastProcessedPage' = lastProcessedPage,
    generationPresent' = generationPresent,
    startHistoryPresent' = startHistoryPresent,
    completedHistoryPresent' = false,
  }

  action retryCurrentCheckpoint = all {
    syncState == STATE_WAITING,
    durablePage != PAGE_NONE,
    generationPresent,
    syncState' = STATE_SYNCING,
    durablePage' = durablePage,
    scheduledPage' = durablePage,
    lastProcessedPage' = lastProcessedPage,
    generationPresent' = true,
    startHistoryPresent' = startHistoryPresent,
    completedHistoryPresent' = false,
  }

  action terminalError = all {
    syncState == STATE_SYNCING or syncState == STATE_WAITING,
    durablePage != PAGE_NONE,
    generationPresent,
    syncState' = STATE_ERROR,
    durablePage' = durablePage,
    scheduledPage' = scheduledPage,
    lastProcessedPage' = lastProcessedPage,
    generationPresent' = true,
    startHistoryPresent' = startHistoryPresent,
    completedHistoryPresent' = false,
  }

  action resumeInitialSync = all {
    syncState == STATE_ERROR,
    durablePage != PAGE_NONE,
    generationPresent,
    syncState' = STATE_SYNCING,
    durablePage' = durablePage,
    scheduledPage' = durablePage,
    lastProcessedPage' = lastProcessedPage,
    generationPresent' = true,
    startHistoryPresent' = startHistoryPresent,
    completedHistoryPresent' = false,
  }

  action completeInitialSync = all {
    syncState == STATE_SYNCING,
    durablePage == PAGE_FINAL,
    scheduledPage == PAGE_FINAL,
    lastProcessedPage == PAGE_SECOND,
    generationPresent,
    startHistoryPresent,
    syncState' = STATE_IDLE,
    durablePage' = PAGE_NONE,
    scheduledPage' = PAGE_NONE,
    lastProcessedPage' = PAGE_FINAL,
    generationPresent' = false,
    startHistoryPresent' = false,
    completedHistoryPresent' = true,
  }

  action idle = all {
    syncState' = syncState,
    durablePage' = durablePage,
    scheduledPage' = scheduledPage,
    lastProcessedPage' = lastProcessedPage,
    generationPresent' = generationPresent,
    startHistoryPresent' = startHistoryPresent,
    completedHistoryPresent' = completedHistoryPresent,
  }

  action step = any {
    startInitialSync,
    captureStartHistory,
    persistNextCheckpoint,
    reconcileFromDurableCheckpoint,
    transientFailure,
    retryCurrentCheckpoint,
    terminalError,
    resumeInitialSync,
    completeInitialSync,
    idle,
  }

  val knownState =
    syncState == STATE_NONE or
    syncState == STATE_SYNCING or
    syncState == STATE_WAITING or
    syncState == STATE_ERROR or
    syncState == STATE_IDLE

  val knownPage =
    durablePage == PAGE_FIRST or
    durablePage == PAGE_SECOND or
    durablePage == PAGE_FINAL or
    durablePage == PAGE_NONE

  val knownScheduledPage =
    scheduledPage == PAGE_FIRST or
    scheduledPage == PAGE_SECOND or
    scheduledPage == PAGE_FINAL or
    scheduledPage == PAGE_NONE

  val knownLastProcessedPage =
    lastProcessedPage == -1 or
    lastProcessedPage == PAGE_FIRST or
    lastProcessedPage == PAGE_SECOND or
    lastProcessedPage == PAGE_FINAL

  val activeInitialSyncHasGeneration =
    not(
      syncState == STATE_SYNCING or
      syncState == STATE_WAITING or
      syncState == STATE_ERROR
    ) or generationPresent

  val pageProgressIsSequential =
    durablePage == PAGE_NONE or durablePage == lastProcessedPage + 1

  val staleWorkerRepresentsAlreadyProcessedPage =
    not(
      scheduledPage != PAGE_NONE and
      durablePage != PAGE_NONE and
      scheduledPage != durablePage
    ) or scheduledPage == lastProcessedPage

  val startHistoryOnlyDuringGeneration =
    not(startHistoryPresent) or generationPresent

  val completedHistoryOnlyAfterInitialSync =
    not(completedHistoryPresent) or syncState == STATE_IDLE

  val idleClearsInitialSyncCheckpoint =
    not(syncState == STATE_IDLE) or (
      durablePage == PAGE_NONE and
      scheduledPage == PAGE_NONE and
      not(generationPresent) and
      not(startHistoryPresent) and
      completedHistoryPresent
    )

  val safety =
    knownState and
    knownPage and
    knownScheduledPage and
    knownLastProcessedPage and
    activeInitialSyncHasGeneration and
    pageProgressIsSequential and
    staleWorkerRepresentsAlreadyProcessedPage and
    startHistoryOnlyDuringGeneration and
    completedHistoryOnlyAfterInitialSync and
    idleClearsInitialSyncCheckpoint
}
