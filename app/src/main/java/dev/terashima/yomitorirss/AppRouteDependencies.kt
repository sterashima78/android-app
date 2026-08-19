package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.asset.AssetViewModel
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.chat.ChatViewModel
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeViewModel
import dev.terashima.yomitorirss.feature.knowledge.data.WorkManagerKnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationViewModel
import dev.terashima.yomitorirss.feature.library.LibraryViewModel
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.CleaningSmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.DefaultLibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.LocalLibraryOrganizationSuggester
import dev.terashima.yomitorirss.feature.library.data.SeriesAwareLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.WorkManagerLibraryOrganizationBatchScheduler
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.mail.data.GmailAuthorizationManager
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.reddit.isRedditArticle
import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl
import dev.terashima.yomitorirss.feature.reddit.redditCommunityFeedUrl
import dev.terashima.yomitorirss.feature.reddit.redditThreadId
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel
import dev.terashima.yomitorirss.feature.task.TaskViewModel
import dev.terashima.yomitorirss.feature.widget.TaskWidgetUpdater
import dev.terashima.yomitorirss.feature.workout.WorkoutViewModel
import dev.terashima.yomitorirss.feature.x.XViewerCssRepository
import dev.terashima.yomitorirss.feature.x.data.SharedPreferencesXViewerCssRepository
import dev.terashima.yomitorirss.feature.youtube.YouTubeViewModel

class AppRouteDependencies internal constructor(
  application: Application,
  container: AppContainer,
) {
  val rssViewModelFactory: RssViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    RssViewModel.Factory(
      articleRepository = container.articleRepository,
      bookmarkRepository = container.bookmarkRepository,
      backupChangeScheduler = container.backupChangeScheduler,
      summaryRepository = container.summaryRepository,
      articleSelector = { article -> !article.isRedditArticle() },
    )
  }

  val redditViewModelFactory: RedditViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    RedditViewModel.Factory(
      redditRepository = container.redditRepository,
      articleRepository = container.articleRepository,
      bookmarkRepository = container.bookmarkRepository,
      backupChangeScheduler = container.backupChangeScheduler,
    )
  }

  val feedViewModelFactory: FeedViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    FeedViewModel.Factory(
      repository = container.feedRepository,
      refreshFeeds = container.refreshFeedsUseCase,
      imports = container.feedImportRepository,
      backupChangeScheduler = container.backupChangeScheduler,
      feedSelector = { feed -> !isRedditFeedUrl(feed.feedUrl) },
      canAddInput = { input ->
        redditCommunityFeedUrl(input) == null &&
          redditThreadId(input) == null &&
          !isRedditFeedUrl(input)
      },
    )
  }

  val bookmarkViewModelFactory: BookmarkViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    BookmarkViewModel.Factory(
      articleRepository = container.articleRepository,
      bookmarkRepository = container.bookmarkRepository,
      imports = container.bookmarkImportRepository,
      backupChangeScheduler = container.backupChangeScheduler,
    )
  }

  val mailViewModelFactory: MailViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    MailViewModel.Factory(container.mailRepository)
  }

  val mailAuthorization: GmailAuthorizationManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    container.gmailAuthorizationManager
  }

  val summaryViewModelFactory: SummaryViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SummaryViewModel.Factory(container.summaryRepository)
  }

  val backupViewModelFactory: BackupViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    BackupViewModel.Factory(container.backupRepository)
  }

  val aiSettingsViewModelFactory: AiSettingsViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AiSettingsViewModel.Factory(container.aiModelRepository)
  }

  val aiTaskQueueRepository: AiTaskQueueRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    container.aiTaskQueueRepository
  }

  val chatViewModelFactory: ChatViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    ChatViewModel.Factory(container.chatRepository, container.chatGenerator)
  }

  val assetViewModelFactory: AssetViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AssetViewModel.Factory(
      repository = container.assetRepository,
      onChanged = container.backupChangeScheduler::scheduleAfterChange,
    )
  }

  val knowledgeViewModelFactory: KnowledgeViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val buildTaskController = WorkManagerKnowledgeBuildTaskController(application)
    KnowledgeViewModel.Factory(
      repository = container.knowledgeRepository,
      buildKnowledge = container.buildKnowledgeUseCase,
      createKnowledgePage = container.createKnowledgePageUseCase,
      editKnowledgePage = container.editKnowledgePageUseCase,
      scheduleBackupAfterChange = container.backupChangeScheduler::scheduleAfterChange,
      scheduleRebuild = buildTaskController::enqueue,
    )
  }

  val library: LibraryRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val database = container.databaseConnection
    val smbRepository = CleaningSmbLibraryRepository(application, database)
    LibraryRouteDependencies(
      authorization = GoogleBooksAuthorizationManager(application),
      libraryViewModelFactory = LibraryViewModel.Factory(
        repository = SeriesAwareLibraryRepository(database),
        smbRepository = smbRepository,
      ),
      organizationViewModelFactory = LibraryOrganizationViewModel.Factory(
        repository = DefaultLibraryOrganizationRepository(database),
        suggester = LocalLibraryOrganizationSuggester(container.modelManager),
        batchScheduler = WorkManagerLibraryOrganizationBatchScheduler(application),
      ),
      smbRepository = smbRepository,
    )
  }

  val taskViewModelFactory: TaskViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    TaskViewModel.Factory(container.taskRepository)
  }

  val updateTaskWidget: () -> Unit = {
    TaskWidgetUpdater.updateAll(application)
  }

  val workoutViewModelFactory: WorkoutViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    WorkoutViewModel.Factory(container.workoutRepository)
  }

  val xViewerCssRepository: XViewerCssRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SharedPreferencesXViewerCssRepository(application)
  }

  val youtubeViewModelFactory: YouTubeViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    YouTubeViewModel.Factory(
      repository = container.youtubeRepository,
      bookmarkRepository = container.bookmarkRepository,
      backupChangeScheduler = container.backupChangeScheduler,
    )
  }
}

data class LibraryRouteDependencies internal constructor(
  val authorization: GoogleBooksAuthorizationManager,
  val libraryViewModelFactory: LibraryViewModel.Factory,
  val organizationViewModelFactory: LibraryOrganizationViewModel.Factory,
  val smbRepository: SmbLibraryRepository,
)
