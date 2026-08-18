package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.data.DefaultArticleRepository
import dev.terashima.yomitorirss.feature.asset.AssetRepository
import dev.terashima.yomitorirss.feature.asset.data.DefaultAssetRepository
import dev.terashima.yomitorirss.feature.backup.BackupChangeScheduler
import dev.terashima.yomitorirss.feature.backup.BackupRepository
import dev.terashima.yomitorirss.feature.backup.data.AndroidBackupChangeScheduler
import dev.terashima.yomitorirss.feature.backup.data.DefaultBackupRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.data.BookmarkSourceMetadataReader
import dev.terashima.yomitorirss.feature.bookmark.data.DefaultBookmarkImportRepository
import dev.terashima.yomitorirss.feature.bookmark.data.DefaultBookmarkRepository
import dev.terashima.yomitorirss.feature.chat.ChatGenerator
import dev.terashima.yomitorirss.feature.chat.ChatRepository
import dev.terashima.yomitorirss.feature.chat.data.DefaultChatRepository
import dev.terashima.yomitorirss.feature.chat.data.LocalChatGenerator
import dev.terashima.yomitorirss.feature.chat.data.createAppResourceSkills
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.data.DefaultKnowledgeRepository
import dev.terashima.yomitorirss.feature.knowledge.data.ManagingKnowledgeRepository
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
import dev.terashima.yomitorirss.feature.settings.AiModelRepository
import dev.terashima.yomitorirss.feature.settings.data.DefaultAiModelRepository
import dev.terashima.yomitorirss.feature.summary.SummaryRepository
import dev.terashima.yomitorirss.feature.summary.SummaryTaskQueueRepository
import dev.terashima.yomitorirss.feature.summary.data.DefaultSummaryRepository
import dev.terashima.yomitorirss.feature.summary.data.DefaultSummaryTaskQueueRepository
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
  private val databaseConnection: DatabaseConnection by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DatabaseConnection(database)
  }
  private val bookmarkSourceMetadataReader: BookmarkSourceMetadataReader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    BookmarkSourceMetadataReader(databaseConnection)
  }

  val articleRepository: ArticleRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultArticleRepository(databaseConnection, dataChanges)
  }
  val assetRepository: AssetRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultAssetRepository(application, databaseConnection)
  }
  val bookmarkRepository: BookmarkRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBookmarkRepository(
      database = databaseConnection,
      dataChanges = dataChanges,
      onBookmarkAdded = { articleId ->
        val source = bookmarkSourceMetadataReader.find(articleId)
        if (
          source != null && shouldRequestBookmarkEnrichment(
            url = source.url,
            sourceFeedUrl = source.sourceFeedUrl,
            contentType = source.effectiveContentType,
          )
        ) {
          summaryRepository.requestBookmarkEnrichment(articleId)
        }
      },
    )
  }
  val bookmarkImportRepository: BookmarkImportRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBookmarkImportRepository(application, databaseConnection, dataChanges)
  }
  val feedRepository: FeedRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultFeedRepository(databaseConnection, dataChanges, application)
  }
  val redditRepository: RedditRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultRedditRepository(feedRepository)
  }
  val youtubeRepository: YouTubeRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultYouTubeRepository(databaseConnection)
  }
  val feedImportRepository: FeedImportRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultFeedImportRepository(application, databaseConnection, dataChanges)
  }
  val refreshFeedsUseCase: RefreshFeedsUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    RefreshFeedsUseCase(feedRepository)
  }
  val backupChangeScheduler: BackupChangeScheduler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AndroidBackupChangeScheduler(application)
  }
  val widgetRepository: WidgetRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultWidgetRepository(
      database = database,
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
  val summaryTaskQueueRepository: SummaryTaskQueueRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultSummaryTaskQueueRepository(application, database)
  }
  val knowledgeRepository: KnowledgeRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    ManagingKnowledgeRepository(
      delegate = DefaultKnowledgeRepository(
        database = databaseConnection,
        bookmarkRepository = bookmarkRepository,
        summaryRepository = summaryRepository,
        modelManager = modelManager,
      ),
      database = databaseConnection,
      dataChanges = dataChanges,
    )
  }
}
