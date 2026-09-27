package dev.terashima.yomitorirss.feature.knowledge

enum class KnowledgePageAiTaskState {
  QUEUED,
  RUNNING,
  SUCCEEDED,
  FAILED,
  CANCELLED,
}

data class KnowledgePageAiTaskSnapshot(
  val state: KnowledgePageAiTaskState,
  val pageId: String? = null,
  val error: String? = null,
)

interface KnowledgePageAiTaskController {
  suspend fun enqueueCreate(
    request: String,
    sourcePageId: String? = null,
  ): String

  suspend fun enqueueEdit(
    pageId: String,
    instruction: String,
  ): String

  suspend fun snapshot(requestId: String): KnowledgePageAiTaskSnapshot
}

interface KnowledgePageAiRunner {
  suspend fun createPage(
    provider: KnowledgeExecutionProvider,
    request: String,
    sourcePageId: String? = null,
  ): KnowledgePage

  suspend fun editPage(
    provider: KnowledgeExecutionProvider,
    pageId: String,
    instruction: String,
  ): KnowledgePage
}
