// Natural-language specifications:
// - docs/spec/07-library-reader.md
//
// Covers:
// - LIBRARY-METADATA-NORMALIZATION-LIFECYCLE-001@sha256:457d431a578517c42b4d70cb4f4f3f48b94e414652756535cf7b53e9d6d8847e -> coverBecomesReady
// - LIBRARY-METADATA-NORMALIZATION-LIFECYCLE-001@sha256:457d431a578517c42b4d70cb4f4f3f48b94e414652756535cf7b53e9d6d8847e -> claimQueued
// - LIBRARY-METADATA-NORMALIZATION-LIFECYCLE-001@sha256:457d431a578517c42b4d70cb4f4f3f48b94e414652756535cf7b53e9d6d8847e -> recoverInterrupted
// - LIBRARY-METADATA-NORMALIZATION-LIFECYCLE-001@sha256:457d431a578517c42b4d70cb4f4f3f48b94e414652756535cf7b53e9d6d8847e -> generationSucceeded
// - LIBRARY-METADATA-NORMALIZATION-LIFECYCLE-001@sha256:457d431a578517c42b4d70cb4f4f3f48b94e414652756535cf7b53e9d6d8847e -> reopenDeferred
// - LIBRARY-METADATA-NORMALIZATION-LIFECYCLE-001@sha256:457d431a578517c42b4d70cb4f4f3f48b94e414652756535cf7b53e9d6d8847e -> applyCurrentRevision
// - LIBRARY-METADATA-NORMALIZATION-LIFECYCLE-001@sha256:457d431a578517c42b4d70cb4f4f3f48b94e414652756535cf7b53e9d6d8847e -> refuseStaleApply
// - LIBRARY-METADATA-NORMALIZATION-LIFECYCLE-001@sha256:457d431a578517c42b4d70cb4f4f3f48b94e414652756535cf7b53e9d6d8847e -> retryWithCover
// - LIBRARY-METADATA-NORMALIZATION-LIFECYCLE-001@sha256:457d431a578517c42b4d70cb4f4f3f48b94e414652756535cf7b53e9d6d8847e -> retryWithoutCover
// - LIBRARY-METADATA-NORMALIZATION-LIFECYCLE-001@sha256:457d431a578517c42b4d70cb4f4f3f48b94e414652756535cf7b53e9d6d8847e -> editApplied
// - LIBRARY-METADATA-NORMALIZATION-LIFECYCLE-001@sha256:457d431a578517c42b4d70cb4f4f3f48b94e414652756535cf7b53e9d6d8847e -> appliedRequiresCurrentRevision
//
// Abstraction:
// One SMB metadata-normalization item is modeled. The model tracks lifecycle,
// cover readiness, and whether the current SMB input revision still matches the
// revision used to generate the candidate. Proposal content, filename
// sanitization, decision payloads, and batch aggregation remain implementation
// concerns covered by unit tests.

module library_metadata_normalization_lifecycle {
  val WAITING_FOR_COVER = 0
  val QUEUED = 1
  val PROCESSING = 2
  val PENDING_REVIEW = 3
  val DEFERRED = 4
  val APPLIED = 5
  val REJECTED = 6
  val FAILED = 7
  val SKIPPED = 8

  var status: int
  var coverReady: bool
  var revisionMatches: bool

  action init = all {
    status' = WAITING_FOR_COVER,
    coverReady' = false,
    revisionMatches' = true,
  }

  action coverBecomesReady = all {
    status == WAITING_FOR_COVER,
    status' = QUEUED,
    coverReady' = true,
    revisionMatches' = revisionMatches,
  }

  action coverPrefetchFails = all {
    status == WAITING_FOR_COVER,
    status' = FAILED,
    coverReady' = false,
    revisionMatches' = revisionMatches,
  }

  action coverPrefetchSkipped = all {
    status == WAITING_FOR_COVER,
    status' = SKIPPED,
    coverReady' = false,
    revisionMatches' = revisionMatches,
  }

  action claimQueued = all {
    status == QUEUED,
    status' = PROCESSING,
    coverReady' = coverReady,
    revisionMatches' = revisionMatches,
  }

  action recoverInterrupted = all {
    status == PROCESSING,
    status' = QUEUED,
    coverReady' = coverReady,
    revisionMatches' = revisionMatches,
  }

  action generationSucceeded = all {
    status == PROCESSING,
    status' = PENDING_REVIEW,
    coverReady' = coverReady,
    revisionMatches' = true,
  }

  action generationFailed = all {
    status == PROCESSING,
    status' = FAILED,
    coverReady' = coverReady,
    revisionMatches' = revisionMatches,
  }

  action generationSkipped = all {
    status == PROCESSING,
    status' = SKIPPED,
    coverReady' = coverReady,
    revisionMatches' = revisionMatches,
  }

  action deferReview = all {
    status == PENDING_REVIEW,
    status' = DEFERRED,
    coverReady' = coverReady,
    revisionMatches' = revisionMatches,
  }

  action reopenDeferred = all {
    status == DEFERRED,
    status' = PENDING_REVIEW,
    coverReady' = coverReady,
    revisionMatches' = revisionMatches,
  }

  action rejectReview = all {
    (status == PENDING_REVIEW or status == DEFERRED or status == FAILED or status == SKIPPED),
    status' = REJECTED,
    coverReady' = coverReady,
    revisionMatches' = revisionMatches,
  }

  action revisionChangesBeforeApply = all {
    (status == PENDING_REVIEW or status == DEFERRED),
    status' = status,
    coverReady' = coverReady,
    revisionMatches' = false,
  }

  action applyCurrentRevision = all {
    (status == PENDING_REVIEW or status == DEFERRED),
    revisionMatches,
    status' = APPLIED,
    coverReady' = coverReady,
    revisionMatches' = true,
  }

  action refuseStaleApply = all {
    (status == PENDING_REVIEW or status == DEFERRED),
    not(revisionMatches),
    status' = SKIPPED,
    coverReady' = coverReady,
    revisionMatches' = false,
  }

  action retryWithCover = all {
    (
      status == PENDING_REVIEW or
      status == DEFERRED or
      status == REJECTED or
      status == FAILED or
      status == SKIPPED
    ),
    status' = QUEUED,
    coverReady' = true,
    revisionMatches' = true,
  }

  action retryWithoutCover = all {
    (
      status == PENDING_REVIEW or
      status == DEFERRED or
      status == REJECTED or
      status == FAILED or
      status == SKIPPED
    ),
    status' = WAITING_FOR_COVER,
    coverReady' = false,
    revisionMatches' = true,
  }

  action editApplied = all {
    status == APPLIED,
    status' = APPLIED,
    coverReady' = coverReady,
    revisionMatches' = revisionMatches,
  }

  action idle = all {
    status' = status,
    coverReady' = coverReady,
    revisionMatches' = revisionMatches,
  }

  action step = any {
    coverBecomesReady,
    coverPrefetchFails,
    coverPrefetchSkipped,
    claimQueued,
    recoverInterrupted,
    generationSucceeded,
    generationFailed,
    generationSkipped,
    deferReview,
    reopenDeferred,
    rejectReview,
    revisionChangesBeforeApply,
    applyCurrentRevision,
    refuseStaleApply,
    retryWithCover,
    retryWithoutCover,
    editApplied,
    idle,
  }

  val knownStatus =
    status == WAITING_FOR_COVER or
    status == QUEUED or
    status == PROCESSING or
    status == PENDING_REVIEW or
    status == DEFERRED or
    status == APPLIED or
    status == REJECTED or
    status == FAILED or
    status == SKIPPED

  val queuedOrProcessingRequiresCover =
    not(status == QUEUED or status == PROCESSING) or coverReady

  val waitingRequiresMissingCover =
    not(status == WAITING_FOR_COVER) or not(coverReady)

  val appliedRequiresCurrentRevision =
    not(status == APPLIED) or revisionMatches

  val safety =
    knownStatus and
    queuedOrProcessingRequiresCover and
    waitingRequiresMissingCover and
    appliedRequiresCurrentRevision
}
