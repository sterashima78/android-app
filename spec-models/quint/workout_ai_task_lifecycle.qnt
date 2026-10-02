// Natural-language specifications:
// - docs/spec/08-task-calendar-workout-health.md
//
// Covers:
// - WORKOUT-AI-BACKGROUND-001@sha256:36bce950bfadd976a2c16f136c86adc9b96b648ff2d1b2f9a87edab517e7bddd -> enqueueTask
// - WORKOUT-AI-BACKGROUND-001@sha256:36bce950bfadd976a2c16f136c86adc9b96b648ff2d1b2f9a87edab517e7bddd -> changeInputsBeforeRun
// - WORKOUT-AI-BACKGROUND-001@sha256:36bce950bfadd976a2c16f136c86adc9b96b648ff2d1b2f9a87edab517e7bddd -> startTask
// - WORKOUT-AI-BACKGROUND-001@sha256:36bce950bfadd976a2c16f136c86adc9b96b648ff2d1b2f9a87edab517e7bddd -> retryQueued
// - WORKOUT-AI-BACKGROUND-001@sha256:36bce950bfadd976a2c16f136c86adc9b96b648ff2d1b2f9a87edab517e7bddd -> finishSucceeded
// - WORKOUT-AI-BACKGROUND-001@sha256:36bce950bfadd976a2c16f136c86adc9b96b648ff2d1b2f9a87edab517e7bddd -> dismissTerminal
// - WORKOUT-AI-BACKGROUND-001@sha256:36bce950bfadd976a2c16f136c86adc9b96b648ff2d1b2f9a87edab517e7bddd -> loseWorkRecord
// - WORKOUT-AI-BACKGROUND-001@sha256:36bce950bfadd976a2c16f136c86adc9b96b648ff2d1b2f9a87edab517e7bddd -> removeStaleReference
// - WORKOUT-AI-BACKGROUND-001@sha256:36bce950bfadd976a2c16f136c86adc9b96b648ff2d1b2f9a87edab517e7bddd -> queuedHasNoPromptSnapshot
// - WORKOUT-AI-BACKGROUND-001@sha256:36bce950bfadd976a2c16f136c86adc9b96b648ff2d1b2f9a87edab517e7bddd -> activeOrTerminalTaskKeepsReference
//
// Abstraction:
// One Workout AI request is modeled. The current input revision represents the
// combined Workout snapshot, memo, policy/settings, and saved-review inputs used
// to build the prompt. The model intentionally distinguishes enqueue metadata
// from the prompt revision so queued work cannot carry a stale prompt snapshot.
// Concrete WorkManager states, prompt text, and provider-specific inference are
// covered by implementation tests and runtime adapters.

module workout_ai_task_lifecycle {
  val NONE = 0
  val QUEUED = 1
  val RUNNING = 2
  val SUCCEEDED = 3
  val FAILED = 4
  val CANCELLED = 5

  var state: int
  var currentInputRevision: int
  var promptRevision: int
  var referencePresent: bool
  var resultPresent: bool

  action init = all {
    state' = NONE,
    currentInputRevision' = 1,
    promptRevision' = 0,
    referencePresent' = false,
    resultPresent' = false,
  }

  action enqueueTask = all {
    state == NONE or state == SUCCEEDED or state == FAILED or state == CANCELLED,
    state' = QUEUED,
    currentInputRevision' = currentInputRevision,
    promptRevision' = 0,
    referencePresent' = true,
    resultPresent' = false,
  }

  action changeInputsBeforeRun = all {
    state == QUEUED,
    currentInputRevision == 1,
    state' = QUEUED,
    currentInputRevision' = 2,
    promptRevision' = 0,
    referencePresent' = referencePresent,
    resultPresent' = false,
  }

  action startTask = all {
    state == QUEUED,
    state' = RUNNING,
    currentInputRevision' = currentInputRevision,
    promptRevision' = currentInputRevision,
    referencePresent' = true,
    resultPresent' = false,
  }

  action changeInputsDuringRun = all {
    state == RUNNING,
    currentInputRevision == 1,
    state' = RUNNING,
    currentInputRevision' = 2,
    promptRevision' = promptRevision,
    referencePresent' = referencePresent,
    resultPresent' = resultPresent,
  }

  action retryQueued = all {
    state == RUNNING or state == QUEUED,
    state' = QUEUED,
    currentInputRevision' = currentInputRevision,
    promptRevision' = 0,
    referencePresent' = true,
    resultPresent' = false,
  }

  action finishSucceeded = all {
    state == RUNNING,
    state' = SUCCEEDED,
    currentInputRevision' = currentInputRevision,
    promptRevision' = promptRevision,
    referencePresent' = true,
    resultPresent' = true,
  }

  action finishFailed = all {
    state == RUNNING or state == QUEUED,
    state' = FAILED,
    currentInputRevision' = currentInputRevision,
    promptRevision' = promptRevision,
    referencePresent' = true,
    resultPresent' = false,
  }

  action finishCancelled = all {
    state == RUNNING or state == QUEUED,
    state' = CANCELLED,
    currentInputRevision' = currentInputRevision,
    promptRevision' = promptRevision,
    referencePresent' = true,
    resultPresent' = false,
  }

  action dismissTerminal = all {
    state == SUCCEEDED or state == FAILED or state == CANCELLED,
    state' = NONE,
    currentInputRevision' = currentInputRevision,
    promptRevision' = 0,
    referencePresent' = false,
    resultPresent' = false,
  }

  action loseWorkRecord = all {
    state != NONE,
    referencePresent,
    state' = NONE,
    currentInputRevision' = currentInputRevision,
    promptRevision' = 0,
    referencePresent' = true,
    resultPresent' = false,
  }

  action removeStaleReference = all {
    state == NONE,
    referencePresent,
    state' = NONE,
    currentInputRevision' = currentInputRevision,
    promptRevision' = 0,
    referencePresent' = false,
    resultPresent' = false,
  }

  action idle = all {
    state' = state,
    currentInputRevision' = currentInputRevision,
    promptRevision' = promptRevision,
    referencePresent' = referencePresent,
    resultPresent' = resultPresent,
  }

  action step = any {
    enqueueTask,
    changeInputsBeforeRun,
    startTask,
    changeInputsDuringRun,
    retryQueued,
    finishSucceeded,
    finishFailed,
    finishCancelled,
    dismissTerminal,
    loseWorkRecord,
    removeStaleReference,
    idle,
  }

  val knownState =
    state == NONE or state == QUEUED or state == RUNNING or
    state == SUCCEEDED or state == FAILED or state == CANCELLED

  val knownInputRevision =
    currentInputRevision == 1 or currentInputRevision == 2

  val queuedHasNoPromptSnapshot =
    not(state == QUEUED) or promptRevision == 0

  val runningHasExecutionPrompt =
    not(state == RUNNING) or promptRevision == 1 or promptRevision == 2

  val activeOrTerminalTaskKeepsReference =
    state == NONE or referencePresent

  val resultOnlyOnSuccess =
    not(resultPresent) or state == SUCCEEDED

  val safety =
    knownState and
    knownInputRevision and
    queuedHasNoPromptSnapshot and
    runningHasExecutionPrompt and
    activeOrTerminalTaskKeepsReference and
    resultOnlyOnSuccess
}
