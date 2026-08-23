package dev.terashima.yomitorirss

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
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
import dev.terashima.yomitorirss.feature.mail.data.GmailAuthorizationOutcome
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.reddit.isRedditArticle
import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl
import dev.terashima.yomitorirss.feature.reddit.redditCommunityFeedUrl
import dev.terashima.yomitorirss.feature.reddit.redditThreadId
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
      delegate = container.featureRuntimeDependencies.library.webLibraryMutator,
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

  val mailAuthorization: MailAuthorizationDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val authorizationManager = container.gmailAuthorizationManager
    MailAuthorizationDependencies(
      requestAccount = {
        when (val outcome = authorizationManager.requestAccount()) {
          is GmailAuthorizationOutcome.Authorized -> MailAuthorizationOutcome.Authorized(
            MailAuthorizedAccount(
              email = outcome.account.email,
              displayName = outcome.account.displayName,
              accessToken = outcome.account.accessToken,
            ),
          )
          is GmailAuthorizationOutcome.RequiresResolution ->
            MailAuthorizationOutcome.RequiresResolution(outcome.pendingIntent)
        }
      },
      resultFromIntent = { data ->
        authorizationManager.resultFromIntent(data).let { account ->
          MailAuthorizedAccount(
            email = account.email,
            displayName = account.displayName,
            accessToken = account.accessToken,
          )
        }
      },
    )
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

  val health: HealthRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val repository = container.featureRuntimeDependencies.healthRepository
    HealthRouteDependencies(
      viewModelFactory = HealthViewModel.Factory(repository),
      readPermissions = repository.requestPermissions(),
    )
  }

  val knowledgeViewModelFactory: KnowledgeViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val buildScheduler = container.featureRuntimeDependencies.knowledgeBuildScheduler
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
    val runtime = container.featureRuntimeDependencies.library
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
      historyExporter = WorkoutHealthConnectExporter(container.featureRuntimeDependencies.healthRepository),
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

data class MailAuthorizationDependencies internal constructor(
  val requestAccount: suspend () -> MailAuthorizationOutcome,
  val resultFromIntent: suspend (Intent) -> MailAuthorizedAccount,
)

data class MailAuthorizedAccount internal constructor(
  val email: String,
  val displayName: String?,
  val accessToken: String,
)

sealed interface MailAuthorizationOutcome {
  data class Authorized(val account: MailAuthorizedAccount) : MailAuthorizationOutcome
  data class RequiresResolution(val pendingIntent: PendingIntent) : MailAuthorizationOutcome
}

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
  val removeWebBook: suspend (LibraryBook) -> Unit,
  val moveWebBookToBookmark: suspend (LibraryBook) -> Unit,
  val bookReader: BookReaderRouteDependencies,
)

data class LibraryAuthorizationDependencies internal constructor(
  val requestAccount: suspend () -> LibraryAuthorizationOutcome,
  val resultFromIntent: (Intent) -> LibraryAuthorizedAccount,
)

data class LibraryAuthorizedAccount internal constructor(
  val accessToken: String,
  val accountLabel: String?,
)

sealed interface LibraryAuthorizationOutcome {
  data class Authorized(val account: LibraryAuthorizedAccount) : LibraryAuthorizationOutcome
  data class RequiresResolution(val pendingIntent: PendingIntent) : LibraryAuthorizationOutcome
}

data class BookReaderRouteDependencies internal constructor(
  val pageSourceFactory: BookPageSourceFactory,
  val readingPositionStore: ReadingPositionStore,
)