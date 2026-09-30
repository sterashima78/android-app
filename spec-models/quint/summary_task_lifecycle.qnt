// Natural-language specifications:
// - docs/spec/05-on-device-ai.md
//
// Covers:
// - SUMMARY-TASK-LIFECYCLE-001@sha256:f9f97a4d72dcbb2b34a8ae9631e864121542f5d48777db9ffc39b7122b933fa7 -> rejectDuplicateEnqueue
// - SUMMARY-TASK-LIFECYCLE-001@sha256:f9f97a4d72dcbb2b34a8ae9631e864121542f5d48777db9ffc39b7122b933fa7 -> resumeTask
// - SUMMARY-TASK-LIFECYCLE-001@sha256:f9f97a4d72dcbb2b34a8ae9631e864121542f5d48777db9ffc39b7122b933fa7 -> requeueInterrupted
//
// Abstraction:
// The model follows one representative article task. Durable per-article
// uniqueness is checked structurally in summary_task_uniqueness.als.
// Framework work identities, progress stages, and provider routing are omitted.

module summary_task_lifecycle {
  val NONE = 0
  val QUEUED = 1
  val RUNNING = 2
  val STOPPED = 3
  val FAILED = 4
  val CANCELLED = 5
  val COMPLETED = 6

  var state: int

  action init = all {
    state' = NONE,
  }

  action enqueueTask = all {
    (state == NONE or state == STOPPED or state == FAILED or
      state == CANCELLED or state == COMPLETED),
    state' = QUEUED,
  }

  action rejectDuplicateEnqueue = all {
    (state == QUEUED or state == RUNNING),
    state' = state,
  }

  action claimTask = all {
    state == QUEUED,
    state' = RUNNING,
  }

  action stopTask = all {
    (state == QUEUED or state == RUNNING),
    state' = STOPPED,
  }

  action cancelTask = all {
    (state == QUEUED or state == RUNNING or state == STOPPED),
    state' = CANCELLED,
  }

  action resumeTask = all {
    (state == STOPPED or state == FAILED),
    state' = QUEUED,
  }

  action requeueInterrupted = all {
    state == RUNNING,
    state' = QUEUED,
  }

  action completeTask = all {
    state == RUNNING,
    state' = COMPLETED,
  }

  action failQueuedTask = all {
    state == QUEUED,
    state' = FAILED,
  }

  action failRunningTask = all {
    state == RUNNING,
    state' = FAILED,
  }

  action idle = all {
    state' = state,
  }

  action step = any {
    enqueueTask,
    rejectDuplicateEnqueue,
    claimTask,
    stopTask,
    cancelTask,
    resumeTask,
    requeueInterrupted,
    completeTask,
    failQueuedTask,
    failRunningTask,
    idle,
  }

  val knownState =
    state == NONE or state == QUEUED or state == RUNNING or
    state == STOPPED or state == FAILED or state == CANCELLED or
    state == COMPLETED

  val safety = knownState
}
