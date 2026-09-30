// Natural-language specifications:
// - docs/spec/05-on-device-ai.md
//
// Covers:
// - KNOWLEDGE-PAGE-AI-TASK-001@sha256:135e47d98d0115b8da74aa2941752b4d13178d2552b83d5dcf89236af2b31db2 -> startRunning
// - KNOWLEDGE-PAGE-AI-TASK-001@sha256:135e47d98d0115b8da74aa2941752b4d13178d2552b83d5dcf89236af2b31db2 -> retryActive
// - KNOWLEDGE-PAGE-AI-TASK-001@sha256:135e47d98d0115b8da74aa2941752b4d13178d2552b83d5dcf89236af2b31db2 -> succeed
// - KNOWLEDGE-PAGE-AI-TASK-001@sha256:135e47d98d0115b8da74aa2941752b4d13178d2552b83d5dcf89236af2b31db2 -> failTerminal
// - KNOWLEDGE-PAGE-AI-TASK-001@sha256:135e47d98d0115b8da74aa2941752b4d13178d2552b83d5dcf89236af2b31db2 -> cancel
// - KNOWLEDGE-PAGE-AI-TASK-001@sha256:135e47d98d0115b8da74aa2941752b4d13178d2552b83d5dcf89236af2b31db2 -> dismissTerminal
// - KNOWLEDGE-PAGE-AI-TASK-001@sha256:135e47d98d0115b8da74aa2941752b4d13178d2552b83d5dcf89236af2b31db2 -> reconcileMissingWork
// - KNOWLEDGE-PAGE-AI-TASK-001@sha256:135e47d98d0115b8da74aa2941752b4d13178d2552b83d5dcf89236af2b31db2 -> activeTaskRetainsRequest
// - KNOWLEDGE-PAGE-AI-TASK-001@sha256:135e47d98d0115b8da74aa2941752b4d13178d2552b83d5dcf89236af2b31db2 -> terminalResultRemainsRecoverable
//
// Abstraction:
// One user-started Knowledge page AI task is modeled. The task reference is
// distinct from the potentially large request payload: terminal success/failure
// may delete request input while retaining a recoverable reference until UI
// consumption. Provider selection, concrete WorkManager constraints, page
// content, and retry backoff remain implementation concerns.

module knowledge_page_ai_task_lifecycle {
  val QUEUED = 0
  val RUNNING = 1
  val SUCCEEDED = 2
  val FAILED = 3
  val CANCELLED = 4

  var state: int
  var requestExists: bool
  var referenceExists: bool
  var workExists: bool
  var dismissed: bool

  action init = all {
    state' = QUEUED,
    requestExists' = true,
    referenceExists' = true,
    workExists' = true,
    dismissed' = false,
  }

  action startRunning = all {
    state == QUEUED,
    state' = RUNNING,
    requestExists' = requestExists,
    referenceExists' = referenceExists,
    workExists' = workExists,
    dismissed' = dismissed,
  }

  action retryActive = all {
    state == RUNNING,
    state' = QUEUED,
    requestExists' = requestExists,
    referenceExists' = referenceExists,
    workExists' = workExists,
    dismissed' = dismissed,
  }

  action succeed = all {
    state == RUNNING,
    state' = SUCCEEDED,
    requestExists' = false,
    referenceExists' = true,
    workExists' = true,
    dismissed' = false,
  }

  action failTerminal = all {
    state == RUNNING,
    state' = FAILED,
    requestExists' = false,
    referenceExists' = true,
    workExists' = true,
    dismissed' = false,
  }

  action cancel = all {
    (state == QUEUED or state == RUNNING),
    state' = CANCELLED,
    requestExists' = requestExists,
    referenceExists' = true,
    workExists' = true,
    dismissed' = false,
  }

  action cleanupCancelledRequest = all {
    state == CANCELLED,
    requestExists,
    state' = CANCELLED,
    requestExists' = false,
    referenceExists' = referenceExists,
    workExists' = workExists,
    dismissed' = dismissed,
  }

  action dismissTerminal = all {
    (state == SUCCEEDED or state == FAILED or state == CANCELLED),
    referenceExists,
    state' = state,
    requestExists' = requestExists,
    referenceExists' = false,
    workExists' = workExists,
    dismissed' = true,
  }

  action terminalWorkRecordDisappears = all {
    (state == SUCCEEDED or state == FAILED or state == CANCELLED),
    workExists,
    state' = state,
    requestExists' = requestExists,
    referenceExists' = referenceExists,
    workExists' = false,
    dismissed' = dismissed,
  }

  action reconcileMissingWork = all {
    not(workExists),
    referenceExists,
    state' = state,
    requestExists' = false,
    referenceExists' = false,
    workExists' = false,
    dismissed' = true,
  }

  action idle = all {
    state' = state,
    requestExists' = requestExists,
    referenceExists' = referenceExists,
    workExists' = workExists,
    dismissed' = dismissed,
  }

  action step = any {
    startRunning,
    retryActive,
    succeed,
    failTerminal,
    cancel,
    cleanupCancelledRequest,
    dismissTerminal,
    terminalWorkRecordDisappears,
    reconcileMissingWork,
    idle,
  }

  val knownState =
    state == QUEUED or
    state == RUNNING or
    state == SUCCEEDED or
    state == FAILED or
    state == CANCELLED

  val activeTaskRetainsRequest =
    not(state == QUEUED or state == RUNNING) or
    (workExists and requestExists and referenceExists and not(dismissed))

  val successfulOrFailedTaskReleasedRequest =
    not(state == SUCCEEDED or state == FAILED) or not(requestExists)

  val terminalResultRemainsRecoverable =
    not(
      workExists and
      not(dismissed) and
      (state == SUCCEEDED or state == FAILED or state == CANCELLED)
    ) or referenceExists

  val missingWorkIsNotActive =
    workExists or not(state == QUEUED or state == RUNNING)

  val dismissedHasNoReference =
    not(dismissed) or not(referenceExists)

  val safety =
    knownState and
    activeTaskRetainsRequest and
    successfulOrFailedTaskReleasedRequest and
    terminalResultRemainsRecoverable and
    missingWorkIsNotActive and
    dismissedHasNoReference
}
