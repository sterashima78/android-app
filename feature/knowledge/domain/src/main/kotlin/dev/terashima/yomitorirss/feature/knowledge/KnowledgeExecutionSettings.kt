package dev.terashima.yomitorirss.feature.knowledge

import kotlinx.coroutines.flow.StateFlow

enum class KnowledgeExecutionProvider {
  LOCAL,
  CHATGPT,
}

interface KnowledgeExecutionSettings {
  val provider: StateFlow<KnowledgeExecutionProvider>
  fun currentProvider(): KnowledgeExecutionProvider
  fun setProvider(provider: KnowledgeExecutionProvider)
}

/** Background build capability with the execution provider fixed when work is scheduled. */
interface KnowledgeBuildRunner {
  suspend fun planRebuild(provider: KnowledgeExecutionProvider): KnowledgeBuildPlan
  suspend fun rebuildTopic(provider: KnowledgeExecutionProvider, topicId: String): Boolean
  suspend fun rebuild(provider: KnowledgeExecutionProvider): KnowledgeBuildResult
}

enum class KnowledgeCloudFailureKind {
  AUTHENTICATION,
  RATE_LIMITED,
  TRANSIENT,
  REQUEST_REJECTED,
  UNKNOWN,
}

class KnowledgeCloudInferenceException(
  val kind: KnowledgeCloudFailureKind,
  val retryable: Boolean,
  message: String,
) : IllegalStateException(message)
