package dev.terashima.yomitorirss.feature.knowledge.data

import dev.terashima.yomitorirss.feature.knowledge.KnowledgePage
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageSummary
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class DefaultKnowledgeRepository(
  private val store: SqlKnowledgePageStore,
) : KnowledgeReader {
  override val changes: StateFlow<Long> = store.changes

  override suspend fun listPages(query: String): List<KnowledgePageSummary> = withContext(Dispatchers.IO) {
    store.listPages(query)
  }

  override suspend fun findPage(id: String): KnowledgePage? = withContext(Dispatchers.IO) {
    store.findPage(id)
  }
}
