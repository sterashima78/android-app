package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.data.DefaultArticleRepository
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
    DefaultYouTubeRepository(application)
  }
  val feedImportRepository: FeedImportRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultFeedImportRepository(application, databaseConnection, dataChanges)
  }
  val summaryTaskQueueRepository: SummaryTaskQueueRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultSummaryTaskQueueRepository(databaseConnection, dataChanges)
  }
  val summaryRepository: SummaryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultSummaryRepository(
      databaseConnection,
      summaryTaskQueueRepository,
      dataChanges,
    )
  }
  val chatGenerator: ChatGenerator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    LocalChatGenerator(
      application = application,
      skills = createAppResourceSkills(
        articleRepository = articleRepository,
        bookmarkRepository = bookmarkRepository,
        feedRepository = feedRepository,
        taskRepository = taskRepository,
        knowledgeRepository = knowledgeRepository,
        libraryRepository = libraryRepository,
      ),
    )
  }
  val chatRepository: ChatRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultChatRepository(databaseConnection, chatGenerator, dataChanges)
  }
  val knowledgeRepository: KnowledgeRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    ManagingKnowledgeRepository(
      delegate = DefaultKnowledgeRepository(databaseConnection, dataChanges),
      taskQueue = summaryTaskQueueRepository,
    )
  }
  val mailRepository: MailRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultMailRepository(
      application = application,
      database = databaseConnection,
      dataChanges = dataChanges,
    )
  }
  val aiModelRepository: AiModelRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultAiModelRepository(application, databaseConnection, LocalModelManager(application))
  }
  val taskRepository: TaskRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultTaskRepository(databaseConnection, dataChanges)
  }
  val widgetRepository: WidgetRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultWidgetRepository(databaseConnection, dataChanges)
  }
  val workoutRepository: WorkoutRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultWorkoutRepository(databaseConnection, dataChanges)
  }
  val backupChangeScheduler: BackupChangeScheduler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AndroidBackupChangeScheduler(application)
  }
  val backupRepository: BackupRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBackupRepository(application, databaseConnection, dataChanges, backupChangeScheduler)
  }
  val refreshFeedsUseCase: RefreshFeedsUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    RefreshFeedsUseCase(feedRepository)
  }
  val gmailAuthorizationManager: GmailAuthorizationManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    GmailAuthorizationManager(application)
  }
}

private fun shouldRequestBookmarkEnrichment(
  url: String,
  sourceFeedUrl: String?,
  contentType: dev.terashima.yomitorirss.feature.article.ContentType,
): Boolean {
  if (!contentType.allowsAutomaticAiEnrichment()) return false
  if (sourceFeedUrl?.let(::isRedditFeedUrl) == true) return false
  val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
  if (host == "youtube.com" || host == "www.youtube.com" || host == "youtu.be") return false
  return true
}
