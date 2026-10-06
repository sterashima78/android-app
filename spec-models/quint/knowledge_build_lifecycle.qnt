// Natural-language specifications:
// - docs/spec/05-on-device-ai.md
//
// Covers:
// - KNOWLEDGE-BUILD-LIFECYCLE-001@sha256:14dc0303d926b0db74a87cdad73b5e0b07d1c5775d3907b8a00663cfeeb1f62c -> requestBuild
// - KNOWLEDGE-BUILD-LIFECYCLE-001@sha256:14dc0303d926b0db74a87cdad73b5e0b07d1c5775d3907b8a00663cfeeb1f62c -> planTopics
// - KNOWLEDGE-BUILD-LIFECYCLE-001@sha256:14dc0303d926b0db74a87cdad73b5e0b07d1c5775d3907b8a00663cfeeb1f62c -> completeWithoutTopics
// - KNOWLEDGE-BUILD-LIFECYCLE-001@sha256:14dc0303d926b0db74a87cdad73b5e0b07d1c5775d3907b8a00663cfeeb1f62c -> completeLastTopic
// - KNOWLEDGE-BUILD-LIFECYCLE-001@sha256:14dc0303d926b0db74a87cdad73b5e0b07d1c5775d3907b8a00663cfeeb1f62c -> pauseGlobal
// - KNOWLEDGE-BUILD-LIFECYCLE-001@sha256:14dc0303d926b0db74a87cdad73b5e0b07d1c5775d3907b8a00663cfeeb1f62c -> retryableFailure
// - KNOWLEDGE-BUILD-LIFECYCLE-001@sha256:14dc0303d926b0db74a87cdad73b5e0b07d1c5775d3907b8a00663cfeeb1f62c -> stopBuild
// - KNOWLEDGE-BUILD-LIFECYCLE-001@sha256:14dc0303d926b0db74a87cdad73b5e0b07d1c5775d3907b8a00663cfeeb1f62c -> failBuild
// - KNOWLEDGE-BUILD-LIFECYCLE-001@sha256:14dc0303d926b0db74a87cdad73b5e0b07d1c5775d3907b8a00663cfeeb1f62c -> resumeBuild
// - KNOWLEDGE-BUILD-LIFECYCLE-001@sha256:14dc0303d926b0db74a87cdad73b5e0b07d1c5775d3907b8a00663cfeeb1f62c -> cancelBuild
// - KNOWLEDGE-BUILD-LIFECYCLE-001@sha256:14dc0303d926b0db74a87cdad73b5e0b07d1c5775d3907b8a00663cfeeb1f62c -> projectedStateIsConsistent
//
// Abstraction:
// The durable Knowledge build store is modeled as requested/stopped/failed/
// pending-topic flags plus a global provider-pause overlay. Topic identity,
// concrete work scheduling, provider choice, and generated page content remain
// implementation concerns.

module knowledge_build_lifecycle {
  val NONE = 0
  val QUEUED = 1
  val RUNNING = 2
  val PAUSED = 3
  val STOPPED = 4
  val FAILED = 5

  var requested: bool
  var stopped: bool
  var failed: bool
  var pendingTopics: bool
  var providerPaused: bool
  var errorPresent: bool
  var attemptToken: bool

  val projectedState =
    if (not(requested)) NONE
    else if (providerPaused) PAUSED
    else if (stopped) STOPPED
    else if (failed) FAILED
    else if (pendingTopics) RUNNING
    else QUEUED

  action init = all {
    requested' = false,
    stopped' = false,
    failed' = false,
    pendingTopics' = false,
    providerPaused' = false,
    errorPresent' = false,
    attemptToken' = false,
  }

  action requestBuild = all {
    requested' = true,
    stopped' = false,
    failed' = false,
    pendingTopics' = false,
    providerPaused' = providerPaused,
    errorPresent' = false,
    attemptToken' = not(attemptToken),
  }

  action planTopics = all {
    requested,
    not(stopped),
    not(failed),
    not(providerPaused),
    requested' = requested,
    stopped' = stopped,
    failed' = failed,
    pendingTopics' = true,
    providerPaused' = providerPaused,
    errorPresent' = false,
    attemptToken' = attemptToken,
  }

  action completeWithoutTopics = all {
    requested,
    not(stopped),
    not(failed),
    not(providerPaused),
    not(pendingTopics),
    requested' = false,
    stopped' = false,
    failed' = false,
    pendingTopics' = false,
    providerPaused' = providerPaused,
    errorPresent' = false,
    attemptToken' = attemptToken,
  }

  action completeLastTopic = all {
    requested,
    not(stopped),
    not(failed),
    pendingTopics,
    requested' = false,
    stopped' = false,
    failed' = false,
    pendingTopics' = false,
    providerPaused' = providerPaused,
    errorPresent' = false,
    attemptToken' = attemptToken,
  }

  action pauseGlobal = all {
    not(providerPaused),
    requested' = requested,
    stopped' = stopped,
    failed' = failed,
    pendingTopics' = false,
    providerPaused' = true,
    errorPresent' = errorPresent,
    attemptToken' = attemptToken,
  }

  action unpauseGlobal = all {
    providerPaused,
    requested' = requested,
    stopped' = stopped,
    failed' = failed,
    pendingTopics' = pendingTopics,
    providerPaused' = false,
    errorPresent' = errorPresent,
    attemptToken' = attemptToken,
  }

  action retryableFailure = all {
    requested,
    not(stopped),
    not(failed),
    pendingTopics,
    requested' = requested,
    stopped' = stopped,
    failed' = failed,
    pendingTopics' = pendingTopics,
    providerPaused' = providerPaused,
    errorPresent' = true,
    attemptToken' = attemptToken,
  }

  action stopBuild = all {
    requested,
    not(stopped),
    requested' = requested,
    stopped' = true,
    failed' = false,
    pendingTopics' = pendingTopics,
    providerPaused' = providerPaused,
    errorPresent' = false,
    attemptToken' = attemptToken,
  }

  action failBuild = all {
    requested,
    requested' = requested,
    stopped' = false,
    failed' = true,
    pendingTopics' = pendingTopics,
    providerPaused' = providerPaused,
    errorPresent' = true,
    attemptToken' = attemptToken,
  }

  action resumeBuild = all {
    requested,
    (stopped or failed),
    requested' = true,
    stopped' = false,
    failed' = false,
    pendingTopics' = false,
    providerPaused' = providerPaused,
    errorPresent' = false,
    attemptToken' = not(attemptToken),
  }

  action cancelBuild = all {
    requested,
    requested' = false,
    stopped' = false,
    failed' = false,
    pendingTopics' = false,
    providerPaused' = providerPaused,
    errorPresent' = false,
    attemptToken' = attemptToken,
  }

  action idle = all {
    requested' = requested,
    stopped' = stopped,
    failed' = failed,
    pendingTopics' = pendingTopics,
    providerPaused' = providerPaused,
    errorPresent' = errorPresent,
    attemptToken' = attemptToken,
  }

  action step = any {
    requestBuild,
    planTopics,
    completeWithoutTopics,
    completeLastTopic,
    pauseGlobal,
    unpauseGlobal,
    retryableFailure,
    stopBuild,
    failBuild,
    resumeBuild,
    cancelBuild,
    idle,
  }

  val durableFlagsAreConsistent =
    not(stopped and failed) and
    (requested or (not(stopped) and not(failed) and not(pendingTopics) and not(errorPresent))) and
    (not(failed) or errorPresent)

  val projectedStateIsConsistent =
    (projectedState == NONE implies not(requested)) and
    (projectedState == PAUSED implies requested and providerPaused) and
    (projectedState == STOPPED implies requested and stopped and not(providerPaused)) and
    (projectedState == FAILED implies requested and failed and not(providerPaused)) and
    (projectedState == RUNNING implies requested and pendingTopics and not(stopped) and not(failed) and not(providerPaused)) and
    (projectedState == QUEUED implies requested and not(pendingTopics) and not(stopped) and not(failed) and not(providerPaused))

  val safety =
    durableFlagsAreConsistent and
    projectedStateIsConsistent
}
