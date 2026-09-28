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

enum class KnowledgePageAiTaskKind {
  CREATE,
  EDIT,
}

data class KnowledgePageAiTaskReference(
  val requestId: String,
  val kind: KnowledgePageAiTaskKind,
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

  suspend fun recoverableTask(): KnowledgePageAiTaskReference?

  suspend fun dismiss(requestId: String)
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
