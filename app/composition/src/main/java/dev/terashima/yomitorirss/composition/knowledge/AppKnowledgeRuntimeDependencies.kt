package dev.terashima.yomitorirss.composition.knowledge

import dev.terashima.yomitorirss.core.aiinference.BackgroundAiTextInference
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildRunner
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuilder
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionSettings
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageAiRunner
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.data.DefaultKnowledgeGenerationService
import dev.terashima.yomitorirss.feature.knowledge.data.DefaultKnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.data.ManagingKnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.data.RoutingKnowledgeGenerationService
import dev.terashima.yomitorirss.feature.knowledge.data.SqlKnowledgePageStore
import dev.terashima.yomitorirss.feature.summary.SummaryRepository

/** Knowledge persistence/generation graph. */
internal class AppKnowledgeRuntimeDependencies(
  database: DatabaseConnection,
  dataChanges: DataChangeNotifier,
  bookmarks: BookmarkRepository,
  summaries: SummaryRepository,
  localTextInference: BackgroundAiTextInference,
  cloudTextInference: BackgroundAiTextInference,
  executionSettings: KnowledgeExecutionSettings,
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

  private val generationService: RoutingKnowledgeGenerationService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    RoutingKnowledgeGenerationService(
      local = DefaultKnowledgeGenerationService(
        store = knowledgePageStore,
        bookmarks = bookmarks,
        summaries = summaries,
        textInference = localTextInference,
      ),
      cloud = DefaultKnowledgeGenerationService(
        store = knowledgePageStore,
        bookmarks = bookmarks,
        summaries = summaries,
        textInference = cloudTextInference,
      ),
      executionSettings = executionSettings,
    )
  }

  val knowledgeBuilder: KnowledgeBuilder get() = generationService
  val knowledgeBuildRunner: KnowledgeBuildRunner get() = generationService
  val knowledgePageAiRunner: KnowledgePageAiRunner get() = generationService
}
