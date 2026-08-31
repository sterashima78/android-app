package dev.terashima.yomitorirss.feature.knowledge.data

import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildPlan
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildResult
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildRunner
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuilder
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionProvider
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionSettings
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePage
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageCreator
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageEditor

class RoutingKnowledgeGenerationService(
  private val local: DefaultKnowledgeGenerationService,
  private val cloud: DefaultKnowledgeGenerationService,
  private val executionSettings: KnowledgeExecutionSettings,
) : KnowledgeBuilder, KnowledgeBuildRunner, KnowledgePageCreator, KnowledgePageEditor {
  override suspend fun rebuild(): KnowledgeBuildResult =
    rebuild(executionSettings.currentProvider())

  override suspend fun rebuild(provider: KnowledgeExecutionProvider): KnowledgeBuildResult =
    delegate(provider).rebuild(autoWikiSourceLimit(provider))

  override suspend fun planRebuild(provider: KnowledgeExecutionProvider): KnowledgeBuildPlan =
    delegate(provider).planRebuild(autoWikiSourceLimit(provider))

  override suspend fun rebuildTopic(
    provider: KnowledgeExecutionProvider,
    topicId: String,
  ): Boolean = delegate(provider).rebuildTopic(topicId, autoWikiSourceLimit(provider))

  override suspend fun createPage(request: String, sourcePageId: String?): KnowledgePage =
    delegate(executionSettings.currentProvider()).createPage(request, sourcePageId)

  override suspend fun editPage(id: String, instruction: String): KnowledgePage =
    delegate(executionSettings.currentProvider()).editPage(id, instruction)

  private fun delegate(provider: KnowledgeExecutionProvider): DefaultKnowledgeGenerationService = when (provider) {
    KnowledgeExecutionProvider.LOCAL -> local
    KnowledgeExecutionProvider.CHATGPT -> cloud
  }
}

internal fun autoWikiSourceLimit(provider: KnowledgeExecutionProvider): Int = when (provider) {
  KnowledgeExecutionProvider.LOCAL -> MAX_SOURCES_PER_TOPIC
  KnowledgeExecutionProvider.CHATGPT -> CLOUD_MAX_SOURCES_PER_TOPIC
}

internal const val CLOUD_MAX_SOURCES_PER_TOPIC = 100
