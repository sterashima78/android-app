package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.aitaskqueue.data.CompositeAiTaskQueueRepository
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.CompositeContentRetentionProtectionQuery
import dev.terashima.yomitorirss.feature.article.ContentRetentionProtectionQuery
import dev.terashima.yomitorirss.feature.article.ContentSourceGateway
import dev.terashima.yomitorirss.feature.article.data.DefaultArticleRepository
import dev.terashima.yomitorirss.feature.article.data.DefaultBookmarkArticleGateway
import dev.terashima.yomitorirss.feature.article.data.DefaultContentSourceGateway
import dev.terashima.yomitorirss.feature.asset.AssetRepository
import dev.terashima.yomitorirss.feature.asset.data.DefaultAssetRepository
import dev.terashima.yomitorirss.feature.backup.BackupChangeScheduler
import dev.terashima.yomitorirss.feature.backup.BackupRepository
import dev.terashima.yomitorirss.feature.backup.data.AndroidBackupChangeScheduler
import dev.terashima.yomitorirss.feature.backup.data.DefaultBackupRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkArticleGateway
import dev.terashima.yomitorirss.feature.bookmark.BookmarkContentQuery
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEnrichmentRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.SaveSharedBookmarkUseCase
import dev.terashima.yomitorirss.feature.bookmark.data.DefaultBookmarkContentQuery
import dev.terashima.yomitorirss.feature.bookmark.data.DefaultBookmarkEnrichmentRepository
import dev.terashima.yomitorirss.feature.bookmark.data.DefaultBookmarkImportRepository
import dev.terashima.yomitorirss.feature.bookmark.data.DefaultBookmarkRepository
import dev.terashima.yomitorirss.feature.chat.ChatGenerator
import dev.terashima.yomitorirss.feature.chat.ChatRepository
import dev.terashima.yomitorirss.feature.chat.data.DefaultChatRepository
import dev.terashima.yomitorirss.feature.chat.data.LocalChatGenerator
import dev.terashima.yomitorirss.feature.chat.data.createAppResourceSkills
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuilder
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageCreator
import dev.terashima.yomitorirss.feature.knowledge.KnowledgePageEditor
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.data.DefaultKnowledgeGenerationService
import dev.terashima.yomitorirss.feature.knowledge.data.DefaultKnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.data.ManagingKnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.data.SqlKnowledgePageStore
import dev.terashima.yomitorirss.feature.knowledge.data.WorkManagerKnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.WorkManagerLibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.mail.MailRepository
import dev.terashima.yomitorirss.feature.mail.data.DefaultMailRepository
import dev.terashima.yomitorirss.feature.mail.data.GmailAuthorizationManager
import dev.terashima.yomitorirss.feature.reddit.RedditRepository
import dev.terashima.yomitorirss.feature.reddit.data.DefaultRedditRepository
import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl
import dev.terashima.yomitorirss.feature.rss.FeedImportRepository
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.rss.RefreshFeedsUseCase
import dev.terashima.yomitorirss.feature.rss.data.DefaultFeedImportRepository
import dev.terashima.yomitorirss.feature.rss.data.DefaultFeedRepository
import dev.terashima.yomitorirss.feature.rss.data.RssContentClassificationSourceQuery
import dev.terashima.yomitorirss.feature.settings.AiModelRepository
import dev.terashima.yomitorirss.feature.settings.data.DefaultAiModelRepository
import dev.terashima.yomitorirss.feature.summary.BackfillBookmarkAutoEnrichmentUseCase
import dev.terashima.yomitorirss.feature.summary.BookmarkAutoEnrichmentUseCase
import dev.terashima.yomitorirss.feature.summary.SummaryRepository
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository
import dev.terashima.yomitorirss.feature.summary.data.DefaultSummaryRepository
import dev.terashima.yomitorirss.feature.summary.data.DefaultSummaryTaskQueueRepository
import dev.terashima.yomitorirss.feature.summary.data.SummaryContentRetentionProtectionQuery
import dev.terashima.yomitorirss.feature.task.TaskRepository
import dev.terashima.yomitorirss.feature.task.data.DefaultTaskRepository
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import dev.terashima.yomitorirss.feature.widget.data.DefaultWidgetRepository
import dev.terashima.yomitorirss.feature.workout.WorkoutRepository
import dev.terashima.yomitorirss.feature.workout.data.DefaultWorkoutRepository
import dev.terashima.yomitorirss.feature.youtube.YouTubeRepository
import dev.terashima.yomitorirss.feature.youtube.data.DefaultYouTubeRepository

class AppContainer(private val application: Application) {
  private val dataChanges = DataChangeNotifier.shared

  val database: YomitoriDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    YomitoriDatabase.create(application)
  }
  internal val databaseConnection: DatabaseConnection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DatabaseConnection(database)
  }
  val bookmarkContentQuery: BookmarkContentQuery by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBookmarkContentQuery(databaseConnection)
  }
  private val bookmarkArticleGateway: BookmarkArticleGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBookmarkArticleGateway(databaseConnection)
  }
  private val contentClassificationSourceQuery by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    RssContentClassificationSourceQuery(databaseConnection)
  }
  private val contentRetentionProtectionQuery by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    CompositeContentRetentionProtectionQuery(
      listOf(
        ContentRetentionProtectionQuery(bookmarkContentQuery::bookmarkedContentIds),
        SummaryContentRetentionProtectionQuery(databaseConnection),
      ),
    )
  }

  val articleRepository: ArticleRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultArticleRepository(
      database = databaseConnection,
      contentClassificationSourceQuery = contentClassificationSourceQuery,
      contentRetentionProtectionQuery = contentRetentionProtectionQuery,
      dataChanges = dataChanges,
    )
  }
  private val contentSourceGateway: ContentSourceGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultContentSourceGateway(databaseConnection, bookmarkContentQuery)
  }
  val assetRepository: AssetRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultAssetRepository(application, databaseConnection)
  }
  private val bookmarkAutoEnrichmentUseCase: BookmarkAutoEnrichmentUseCase by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    BookmarkAutoEnrichmentUseCase(
      articleRepository = articleRepository,
      enrichmentRequester = summaryRepository,
    )
  }
  val bookmarkRepository: BookmarkRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBookmarkRepository(
      database = databaseConnection,
      articleRepository = articleRepository,
      articleGateway = bookmarkArticleGateway,
      dataChanges = dataChanges,
      onBookmarkAdded = bookmarkAutoEnrichmentUseCase::invoke,
    )
  }
  val bookmarkEnrichmentRepository: BookmarkEnrichmentRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBookmarkEnrichmentRepository(databaseConnection, dataChanges)
  }
  val bookmarkImportRepository: BookmarkImportRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBookmarkImportRepository(
      context = application,
      database = databaseConnection,
      articleGateway = bookmarkArticleGateway,
      dataChanges = dataChanges,
    )
  }
  val feedRepository: FeedRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultFeedRepository(
      database = databaseConnection,
      contentSourceGateway = contentSourceGateway,
      dataChanges = dataChanges,
      applicationContext = application,
    )
  }
  val redditRepository: RedditRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultRedditRepository(feedRepository)
  }
  val youtubeRepository: YouTubeRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultYouTubeRepository(databaseConnection)
  }
  val feedImportRepository: FeedImportRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultFeedImportRepository(
      context = application,
      database = databaseConnection,
      contentSourceGateway = contentSourceGateway,
      dataChanges = dataChanges,
    )
  }
  val refreshFeedsUseCase: RefreshFeedsUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    RefreshFeedsUseCase(feedRepository)
  }
  val backupChangeScheduler: BackupChangeScheduler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AndroidBackupChangeScheduler(application)
  }
  val saveSharedBookmarkUseCase: SaveSharedBookmarkUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SaveSharedBookmarkUseCase(
      saver = bookmarkRepository,
      onBookmarkChanged = backupChangeScheduler::scheduleAfterChange,
    )
  }
  val widgetRepository: WidgetRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultWidgetRepository(
      articleRepository = articleRepository,
      feedRepository = feedRepository,
      bookmarkRepository = bookmarkRepository,
      backupChangeScheduler = backupChangeScheduler,
      sourceSelector = { feedUrl -> !isRedditFeedUrl(feedUrl) },
    )
  }
  val modelManager: LocalModelManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    LocalModelManager.shared(application)
  }
  val aiModelRepository: AiModelRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultAiModelRepository(application, modelManager)
  }
  val chatRepository: ChatRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultChatRepository(databaseConnection)
  }
  val taskRepository: TaskRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultTaskRepository(databaseConnection)
  }
  val workoutRepository: WorkoutRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultWorkoutRepository(application)
  }
  val gmailAuthorizationManager: GmailAuthorizationManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    GmailAuthorizationManager(application)
  }
  val mailRepository: MailRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultMailRepository(
      context = application,
      database = databaseConnection,
      authorization = gmailAuthorizationManager,
    )
  }
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
  val backupRepository: BackupRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBackupRepository(application, database, dataChanges)
  }
  val summaryRepository: SummaryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultSummaryRepository(application, database, modelManager)
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
  private val knowledgePageStore: SqlKnowledgePageStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SqlKnowledgePageStore(databaseConnection, dataChanges)
  }
  private val knowledgeReader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultKnowledgeRepository(knowledgePageStore)
  }
  val knowledgeRepository: KnowledgeRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    ManagingKnowledgeRepository(
      delegate = knowledgeReader,
      database = databaseConnection,
      dataChanges = dataChanges,
    )
  }
  private val knowledgeGenerationService by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultKnowledgeGenerationService(
      store = knowledgePageStore,
      bookmarks = bookmarkRepository,
      summaries = summaryRepository,
      modelManager = modelManager,
    )
  }
  val knowledgeBuilder: KnowledgeBuilder get() = knowledgeGenerationService
  val knowledgePageCreator: KnowledgePageCreator get() = knowledgeGenerationService
  val knowledgePageEditor: KnowledgePageEditor get() = knowledgeGenerationService

  val aiTaskQueueRepository: AiTaskQueueRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    CompositeAiTaskQueueRepository(
      summaryRepository = summaryTaskQueueRepository,
      libraryRepository = DefaultLibraryOrganizationRepository(databaseConnection),
      libraryCatalogRepository = DefaultLibraryRepository(databaseConnection),
      libraryScheduler = WorkManagerLibraryOrganizationBatchScheduler(application),
      knowledgeController = WorkManagerKnowledgeBuildTaskController(application),
    )
  }
}
