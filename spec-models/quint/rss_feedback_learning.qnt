// Natural-language specifications:
// - docs/spec/04-content.md
//
// Covers:
// - RSS-FEEDBACK-LEARNING-001@sha256:7c0f6a4544276f70c89bd3e79bfc2d9178d292c4437cd1df1f3ae48927ee6e2f -> addFeedbackBeforeLearning
// - RSS-FEEDBACK-LEARNING-001@sha256:7c0f6a4544276f70c89bd3e79bfc2d9178d292c4437cd1df1f3ae48927ee6e2f -> addFeedbackDuringLearning
// - RSS-FEEDBACK-LEARNING-001@sha256:7c0f6a4544276f70c89bd3e79bfc2d9178d292c4437cd1df1f3ae48927ee6e2f -> debounceExpires
// - RSS-FEEDBACK-LEARNING-001@sha256:7c0f6a4544276f70c89bd3e79bfc2d9178d292c4437cd1df1f3ae48927ee6e2f -> policyChangesDuringLearning
// - RSS-FEEDBACK-LEARNING-001@sha256:7c0f6a4544276f70c89bd3e79bfc2d9178d292c4437cd1df1f3ae48927ee6e2f -> learningFailsWithoutLaterFeedback
// - RSS-FEEDBACK-LEARNING-001@sha256:7c0f6a4544276f70c89bd3e79bfc2d9178d292c4437cd1df1f3ae48927ee6e2f -> learningFailsWithLaterFeedback
// - RSS-FEEDBACK-LEARNING-001@sha256:7c0f6a4544276f70c89bd3e79bfc2d9178d292c4437cd1df1f3ae48927ee6e2f -> staleResultRejectedWithoutLaterFeedback
// - RSS-FEEDBACK-LEARNING-001@sha256:7c0f6a4544276f70c89bd3e79bfc2d9178d292c4437cd1df1f3ae48927ee6e2f -> staleResultRejectedWithLaterFeedback
// - RSS-FEEDBACK-LEARNING-001@sha256:7c0f6a4544276f70c89bd3e79bfc2d9178d292c4437cd1df1f3ae48927ee6e2f -> learningSucceedsWithoutLaterFeedback
// - RSS-FEEDBACK-LEARNING-001@sha256:7c0f6a4544276f70c89bd3e79bfc2d9178d292c4437cd1df1f3ae48927ee6e2f -> learningSucceedsWithLaterFeedback
// - RSS-FEEDBACK-LEARNING-001@sha256:7c0f6a4544276f70c89bd3e79bfc2d9178d292c4437cd1df1f3ae48927ee6e2f -> laterFeedbackOnlyExistsDuringLearning
//
// Abstraction:
// The model represents two feedback generations: the snapshot captured when a
// learning attempt starts and feedback added while that attempt is running.
// Concrete feedback IDs, the 50-item batch cap, WorkManager backoff, and model
// output text are intentionally omitted. A policy/provider change during an
// attempt is represented by the stale flag.

module rss_feedback_learning {
  var capturedPending: bool
  var laterPending: bool
  var debounceElapsed: bool
  var learning: bool
  var stale: bool

  action init = all {
    capturedPending' = false,
    laterPending' = false,
    debounceElapsed' = false,
    learning' = false,
    stale' = false,
  }

  action addFeedbackBeforeLearning = all {
    not(learning),
    capturedPending' = true,
    laterPending' = false,
    debounceElapsed' = false,
    learning' = false,
    stale' = false,
  }

  action addFeedbackDuringLearning = all {
    learning,
    capturedPending' = capturedPending,
    laterPending' = true,
    debounceElapsed' = false,
    learning' = true,
    stale' = stale,
  }

  action debounceExpires = all {
    not(learning),
    capturedPending,
    not(debounceElapsed),
    capturedPending' = capturedPending,
    laterPending' = false,
    debounceElapsed' = true,
    learning' = false,
    stale' = false,
  }

  action startLearning = all {
    not(learning),
    capturedPending,
    debounceElapsed,
    capturedPending' = true,
    laterPending' = false,
    debounceElapsed' = false,
    learning' = true,
    stale' = false,
  }

  action policyChangesDuringLearning = all {
    learning,
    capturedPending' = capturedPending,
    laterPending' = laterPending,
    debounceElapsed' = debounceElapsed,
    learning' = true,
    stale' = true,
  }

  action learningFailsWithoutLaterFeedback = all {
    learning,
    not(laterPending),
    capturedPending' = true,
    laterPending' = false,
    debounceElapsed' = true,
    learning' = false,
    stale' = false,
  }

  action learningFailsWithLaterFeedback = all {
    learning,
    laterPending,
    capturedPending' = true,
    laterPending' = false,
    debounceElapsed' = false,
    learning' = false,
    stale' = false,
  }

  action staleResultRejectedWithoutLaterFeedback = all {
    learning,
    stale,
    not(laterPending),
    capturedPending' = true,
    laterPending' = false,
    debounceElapsed' = true,
    learning' = false,
    stale' = false,
  }

  action staleResultRejectedWithLaterFeedback = all {
    learning,
    stale,
    laterPending,
    capturedPending' = true,
    laterPending' = false,
    debounceElapsed' = false,
    learning' = false,
    stale' = false,
  }

  action learningSucceedsWithoutLaterFeedback = all {
    learning,
    not(stale),
    not(laterPending),
    capturedPending' = false,
    laterPending' = false,
    debounceElapsed' = false,
    learning' = false,
    stale' = false,
  }

  action learningSucceedsWithLaterFeedback = all {
    learning,
    not(stale),
    laterPending,
    capturedPending' = true,
    laterPending' = false,
    debounceElapsed' = false,
    learning' = false,
    stale' = false,
  }

  action idle = all {
    capturedPending' = capturedPending,
    laterPending' = laterPending,
    debounceElapsed' = debounceElapsed,
    learning' = learning,
    stale' = stale,
  }

  action step = any {
    addFeedbackBeforeLearning,
    addFeedbackDuringLearning,
    debounceExpires,
    startLearning,
    policyChangesDuringLearning,
    learningFailsWithoutLaterFeedback,
    learningFailsWithLaterFeedback,
    staleResultRejectedWithoutLaterFeedback,
    staleResultRejectedWithLaterFeedback,
    learningSucceedsWithoutLaterFeedback,
    learningSucceedsWithLaterFeedback,
    idle,
  }

  val learningRequiresCapturedFeedback =
    not(learning) or capturedPending

  val laterFeedbackOnlyExistsDuringLearning =
    not(laterPending) or learning

  val staleOnlyExistsDuringLearning =
    not(stale) or learning

  val debounceReadyRequiresPendingFeedback =
    not(debounceElapsed) or (capturedPending and not(learning))

  val safety =
    learningRequiresCapturedFeedback and
    laterFeedbackOnlyExistsDuringLearning and
    staleOnlyExistsDuringLearning and
    debounceReadyRequiresPendingFeedback
}
