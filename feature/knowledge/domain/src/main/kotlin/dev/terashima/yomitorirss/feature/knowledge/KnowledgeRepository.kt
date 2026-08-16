package dev.terashima.yomitorirss.feature.knowledge

import kotlinx.coroutines.flow.StateFlow

interface KnowledgeRepository {
  val changes: StateFlow<Long>
  suspend fun listPages(query: String = ""): List<KnowledgePageSummary>
  suspend fun findPage(id: String): KnowledgePage?
  suspend fun rebuild(): KnowledgeBuildResult
  suspend fun createPage(request: String, sourcePageId: String? = null): KnowledgePage
  suspend fun editPage(id: String, instruction: String): KnowledgePage

  suspend fun deletePage(id: String) {
    error("この記事の削除には対応していません")
  }

  suspend fun splitPage(id: String, heading: String): KnowledgePage {
    error("この記事の分割には対応していません")
  }

  suspend fun mergePages(primaryId: String, secondaryId: String): KnowledgePage {
    error("この記事の統合には対応していません")
  }
}
