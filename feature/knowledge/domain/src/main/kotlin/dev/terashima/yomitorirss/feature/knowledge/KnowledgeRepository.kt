package dev.terashima.yomitorirss.feature.knowledge

interface KnowledgeRepository {
  suspend fun listPages(query: String = ""): List<KnowledgePageSummary>
  suspend fun findPage(id: String): KnowledgePage?
  suspend fun rebuild(): KnowledgeBuildResult
}
