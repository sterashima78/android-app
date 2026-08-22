package dev.terashima.yomitorirss

import android.app.Application
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
import dev.terashima.yomitorirss.feature.reddit.RedditRepository
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.summary.BackfillBookmarkAutoEnrichmentUseCase
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
  private val featureRuntimeDependencies: AppFeatureRuntimeDependencies,
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
      ),
    )
  }

  val backfillBookmarkAutoEnrichmentUseCase: BackfillBookmarkAutoEnrichmentUseCase by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    BackfillBookmarkAutoEnrichmentUseCase(
      bookmarks = bookmarkRepository,
      enrichmentRequester = summaryRepository,
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
    val library = featureRuntimeDependencies.library
    CompositeAiTaskQueueRepository(
      summaryRepository = summaryTaskQueueRepository,
      libraryRepository = library.organizationRepository,
      libraryCatalogRepository = library.catalogRepository,
      libraryScheduler = library.organizationBatchScheduler,
      knowledgeController = featureRuntimeDependencies.knowledgeBuildTaskController,
      smbMetadataNormalizationRepository = library.smbMetadataNormalizationRepository,
      smbMetadataNormalizationScheduler = library.smbMetadataNormalizationScheduler,
    )
  }
}
