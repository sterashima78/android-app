package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.bookreader.BookPageSourceFactory
import dev.terashima.yomitorirss.feature.bookreader.ReadingPositionStore
import dev.terashima.yomitorirss.feature.chat.ChatViewModel
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeViewModel
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationViewModel
import dev.terashima.yomitorirss.feature.library.LibraryViewModel
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractor
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorTestResult
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataRefreshResult
import dev.terashima.yomitorirss.feature.library.WebLibraryMutator
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.reddit.RedditSourceBoundary
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel
import dev.terashima.yomitorirss.feature.youtube.YouTubeViewModel

internal class AppContentRouteDependencies(
  private val container: AppContainer,
) {
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

  val reprocessBookmarkEnrichment: suspend () -> Int = {
    container.reprocessBookmarkAutoEnrichmentUseCase()
  }

  val mailViewModelFactory: MailViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    MailViewModel.Factory(container.mailRepository)
  }

  val mailAuthorization: MailAuthorizationDependencies
    get() = container.mailAuthorization

  val summaryViewModelFactory: SummaryViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SummaryViewModel.Factory(container.summaryRepository)
  }

  val chatViewModelFactory: ChatViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    ChatViewModel.Factory(container.chatRepository, container.chatGenerator)
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
      refreshWebBook = webLibraryMutator::refreshWebBookWithReport,
      removeWebBook = webLibraryMutator::removeWebBook,
      moveWebBookToBookmark = libraryTransfers.moveWebBookToBookmark,
      listWebMetadataExtractors = runtime.webMetadataExtractorRepository::list,
      saveWebMetadataExtractor = { id, urlPattern, functionCode, timeoutSeconds ->
        runtime.webMetadataExtractorRepository.save(
          id = id,
          urlPattern = urlPattern,
          functionCode = functionCode,
          timeoutSeconds = timeoutSeconds,
        ).also {
          container.backupChangeScheduler.scheduleAfterChange()
        }
      },
      deleteWebMetadataExtractor = { id ->
        runtime.webMetadataExtractorRepository.delete(id)
        container.backupChangeScheduler.scheduleAfterChange()
      },
      testWebMetadataExtractor = runtime.webMetadataExtractorTester::test,
      bookReader = BookReaderRouteDependencies(
        pageSourceFactory = runtime.bookPageSourceFactory,
        readingPositionStore = runtime.readingPositionStore,
      ),
    )
  }

  val youtubeViewModelFactory: YouTubeViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    YouTubeViewModel.Factory(
      repository = container.youtubeRepository,
      bookmarkRepository = container.bookmarkRepository,
      backupChangeScheduler = container.backupChangeScheduler,
    )
  }
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
  val refreshWebBook: suspend (LibraryBook) -> WebLibraryMetadataRefreshResult,
  val removeWebBook: suspend (LibraryBook) -> Unit,
  val moveWebBookToBookmark: suspend (LibraryBook) -> Unit,
  val listWebMetadataExtractors: () -> List<WebLibraryMetadataExtractor>,
  val saveWebMetadataExtractor: (String?, String, String, Int) -> WebLibraryMetadataExtractor,
  val deleteWebMetadataExtractor: (String) -> Unit,
  val testWebMetadataExtractor: suspend (String, String, String, Int) -> WebLibraryMetadataExtractorTestResult,
  val bookReader: BookReaderRouteDependencies,
)

data class BookReaderRouteDependencies internal constructor(
  val pageSourceFactory: BookPageSourceFactory,
  val readingPositionStore: ReadingPositionStore,
)
