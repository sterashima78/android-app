// Natural-language specifications:
// - docs/spec/05-on-device-ai.md
//
// Covers:
// - LOCAL-AI-ARTIFACT-LIFECYCLE-001@sha256:cf74929494df8a342febc35761376e691e2a6a077bef3ce911f25c4bb9f946a9 -> startDownload
// - LOCAL-AI-ARTIFACT-LIFECYCLE-001@sha256:cf74929494df8a342febc35761376e691e2a6a077bef3ce911f25c4bb9f946a9 -> verifyTemporaryArtifact
// - LOCAL-AI-ARTIFACT-LIFECYCLE-001@sha256:cf74929494df8a342febc35761376e691e2a6a077bef3ce911f25c4bb9f946a9 -> promoteVerifiedArtifact
// - LOCAL-AI-ARTIFACT-LIFECYCLE-001@sha256:cf74929494df8a342febc35761376e691e2a6a077bef3ce911f25c4bb9f946a9 -> recordCurrentRevision
// - LOCAL-AI-ARTIFACT-LIFECYCLE-001@sha256:cf74929494df8a342febc35761376e691e2a6a077bef3ce911f25c4bb9f946a9 -> downloadFailure
// - LOCAL-AI-ARTIFACT-LIFECYCLE-001@sha256:cf74929494df8a342febc35761376e691e2a6a077bef3ce911f25c4bb9f946a9 -> selectValidArtifact
// - LOCAL-AI-ARTIFACT-LIFECYCLE-001@sha256:cf74929494df8a342febc35761376e691e2a6a077bef3ce911f25c4bb9f946a9 -> catalogRevisionChanges
// - LOCAL-AI-ARTIFACT-LIFECYCLE-001@sha256:cf74929494df8a342febc35761376e691e2a6a077bef3ce911f25c4bb9f946a9 -> cleanupOutdatedArtifact
// - LOCAL-AI-ARTIFACT-LIFECYCLE-001@sha256:cf74929494df8a342febc35761376e691e2a6a077bef3ce911f25c4bb9f946a9 -> deleteModel
// - LOCAL-AI-ARTIFACT-LIFECYCLE-001@sha256:cf74929494df8a342febc35761376e691e2a6a077bef3ce911f25c4bb9f946a9 -> effectiveSelectionRequiresValidArtifact
// - LOCAL-AI-ARTIFACT-LIFECYCLE-001@sha256:cf74929494df8a342febc35761376e691e2a6a077bef3ce911f25c4bb9f946a9 -> partialArtifactNeverEffective
//
// Abstraction:
// One local model artifact is modeled. ABSENT also represents a physically
// present but invalid partial destination because such a file is not an
// effective downloaded artifact. VALID means both the catalog artifact check
// and the current revision marker have succeeded. Concrete bytes, storage
// capacity, network redirects, and concurrent UI operations are outside scope.

module local_ai_artifact_lifecycle {
  val ABSENT = 0
  val DOWNLOADING = 1
  val VERIFIED_TEMP = 2
  val INSTALLED_UNMARKED = 3
  val VALID = 4
  val OUTDATED = 5

  var artifactState: int
  var selected: bool
  var resourcesRetained: bool
  var derivedCachePresent: bool

  action init = all {
    artifactState' = ABSENT,
    selected' = false,
    resourcesRetained' = false,
    derivedCachePresent' = false,
  }

  action startDownload = all {
    artifactState == ABSENT,
    artifactState' = DOWNLOADING,
    selected' = false,
    resourcesRetained' = false,
    derivedCachePresent' = derivedCachePresent,
  }

  action verifyTemporaryArtifact = all {
    artifactState == DOWNLOADING,
    artifactState' = VERIFIED_TEMP,
    selected' = false,
    resourcesRetained' = false,
    derivedCachePresent' = derivedCachePresent,
  }

  action promoteVerifiedArtifact = all {
    artifactState == VERIFIED_TEMP,
    artifactState' = INSTALLED_UNMARKED,
    selected' = false,
    resourcesRetained' = false,
    derivedCachePresent' = derivedCachePresent,
  }

  action recordCurrentRevision = all {
    artifactState == INSTALLED_UNMARKED,
    artifactState' = VALID,
    selected' = false,
    resourcesRetained' = false,
    derivedCachePresent' = derivedCachePresent,
  }

  action downloadFailure = all {
    artifactState == DOWNLOADING or
    artifactState == VERIFIED_TEMP or
    artifactState == INSTALLED_UNMARKED,
    artifactState' = ABSENT,
    selected' = false,
    resourcesRetained' = false,
    derivedCachePresent' = derivedCachePresent,
  }

  action selectValidArtifact = all {
    artifactState == VALID,
    artifactState' = VALID,
    selected' = true,
    resourcesRetained' = resourcesRetained,
    derivedCachePresent' = derivedCachePresent,
  }

  action deselectValidArtifact = all {
    artifactState == VALID,
    selected,
    artifactState' = VALID,
    selected' = false,
    resourcesRetained' = resourcesRetained,
    derivedCachePresent' = derivedCachePresent,
  }

  action openModelResources = all {
    artifactState == VALID,
    artifactState' = VALID,
    selected' = selected,
    resourcesRetained' = true,
    derivedCachePresent' = true,
  }

  action catalogRevisionChanges = all {
    artifactState == VALID,
    artifactState' = OUTDATED,
    selected' = false,
    resourcesRetained' = false,
    derivedCachePresent' = derivedCachePresent,
  }

  action cleanupOutdatedArtifact = all {
    artifactState == OUTDATED,
    artifactState' = ABSENT,
    selected' = false,
    resourcesRetained' = false,
    derivedCachePresent' = false,
  }

  action deleteModel = all {
    artifactState != DOWNLOADING,
    artifactState' = ABSENT,
    selected' = false,
    resourcesRetained' = false,
    derivedCachePresent' = false,
  }

  action idle = all {
    artifactState' = artifactState,
    selected' = selected,
    resourcesRetained' = resourcesRetained,
    derivedCachePresent' = derivedCachePresent,
  }

  action step = any {
    startDownload,
    verifyTemporaryArtifact,
    promoteVerifiedArtifact,
    recordCurrentRevision,
    downloadFailure,
    selectValidArtifact,
    deselectValidArtifact,
    openModelResources,
    catalogRevisionChanges,
    cleanupOutdatedArtifact,
    deleteModel,
    idle,
  }

  val knownArtifactState =
    artifactState == ABSENT or
    artifactState == DOWNLOADING or
    artifactState == VERIFIED_TEMP or
    artifactState == INSTALLED_UNMARKED or
    artifactState == VALID or
    artifactState == OUTDATED

  val effectiveSelectionRequiresValidArtifact =
    not(selected) or artifactState == VALID

  val partialArtifactNeverEffective =
    not(
      artifactState == DOWNLOADING or
      artifactState == VERIFIED_TEMP or
      artifactState == INSTALLED_UNMARKED
    ) or not(selected)

  val retainedResourcesRequireValidArtifact =
    not(resourcesRetained) or artifactState == VALID

  val absentArtifactIsNotEffective =
    not(artifactState == ABSENT) or (
      not(selected) and
      not(resourcesRetained)
    )

  val safety =
    knownArtifactState and
    effectiveSelectionRequiresValidArtifact and
    partialArtifactNeverEffective and
    retainedResourcesRequireValidArtifact and
    absentArtifactIsNotEffective
}
