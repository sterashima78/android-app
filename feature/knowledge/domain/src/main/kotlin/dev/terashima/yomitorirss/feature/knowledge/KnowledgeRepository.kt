package dev.terashima.yomitorirss.feature.knowledge

interface KnowledgeRepository {
  suspend fun listPages(query: String = ""): List<KnowledgePageSummary>
  suspend fun findPage(id: String): KnowledgePage?
  suspend fun rebuild(): KnowledgeBuildResult
  suspend fun createPage(request: String, sourcePageId: String? = null): KnowledgePage
  suspend fun editPage(id: String, instruction: String): KnowledgePage
}
