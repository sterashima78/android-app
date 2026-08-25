package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.background.BackgroundDataFetchPreferences
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.asset.AssetViewModel
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.bookreader.BookPageSourceFactory
import dev.terashima.yomitorirss.feature.bookreader.ReadingPositionStore
import dev.terashima.yomitorirss.feature.calendar.CalendarViewModel
import dev.terashima.yomitorirss.feature.chat.ChatViewModel
import dev.terashima.yomitorirss.feature.health.HealthViewModel
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeViewModel
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationViewModel
import dev.terashima.yomitorirss.feature.library.LibraryViewModel
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.WebLibraryMutator
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.reddit.RedditSourceBoundary
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel
import dev.terashima.yomitorirss.feature.task.TaskChangeNotifyingRepository
import dev.terashima.yomitorirss.feature.task.TaskViewModel
import dev.terashima.yomitorirss.feature.widget.TaskWidgetUpdater
import dev.terashima.yomitorirss.feature.workout.WorkoutViewModel
import dev.terashima.yomitorirss.feature.x.XViewerCssRepository
import dev.terashima.yomitorirss.feature.youtube.YouTubeViewModel

class AppRouteDependencies internal constructor(
  private val application: Application,
  private val container: AppContainer,
) {
  private val backgroundDataFetchPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    BackgroundDataFetchPreferences(application)
  }
  private val webLibraryMutator: WebLibraryMutator by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    NotifyingWebLibraryMutator(
      delegate = container.libraryRuntime.webLibraryMutator,
      onChanged = container.backupChangeScheduler::scheduleAfterChange,
    )
  }
  val libraryTransfers: LibraryTransferDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val service = BookmarkLibraryTransferService(
      webLibrary = webLibraryMutator,
      bookmarkMutator = container.bookmarkRepository,
      saveSharedBookmark = container.saveSharedBookmarkUseCase,
      onChanged = container.backupChangeScheduler::scheduleAfterChange,
    )
    LibraryTransferDependencies(
      moveBookmarkToLibrary = service::moveBookmarkToLibrary,
      moveWebBookToBookmark = service::moveWebBookToBookmark,
    )
  }

  val rssViewModelFactory: RssViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    RssViewModel.Factory(
      articleRepository = container.articleRepository,
      bookmarkRepository = container.bookmarkRepository,
      backupChangeScheduler = container.backupChangeScheduler,
      articleSelector = RedditSourceBoundary::isNonRedditArticle,
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
      feedSelector = { feed -> RedditSourceBoundary.isNonRedditFeed(feed.feedUrl) },
      canAddInput = RedditSourceBoundary::isRssSubscriptionInput,
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

  val mailAuthorization: MailAuthorizationDependencies
    get() = container.mailAuthorization

  val summaryViewModelFactory: SummaryViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SummaryViewModel.Factory(container.summaryRepository)
  }

  val backupViewModelFactory: BackupViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    BackupViewModel.Factory(container.backupRepository)
  }

  val aiSettingsViewModelFactory: AiSettingsViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    AiSettingsViewModel.Factory(
      repository = container.aiModelRepository,
      summaryPromptSettings = container.summaryPromptSettings,
    )
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

  val health: HealthRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val repository = container.healthRepository
    HealthRouteDependencies(
      viewModelFactory = HealthViewModel.Factory(repository),
      readPermissions = repository.requestPermissions(),
    )
  }

  val knowledgeViewModelFactory: KnowledgeViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val buildScheduler = container.knowledgeBuildScheduler
    KnowledgeViewModel.Factory(
      repository = container.knowledgeRepository,
      builder = container.knowledgeBuilder,
      creator = container.knowledgePageCreator,
      editor = container.knowledgePageEditor,
      scheduleBackupAfterChange = container.backupChangeScheduler::scheduleAfterChange,
      scheduleRebuild = buildScheduler::enqueue,
    )
  }

  val library: LibraryRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val runtime = container.libraryRuntime
    LibraryRouteDependencies(
      authorization = runtime.authorization,
      libraryViewModelFactory = LibraryViewModel.Factory(
        repository = runtime.catalogRepository,
        smbRepository = runtime.smbRepository,
        smbCoverPrefetchScheduler = runtime.smbCoverPrefetchScheduler,
        smbMetadataNormalizationRepository = runtime.smbMetadataNormalizationRepository,
        smbMetadataNormalizationScheduler = runtime.smbMetadataNormalizationScheduler,
        smbMetadataNormalizationPromptRepository = runtime.smbMetadataNormalizationPromptRepository,
      ),
      organizationViewModelFactory = LibraryOrganizationViewModel.Factory(
        repository = runtime.organizationRepository,
        suggester = runtime.organizationSuggester,
        batchScheduler = runtime.organizationBatchScheduler,
      ),
      smbRepository = runtime.smbRepository,
      addWebBook = { url, titleHint -> webLibraryMutator.addWebBook(url, titleHint) },
      refreshWebBook = webLibraryMutator::refreshWebBook,
      removeWebBook = webLibraryMutator::removeWebBook,
      moveWebBookToBookmark = libraryTransfers.moveWebBookToBookmark,
      bookReader = BookReaderRouteDependencies(
        pageSourceFactory = runtime.bookPageSourceFactory,
        readingPositionStore = runtime.readingPositionStore,
      ),
    )
  }

  val taskViewModelFactory: TaskViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val repository = TaskChangeNotifyingRepository(container.taskRepository) {
      runCatching { TaskWidgetUpdater.updateAll(application) }
    }
    TaskViewModel.Factory(repository)
  }

  val calendarViewModelFactory: CalendarViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    CalendarViewModel.Factory(container.calendarRepository)
  }

  val workoutViewModelFactory: WorkoutViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    WorkoutViewModel.Factory(
      repository = container.workoutRepository,
      historyExporter = WorkoutHealthConnectExporter(container.healthRepository),
    )
  }

  val xViewerCssRepository: XViewerCssRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    container.xViewerCssRepository
  }

  val youtubeViewModelFactory: YouTubeViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    YouTubeViewModel.Factory(
      repository = container.youtubeRepository,
      bookmarkRepository = container.bookmarkRepository,
      backupChangeScheduler = container.backupChangeScheduler,
    )
  }

  fun backgroundFetchWifiOnly(): Boolean = backgroundDataFetchPreferences.wifiOnly

  fun setBackgroundFetchWifiOnly(wifiOnly: Boolean) {
    backgroundDataFetchPreferences.wifiOnly = wifiOnly
    container.mailRepository.refreshPeriodicSyncPolicy()
  }
}

data class HealthRouteDependencies internal constructor(
  val viewModelFactory: HealthViewModel.Factory,
  val readPermissions: Set<String>,
)

data class LibraryTransferDependencies internal constructor(
  val moveBookmarkToLibrary: suspend (Article) -> Unit,
  val moveWebBookToBookmark: suspend (LibraryBook) -> Unit,
)

data class LibraryRouteDependencies internal constructor(
  val authorization: LibraryAuthorizationDependencies,
  val libraryViewModelFactory: LibraryViewModel.Factory,
  val organizationViewModelFactory: LibraryOrganizationViewModel.Factory,
  val smbRepository: SmbLibraryRepository,
  val addWebBook: suspend (String, String?) -> LibraryBook,
  val refreshWebBook: suspend (LibraryBook) -> LibraryBook,
  val removeWebBook: suspend (LibraryBook) -> Unit,
  val moveWebBookToBookmark: suspend (LibraryBook) -> Unit,
  val bookReader: BookReaderRouteDependencies,
)

data class BookReaderRouteDependencies internal constructor(
  val pageSourceFactory: BookPageSourceFactory,
  val readingPositionStore: ReadingPositionStore,
)
