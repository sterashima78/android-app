// Natural-language specifications:
// - docs/spec/13-background-execution.md
//
// Covers:
// - RSS-RECOMMENDATION-QUEUE-001@sha256:f5909ae74b343dc88bd0fdea80222a980a93a226c2647970fdfe65cf77571ed4 -> reconcileCurrentRevision
// - RSS-RECOMMENDATION-QUEUE-001@sha256:f5909ae74b343dc88bd0fdea80222a980a93a226c2647970fdfe65cf77571ed4 -> requeueInterrupted
// - RSS-RECOMMENDATION-QUEUE-001@sha256:f5909ae74b343dc88bd0fdea80222a980a93a226c2647970fdfe65cf77571ed4 -> reconciledQueueUsesCurrentRevision
//
// Abstraction:
// One representative article is assumed to remain eligible for evaluation.
// Revision change and queue reconciliation are separate because production may
// briefly retain an old task until the scheduler rebuilds the queue.
// Reconciliation abstracts the transaction that removes stale tasks and queues
// the representative article at the current revision.

module rss_recommendation_queue {
  val NONE = 0
  val QUEUED = 1
  val RUNNING = 2

  var currentRevision: int
  var taskRevision: int
  var taskState: int
  var reconciled: bool

  action init = all {
    currentRevision' = 1,
    taskRevision' = 0,
    taskState' = NONE,
    reconciled' = true,
  }

  action enqueueCurrent = all {
    taskState == NONE,
    taskRevision' = currentRevision,
    taskState' = QUEUED,
    currentRevision' = currentRevision,
    reconciled' = reconciled,
  }

  action claimNext = all {
    taskState == QUEUED,
    currentRevision' = currentRevision,
    taskRevision' = taskRevision,
    taskState' = RUNNING,
    reconciled' = reconciled,
  }

  action requeueInterrupted = all {
    taskState == RUNNING,
    currentRevision' = currentRevision,
    taskRevision' = taskRevision,
    taskState' = QUEUED,
    reconciled' = reconciled,
  }

  action completeTask = all {
    taskState == RUNNING,
    currentRevision' = currentRevision,
    taskRevision' = 0,
    taskState' = NONE,
    reconciled' = reconciled,
  }

  action changeRevision = all {
    currentRevision == 1,
    currentRevision' = 2,
    taskRevision' = taskRevision,
    taskState' = taskState,
    reconciled' = false,
  }

  action reconcileCurrentRevision = all {
    not(reconciled),
    currentRevision' = currentRevision,
    taskRevision' = currentRevision,
    taskState' = QUEUED,
    reconciled' = true,
  }

  action idle = all {
    currentRevision' = currentRevision,
    taskRevision' = taskRevision,
    taskState' = taskState,
    reconciled' = reconciled,
  }

  action step = any {
    enqueueCurrent,
    claimNext,
    requeueInterrupted,
    completeTask,
    changeRevision,
    reconcileCurrentRevision,
    idle,
  }

  val knownState =
    taskState == NONE or taskState == QUEUED or taskState == RUNNING

  val knownRevision =
    currentRevision == 1 or currentRevision == 2

  val validTaskRevision =
    taskState == NONE or taskRevision == 1 or taskRevision == 2

  val reconciledQueueUsesCurrentRevision =
    not(reconciled) or taskState == NONE or taskRevision == currentRevision

  val safety =
    knownState and
    knownRevision and
    validTaskRevision and
    reconciledQueueUsesCurrentRevision
}
