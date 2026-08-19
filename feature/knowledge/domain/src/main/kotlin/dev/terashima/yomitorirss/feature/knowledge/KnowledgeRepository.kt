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

/** AI生成を含むKnowledge再構築application capability。 */
interface KnowledgeBuilder {
  suspend fun rebuild(): KnowledgeBuildResult
}

/** ユーザー要求からKnowledge pageを生成するapplication capability。 */
interface KnowledgePageCreator {
  suspend fun createPage(request: String, sourcePageId: String? = null): KnowledgePage
}

/** 既存Knowledge pageをAIで編集するapplication capability。 */
interface KnowledgePageEditor {
  suspend fun editPage(id: String, instruction: String): KnowledgePage
}
