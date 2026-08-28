package dev.terashima.yomitorirss.composition.route

import dev.terashima.yomitorirss.AppContainer
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.bookmark.MoveBookmarkToLibraryUseCase
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
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.reddit.RedditSourceBoundary
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.summary.SummaryViewModel
import dev.terashima.yomitorirss.feature.youtube.YouTubeViewModel
import dev.terashima.yomitorirss.platform.authorization.LibraryAuthorizationDependencies
import dev.terashima.yomitorirss.platform.authorization.MailAuthorizationDependencies

internal class AppContentRouteDependencies(
  private val container: AppContainer,
) {
  val rssViewModelFactory: RssViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    RssViewModel.Factory(
      articleRepository = container.articleRepository,
      bookmarkRepository = container.bookmarkRepository,
      articleSelector = RedditSourceBoundary::isNonRedditArticle,
    )
  }

  val redditViewModelFactory: RedditViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    RedditViewModel.Factory(
      redditRepository = container.redditRepository,
      articleRepository = container.articleRepository,
      bookmarkRepository = container.bookmarkRepository,
    )
  }

  val feedViewModelFactory: FeedViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    FeedViewModel.Factory(
      repository = container.feedRepository,
      refreshFeeds = container.refreshFeedsUseCase,
      imports = container.feedImportRepository,
      feedSelector = { feed -> RedditSourceBoundary.isNonRedditFeed(feed.feedUrl) },
      canAddInput = RedditSourceBoundary::isRssSubscriptionInput,
    )
  }

  val bookmarkViewModelFactory: BookmarkViewModel.Factory by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    BookmarkViewModel.Factory(
      articleRepository = container.articleRepository,
      bookmarkRepository = container.bookmarkRepository,
      imports = container.bookmarkImportRepository,
      moveBookmarkToLibrary = MoveBookmarkToLibraryUseCase(
        webLibrary = container.libraryRuntime.webLibraryMutator,
        bookmarkMutator = container.bookmarkRepository,
      ),
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
      scheduleRebuild = buildScheduler::enqueue,
    )
  }

  val library: LibraryRouteDependencies by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    val runtime = container.libraryRuntime
    val webLibraryMutator = runtime.webLibraryMutator
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
      addWebBook = webLibraryMutator::addWebBook,
      refreshWebBook = webLibraryMutator::refreshWebBookWithReport,
      removeWebBook = webLibraryMutator::removeWebBook,
      listWebMetadataExtractors = runtime.webMetadataExtractorRepository::list,
      saveWebMetadataExtractor = runtime.webMetadataExtractorRepository::save,
      deleteWebMetadataExtractor = runtime.webMetadataExtractorRepository::delete,
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
    )
  }
}

data class LibraryRouteDependencies internal constructor(
  val authorization: LibraryAuthorizationDependencies,
  val libraryViewModelFactory: LibraryViewModel.Factory,
  val organizationViewModelFactory: LibraryOrganizationViewModel.Factory,
  val smbRepository: SmbLibraryRepository,
  val addWebBook: suspend (String, String?) -> LibraryBook,
  val refreshWebBook: suspend (LibraryBook) -> WebLibraryMetadataRefreshResult,
  val removeWebBook: suspend (LibraryBook) -> Unit,
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
