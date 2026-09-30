// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - PODCAST-EPISODE-LIFECYCLE-001@sha256:57ee1a032fb24c54d02071c91f1cdf8f9cc13e76229ecb40597eb9c81cac452c -> archiveEpisode
// - PODCAST-EPISODE-LIFECYCLE-001@sha256:57ee1a032fb24c54d02071c91f1cdf8f9cc13e76229ecb40597eb9c81cac452c -> restoreEpisode
// - PODCAST-EPISODE-LIFECYCLE-001@sha256:57ee1a032fb24c54d02071c91f1cdf8f9cc13e76229ecb40597eb9c81cac452c -> deleteEpisode
// - PODCAST-EPISODE-LIFECYCLE-001@sha256:57ee1a032fb24c54d02071c91f1cdf8f9cc13e76229ecb40597eb9c81cac452c -> deletedEpisodeKeepsConsumption
//
// Abstraction:
// The episode is modeled after reservation, so its article snapshot and
// consumed-entry identity already exist. Chapter-level retry/checkpoint
// behavior is deliberately outside this model; this model focuses on the
// episode organization lifecycle and irreversible deletion semantics.

module podcast_episode_lifecycle {
  val QUEUED = 0
  val GENERATING = 1
  val READY = 2
  val FAILED = 3
  val ARCHIVED = 4
  val DELETED = 5

  var status: int
  var snapshotPresent: bool
  var consumedIdentityPresent: bool

  action init = all {
    status' = QUEUED,
    snapshotPresent' = true,
    consumedIdentityPresent' = true,
  }

  action startGeneration = all {
    status == QUEUED,
    status' = GENERATING,
    snapshotPresent' = snapshotPresent,
    consumedIdentityPresent' = consumedIdentityPresent,
  }

  action completeGeneration = all {
    status == GENERATING,
    status' = READY,
    snapshotPresent' = snapshotPresent,
    consumedIdentityPresent' = consumedIdentityPresent,
  }

  action failGeneration = all {
    status == GENERATING,
    status' = FAILED,
    snapshotPresent' = snapshotPresent,
    consumedIdentityPresent' = consumedIdentityPresent,
  }

  action retryFailure = all {
    status == FAILED,
    status' = GENERATING,
    snapshotPresent' = snapshotPresent,
    consumedIdentityPresent' = consumedIdentityPresent,
  }

  action regenerateEpisode = all {
    (status == READY or status == FAILED),
    status' = GENERATING,
    snapshotPresent' = snapshotPresent,
    consumedIdentityPresent' = consumedIdentityPresent,
  }

  action archiveEpisode = all {
    status == READY,
    status' = ARCHIVED,
    snapshotPresent' = snapshotPresent,
    consumedIdentityPresent' = consumedIdentityPresent,
  }

  action restoreEpisode = all {
    status == ARCHIVED,
    status' = READY,
    snapshotPresent' = snapshotPresent,
    consumedIdentityPresent' = consumedIdentityPresent,
  }

  action deleteEpisode = all {
    (status == READY or status == ARCHIVED or status == FAILED),
    status' = DELETED,
    snapshotPresent' = false,
    consumedIdentityPresent' = true,
  }

  action remainDeleted = all {
    status == DELETED,
    status' = DELETED,
    snapshotPresent' = false,
    consumedIdentityPresent' = consumedIdentityPresent,
  }

  action step = any {
    startGeneration,
    completeGeneration,
    failGeneration,
    retryFailure,
    regenerateEpisode,
    archiveEpisode,
    restoreEpisode,
    deleteEpisode,
    remainDeleted,
  }

  val knownStatus =
    status == QUEUED or status == GENERATING or status == READY or
    status == FAILED or status == ARCHIVED or status == DELETED

  val deletedEpisodeKeepsConsumption =
    not(status == DELETED) or
    (not(snapshotPresent) and consumedIdentityPresent)

  val nonDeletedEpisodeKeepsSnapshot =
    status == DELETED or snapshotPresent

  val consumedIdentityNeverReturnsToUnused = consumedIdentityPresent

  val safety =
    knownStatus and
    deletedEpisodeKeepsConsumption and
    nonDeletedEpisodeKeepsSnapshot and
    consumedIdentityNeverReturnsToUnused
}
