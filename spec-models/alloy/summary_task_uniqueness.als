// Natural-language specifications:
// - docs/spec/05-on-device-ai.md
//
// Covers:
// - SUMMARY-TASK-LIFECYCLE-001@sha256:f9f97a4d72dcbb2b34a8ae9631e864121542f5d48777db9ffc39b7122b933fa7 -> AtMostOneDurableTaskPerArticle
//
// Scope:
// A SummaryTask represents the current durable task row for an article, not a
// historical execution attempt. Lifecycle transitions are checked in Quint.

module summary_task_uniqueness

sig Article {}

abstract sig TaskState {}
one sig Queued, Running, Stopped, Failed, Cancelled, Completed extends TaskState {}

sig SummaryTask {
  article: one Article,
  state: one TaskState
}

fact OneDurableTaskPerArticle {
  all article: Article |
    lone { task: SummaryTask | task.article = article }
}

assert AtMostOneDurableTaskPerArticle {
  all article: Article |
    lone { task: SummaryTask | task.article = article }
}

check AtMostOneDurableTaskPerArticle for 6 expect 0

run TwoIndependentArticleTasks {
  #Article = 2
  #SummaryTask = 2
} for 6 expect 1
