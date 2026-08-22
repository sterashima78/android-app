package dev.terashima.yomitorirss

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import dev.terashima.yomitorirss.core.background.BackgroundDataFetchPreferences
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.asset.AssetViewModel
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.bookreader.BookPageSourceFactory
import dev.terashima.yomitorirss.feature.bookreader.ReadingPositionStore
import dev.terashima.yomitorirss.feature.bookreader.data.DefaultBookPageSourceFactory
import dev.terashima.yomitorirss.feature.bookreader.data.SharedPreferencesReadingPositionStore
import dev.terashima.yomitorirss.feature.calendar.CalendarViewModel
import dev.terashima.yomitorirss.feature.chat.ChatViewModel
import dev.terashima.yomitorirss.feature.health.HealthViewModel
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeViewModel
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationViewModel
import dev.terashima.yomitorirss.feature.library.LibraryViewModel
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationManager
import dev.terashima.yomitorirss.feature.library.data.GoogleBooksAuthorizationOutcome
import dev.terashima.yomitorirss.feature.library.data.LocalLibraryOrganizationSuggester
import dev.terashima.yomitorirss.feature.library.data.SharedPreferencesSmbMetadataNormalizationPromptRepository
import dev.terashima.yomitorirss.feature.library.data.WorkManagerSmbCoverPrefetchScheduler
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.mail.data.GmailAuthorizationManager
import dev.terashima.yomitorirss.feature.mail.data.MailSyncScheduler
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
  private val application: Application,
  container: AppContainer,
) {
  private val backgroundDataFetchPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    BackgroundDataFetchPreferences(application)
  }

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
    val authorizationManager = GoogleBooksAuthorizationManager(application)
    val smbCoverPrefetchScheduler = WorkManagerSmbCoverPrefetchScheduler(application)
    val smbMetadataNormalizationPromptRepository =
      SharedPreferencesSmbMetadataNormalizationPromptRepository(application)
    LibraryRouteDependencies(
      authorization = LibraryAuthorizationDependencies(
        requestAccount = {
          when (val outcome = authorizationManager.requestAccount()) {
            is GoogleBooksAuthorizationOutcome.Authorized -> LibraryAuthorizationOutcome.Authorized(
              LibraryAuthorizedAccount(
                accessToken = outcome.account.accessToken,
                accountLabel = outcome.account.accountLabel,
              ),
            )
            is GoogleBooksAuthorizationOutcome.RequiresResolution ->
              LibraryAuthorizationOutcome.RequiresResolution(outcome.pendingIntent)
          }
        },
        resultFromIntent = { data ->
          authorizationManager.resultFromIntent(data).let { account ->
            LibraryAuthorizedAccount(
              accessToken = account.accessToken,
              accountLabel = account.accountLabel,
            )
          }
        },
      ),
      libraryViewModelFactory = LibraryViewModel.Factory(
        repository = runtime.catalogRepository,
        smbRepository = runtime.smbRepository,
        smbCoverPrefetchScheduler = smbCoverPrefetchScheduler,
        smbMetadataNormalizationRepository = runtime.smbMetadataNormalizationRepository,
        smbMetadataNormalizationScheduler = runtime.smbMetadataNormalizationScheduler,
        smbMetadataNormalizationPromptRepository = smbMetadataNormalizationPromptRepository,
      ),
      organizationViewModelFactory = LibraryOrganizationViewModel.Factory(
        repository = runtime.organizationRepository,
        suggester = LocalLibraryOrganizationSuggester(container.modelManager),
        batchScheduler = runtime.organizationBatchScheduler,
      ),
      smbRepository = runtime.smbRepository,
      bookReader = BookReaderRouteDependencies(
        pageSourceFactory = DefaultBookPageSourceFactory(),
        readingPositionStore = SharedPreferencesReadingPositionStore(application),
      ),
    )
  }

  val taskViewModelFactory: TaskViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    TaskViewModel.Factory(container.taskRepository)
  }

  val calendarViewModelFactory: CalendarViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    CalendarViewModel.Factory(container.calendarRepository)
  }

  val updateTaskWidget: () -> Unit = {
    TaskWidgetUpdater.updateAll(application)
  }

  val workoutViewModelFactory: WorkoutViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    WorkoutViewModel.Factory(
      repository = container.workoutRepository,
      historyExporter = WorkoutHealthConnectExporter(container.featureRuntimeDependencies.healthRepository),
    )
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

  fun backgroundFetchWifiOnly(): Boolean = backgroundDataFetchPreferences.wifiOnly

  fun setBackgroundFetchWifiOnly(wifiOnly: Boolean) {
    backgroundDataFetchPreferences.wifiOnly = wifiOnly
    MailSyncScheduler(application).refreshPeriodicNetworkPolicy()
  }
}

data class HealthRouteDependencies internal constructor(
  val viewModelFactory: HealthViewModel.Factory,
  val readPermissions: Set<String>,
)

data class LibraryRouteDependencies internal constructor(
  val authorization: LibraryAuthorizationDependencies,
  val libraryViewModelFactory: LibraryViewModel.Factory,
  val organizationViewModelFactory: LibraryOrganizationViewModel.Factory,
  val smbRepository: SmbLibraryRepository,
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
