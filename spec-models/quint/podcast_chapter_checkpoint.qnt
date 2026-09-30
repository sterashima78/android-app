// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - PODCAST-CHAPTER-CHECKPOINT-001@sha256:8f97947f45437d41875bd01c9787eef1b730964636129648f9e2375d5c0eaeb6 -> resumeUnfinishedCheckpoint
// - PODCAST-CHAPTER-CHECKPOINT-001@sha256:8f97947f45437d41875bd01c9787eef1b730964636129648f9e2375d5c0eaeb6 -> preserveReadyCheckpoint
// - PODCAST-CHAPTER-RETRY-001@sha256:7e75aaa3908d5a2e995b245e0456cfe7a61456ec718f15fb403a22fe2b655536 -> retryableFailure
// - PODCAST-CHAPTER-RETRY-001@sha256:7e75aaa3908d5a2e995b245e0456cfe7a61456ec718f15fb403a22fe2b655536 -> retryableFailureExhausted
// - PODCAST-CHAPTER-RETRY-001@sha256:7e75aaa3908d5a2e995b245e0456cfe7a61456ec718f15fb403a22fe2b655536 -> retryFailureIsBounded
//
// Abstraction:
// The model follows one chapter checkpoint. A READY checkpoint has no
// transition back into generation and is therefore reused during recovery.
// Cloud retry count represents retries after the initial attempt; three retries
// are allowed before a retryable failure becomes terminal.

module podcast_chapter_checkpoint {
  val PENDING = 0
  val GENERATING = 1
  val READY = 2
  val FAILED = 3
  val MAX_RETRIES = 3

  var state: int
  var retries: int
  var nonRetryableFailureObserved: bool

  action init = all {
    state' = PENDING,
    retries' = 0,
    nonRetryableFailureObserved' = false,
  }

  action startGeneration = all {
    state == PENDING,
    state' = GENERATING,
    retries' = 0,
    nonRetryableFailureObserved' = false,
  }

  action resumeUnfinishedCheckpoint = all {
    (state == PENDING or state == GENERATING or state == FAILED),
    state' = GENERATING,
    retries' = 0,
    nonRetryableFailureObserved' = false,
  }

  action preserveReadyCheckpoint = all {
    state == READY,
    state' = READY,
    retries' = retries,
    nonRetryableFailureObserved' = nonRetryableFailureObserved,
  }

  action retryableFailure = all {
    state == GENERATING,
    retries < MAX_RETRIES,
    state' = GENERATING,
    retries' = retries + 1,
    nonRetryableFailureObserved' = false,
  }

  action retryableFailureExhausted = all {
    state == GENERATING,
    retries == MAX_RETRIES,
    state' = FAILED,
    retries' = retries,
    nonRetryableFailureObserved' = false,
  }

  action nonRetryableFailure = all {
    state == GENERATING,
    state' = FAILED,
    retries' = retries,
    nonRetryableFailureObserved' = true,
  }

  action completeChapter = all {
    state == GENERATING,
    state' = READY,
    retries' = retries,
    nonRetryableFailureObserved' = nonRetryableFailureObserved,
  }

  action idle = all {
    state' = state,
    retries' = retries,
    nonRetryableFailureObserved' = nonRetryableFailureObserved,
  }

  action step = any {
    startGeneration,
    resumeUnfinishedCheckpoint,
    preserveReadyCheckpoint,
    retryableFailure,
    retryableFailureExhausted,
    nonRetryableFailure,
    completeChapter,
    idle,
  }

  val knownState =
    state == PENDING or state == GENERATING or state == READY or state == FAILED

  val retryCountIsBounded =
    retries >= 0 and retries <= MAX_RETRIES

  val retryFailureIsBounded =
    not(state == FAILED) or
    nonRetryableFailureObserved or retries == MAX_RETRIES

  val safety =
    knownState and
    retryCountIsBounded and
    retryFailureIsBounded
}
