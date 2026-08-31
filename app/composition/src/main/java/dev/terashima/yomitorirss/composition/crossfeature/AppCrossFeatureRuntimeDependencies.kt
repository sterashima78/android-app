package dev.terashima.yomitorirss.composition.crossfeature

import android.app.Application
import dev.terashima.yomitorirss.composition.library.LibraryRuntimeDependencies
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.aitaskqueue.data.CompositeAiTaskQueueRepository
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkContentQuery
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.chat.ChatGenerator
import dev.terashima.yomitorirss.feature.chat.data.LocalChatGenerator
import dev.terashima.yomitorirss.feature.chat.data.createAppResourceSkills
import dev.terashima.yomitorirss.feature.chat.data.createKnowledgeLibraryResourceSkills
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionSettings
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeReader
import dev.terashima.yomitorirss.feature.reddit.RedditRepository
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.summary.BackfillBookmarkAutoEnrichmentUseCase
import dev.terashima.yomitorirss.feature.summary.ReprocessBookmarkAutoEnrichmentUseCase
import dev.terashima.yomitorirss.feature.summary.SummaryRepository
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository
import dev.terashima.yomitorirss.feature.summary.data.DefaultSummaryTaskQueueRepository
import dev.terashima.yomitorirss.feature.task.TaskRepository

/** Cross-feature application services assembled after owner repositories are available. */
internal class AppCrossFeatureRuntimeDependencies(
  private val application: Application,
  private val database: YomitoriDatabase,
  private val modelManager: LocalModelManager,
  private val articleRepository: ArticleRepository,
  private val bookmarkContentQuery: BookmarkContentQuery,
  private val bookmarkRepository: BookmarkRepository,
  private val feedRepository: FeedRepository,
  private val redditRepository: RedditRepository,
  private val summaryRepository: SummaryRepository,
  private val taskRepository: TaskRepository,
  private val libraryRuntime: LibraryRuntimeDependencies,
  private val knowledgeReader: KnowledgeReader,
  private val knowledgeBuildTaskController: KnowledgeBuildTaskController,
  private val knowledgeExecutionSettings: KnowledgeExecutionSettings,
) {
  val chatGenerator: ChatGenerator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    LocalChatGenerator(
      modelManager = modelManager,
      skills = createAppResourceSkills(
        articleRepository = articleRepository,
        bookmarkRepository = bookmarkRepository,
        feedRepository = feedRepository,
        redditRepository = redditRepository,
        summaryRepository = summaryRepository,
        taskRepository = taskRepository,
      ) + createKnowledgeLibraryResourceSkills(
        knowledgeReader = knowledgeReader,
        libraryReader = libraryRuntime.catalogRepository,
      ),
    )
  }

  val backfillBookmarkAutoEnrichmentUseCase: BackfillBookmarkAutoEnrichmentUseCase by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    BackfillBookmarkAutoEnrichmentUseCase(
      bookmarks = bookmarkRepository,
      batchRequester = summaryRepository,
    )
  }

  val reprocessBookmarkAutoEnrichmentUseCase: ReprocessBookmarkAutoEnrichmentUseCase by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    ReprocessBookmarkAutoEnrichmentUseCase(
      bookmarks = bookmarkRepository,
      batchRequester = summaryRepository,
    )
  }

  val summaryTaskQueueRepository: SummaryTaskQueueRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultSummaryTaskQueueRepository(
      context = application,
      database = database,
      articleRepository = articleRepository,
      bookmarkContentQuery = bookmarkContentQuery,
    )
  }

  val aiTaskQueueRepository: AiTaskQueueRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    CompositeAiTaskQueueRepository(
      summaryRepository = summaryTaskQueueRepository,
      libraryRepository = libraryRuntime.organizationRepository,
      libraryCatalogRepository = libraryRuntime.catalogRepository,
      libraryScheduler = libraryRuntime.organizationBatchScheduler,
      knowledgeController = knowledgeBuildTaskController,
      knowledgeExecutionSettings = knowledgeExecutionSettings,
      smbMetadataNormalizationRepository = libraryRuntime.smbMetadataNormalizationRepository,
      smbMetadataNormalizationScheduler = libraryRuntime.smbMetadataNormalizationScheduler,
    )
  }
}
