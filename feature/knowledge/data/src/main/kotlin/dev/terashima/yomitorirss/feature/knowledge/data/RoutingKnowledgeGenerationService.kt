package dev.terashima.yomitorirss.feature.knowledge.data

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
    delegate(executionSettings.currentProvider()).rebuild()

  override suspend fun rebuild(provider: KnowledgeExecutionProvider): KnowledgeBuildResult =
    delegate(provider).rebuild()

  override suspend fun createPage(request: String, sourcePageId: String?): KnowledgePage =
    delegate(executionSettings.currentProvider()).createPage(request, sourcePageId)

  override suspend fun editPage(id: String, instruction: String): KnowledgePage =
    delegate(executionSettings.currentProvider()).editPage(id, instruction)

  private fun delegate(provider: KnowledgeExecutionProvider): DefaultKnowledgeGenerationService = when (provider) {
    KnowledgeExecutionProvider.LOCAL -> local
    KnowledgeExecutionProvider.CHATGPT -> cloud
  }
}
