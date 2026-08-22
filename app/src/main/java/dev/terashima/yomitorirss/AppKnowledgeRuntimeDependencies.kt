package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuilder
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageCreator
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageEditor
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.data.DefaultKnowledgeGenerationService
import dev.terashima.yomitorirss.feature.knowledge.data.DefaultKnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.data.ManagingKnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.data.SqlKnowledgePageStore
import dev.terashima.yomitorirss.feature.summary.SummaryRepository

/** Knowledge persistence/generation graph. */
internal class AppKnowledgeRuntimeDependencies(
  database: DatabaseConnection,
  dataChanges: DataChangeNotifier,
  bookmarks: BookmarkRepository,
  summaries: SummaryRepository,
  modelManager: LocalModelManager,
) {
  private val knowledgePageStore: SqlKnowledgePageStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SqlKnowledgePageStore(database, dataChanges)
  }

  private val knowledgeReader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultKnowledgeRepository(knowledgePageStore)
  }

  val knowledgeRepository: KnowledgeRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    ManagingKnowledgeRepository(
      delegate = knowledgeReader,
      database = database,
      dataChanges = dataChanges,
    )
  }

  private val generationService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultKnowledgeGenerationService(
      store = knowledgePageStore,
      bookmarks = bookmarks,
      summaries = summaries,
      modelManager = modelManager,
    )
  }

  val knowledgeBuilder: KnowledgeBuilder get() = generationService
  val knowledgePageCreator: KnowledgePageCreator get() = generationService
  val knowledgePageEditor: KnowledgePageEditor get() = generationService
}
