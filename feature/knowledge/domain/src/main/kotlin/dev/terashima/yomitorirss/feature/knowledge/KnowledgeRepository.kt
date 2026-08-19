package dev.terashima.yomitorirss.feature.knowledge

import kotlinx.coroutines.flow.StateFlow

interface KnowledgeReader {
  val changes: StateFlow<Long>
  suspend fun listPages(query: String = ""): List<KnowledgePageSummary>
  suspend fun findPage(id: String): KnowledgePage?
}

interface KnowledgePageManager {
  suspend fun deletePage(id: String)
  suspend fun splitPage(id: String, heading: String): KnowledgePage
  suspend fun mergePages(primaryId: String, secondaryId: String): KnowledgePage
}

interface KnowledgeRepository : KnowledgeReader, KnowledgePageManager

interface KnowledgeBuilder {
  suspend fun rebuild(): KnowledgeBuildResult
}

interface KnowledgePageCreator {
  suspend fun createPage(request: String, sourcePageId: String? = null): KnowledgePage
}

interface KnowledgePageEditor {
  suspend fun editPage(id: String, instruction: String): KnowledgePage
}

class BuildKnowledgeUseCase(
  private val builder: KnowledgeBuilder,
) {
  suspend operator fun invoke(): KnowledgeBuildResult = builder.rebuild()
}

class CreateKnowledgePageUseCase(
  private val creator: KnowledgePageCreator,
) {
  suspend operator fun invoke(request: String, sourcePageId: String? = null): KnowledgePage =
    creator.createPage(request, sourcePageId)
}

class EditKnowledgePageUseCase(
  private val editor: KnowledgePageEditor,
) {
  suspend operator fun invoke(id: String, instruction: String): KnowledgePage =
    editor.editPage(id, instruction)
}
