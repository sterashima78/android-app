package dev.terashima.yomitorirss.feature.summary.data

import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority

internal fun selectNextSummaryTask(
  candidates: List<SummaryTaskRecord>,
  highPriorityArticleIds: Set<String>,
): SummaryTaskRecord? = candidates.minWithOrNull(
  compareBy<SummaryTaskRecord> { if (it.articleId in highPriorityArticleIds) 0 else 1 }
    .thenBy(SummaryTaskRecord::queuedAt),
)

internal fun summaryTaskPriority(
  task: SummaryTaskRecord,
  highPriorityArticleIds: Set<String>,
): LocalAiBackgroundTaskPriority = if (task.articleId in highPriorityArticleIds) {
  LocalAiBackgroundTaskPriority.HIGH
} else {
  LocalAiBackgroundTaskPriority.NORMAL
}
