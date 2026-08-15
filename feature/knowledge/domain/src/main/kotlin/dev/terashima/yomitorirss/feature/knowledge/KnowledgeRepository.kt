package dev.terashima.yomitorirss.feature.knowledge

import kotlinx.coroutines.flow.StateFlow

interface KnowledgeRepository {
  val changes: StateFlow<Long>
  suspend fun listPages(query: String = ""): List<KnowledgePageSummary>
  suspend fun findPage(id: String): KnowledgePage?
  suspend fun rebuild(): KnowledgeBuildResult
  suspend fun createPage(request: String, sourcePageId: String? = null): KnowledgePage
  suspend fun editPage(id: String, instruction: String): KnowledgePage
}
