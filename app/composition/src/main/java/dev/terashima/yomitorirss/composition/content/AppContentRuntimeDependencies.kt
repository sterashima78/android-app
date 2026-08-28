package dev.terashima.yomitorirss.composition.content

import android.app.Application
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.CompositeContentRetentionProtectionQuery
import dev.terashima.yomitorirss.feature.article.ContentRetentionProtectionQuery
import dev.terashima.yomitorirss.feature.article.ContentSourceGateway
import dev.terashima.yomitorirss.feature.article.data.DefaultArticleRepository
import dev.terashima.yomitorirss.feature.article.data.DefaultBookmarkArticleGateway
import dev.terashima.yomitorirss.feature.article.data.DefaultContentSourceGateway
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
import dev.terashima.yomitorirss.feature.reddit.RedditRepository
import dev.terashima.yomitorirss.feature.reddit.RedditSourceBoundary
import dev.terashima.yomitorirss.feature.reddit.data.DefaultRedditRepository
import dev.terashima.yomitorirss.feature.rss.FeedImportRepository
import dev.terashima.yomitorirss.feature.rss.FeedRepository
import dev.terashima.yomitorirss.feature.rss.RefreshFeedsUseCase
import dev.terashima.yomitorirss.feature.rss.data.DefaultFeedImportRepository
import dev.terashima.yomitorirss.feature.rss.data.DefaultFeedRepository
import dev.terashima.yomitorirss.feature.rss.data.RssContentClassificationSourceQuery
import dev.terashima.yomitorirss.feature.summary.BookmarkAutoEnrichmentUseCase
import dev.terashima.yomitorirss.feature.summary.SummaryRepository
import dev.terashima.yomitorirss.feature.summary.data.SummaryContentRetentionProtectionQuery
import dev.terashima.yomitorirss.feature.widget.WidgetRepository
import dev.terashima.yomitorirss.feature.widget.data.DefaultWidgetRepository
import dev.terashima.yomitorirss.feature.youtube.YouTubeRepository
import dev.terashima.yomitorirss.feature.youtube.data.DefaultYouTubeRepository

/** Content/Curation ingestion graph kept at application scope. */
internal class AppContentRuntimeDependencies(
  private val application: Application,
  private val database: DatabaseConnection,
  private val dataChanges: DataChangeNotifier,
  private val httpClient: HttpClient,
  private val summaryRepository: SummaryRepository,
) {
  val bookmarkContentQuery: BookmarkContentQuery by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBookmarkContentQuery(database)
  }

  private val bookmarkArticleGateway: BookmarkArticleGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBookmarkArticleGateway(database)
  }

  private val contentClassificationSourceQuery by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    RssContentClassificationSourceQuery(database)
  }

  private val contentRetentionProtectionQuery by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    CompositeContentRetentionProtectionQuery(
      listOf(
        ContentRetentionProtectionQuery(bookmarkContentQuery::bookmarkedContentIds),
        SummaryContentRetentionProtectionQuery(database),
      ),
    )
  }

  val articleRepository: ArticleRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultArticleRepository(
      database = database,
      contentClassificationSourceQuery = contentClassificationSourceQuery,
      contentRetentionProtectionQuery = contentRetentionProtectionQuery,
      dataChanges = dataChanges,
    )
  }

  private val contentSourceGateway: ContentSourceGateway by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultContentSourceGateway(database, bookmarkContentQuery)
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
      database = database,
      articleRepository = articleRepository,
      articleGateway = bookmarkArticleGateway,
      dataChanges = dataChanges,
      onBookmarkAdded = bookmarkAutoEnrichmentUseCase::invoke,
    )
  }

  val bookmarkEnrichmentRepository: BookmarkEnrichmentRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBookmarkEnrichmentRepository(database, dataChanges)
  }

  val bookmarkImportRepository: BookmarkImportRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultBookmarkImportRepository(
      context = application,
      database = database,
      articleGateway = bookmarkArticleGateway,
      dataChanges = dataChanges,
    )
  }

  val feedRepository: FeedRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultFeedRepository(
      database = database,
      contentSourceGateway = contentSourceGateway,
      dataChanges = dataChanges,
      applicationContext = application,
      httpClient = httpClient,
    )
  }

  val redditRepository: RedditRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultRedditRepository(feedRepository)
  }

  val youtubeRepository: YouTubeRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultYouTubeRepository(database, httpClient)
  }

  val feedImportRepository: FeedImportRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultFeedImportRepository(
      context = application,
      database = database,
      contentSourceGateway = contentSourceGateway,
      dataChanges = dataChanges,
    )
  }

  val refreshFeedsUseCase: RefreshFeedsUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    RefreshFeedsUseCase(feedRepository)
  }

  val saveSharedBookmarkUseCase: SaveSharedBookmarkUseCase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SaveSharedBookmarkUseCase(saver = bookmarkRepository)
  }

  val widgetRepository: WidgetRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultWidgetRepository(
      articleRepository = articleRepository,
      feedRepository = feedRepository,
      bookmarkRepository = bookmarkRepository,
      sourceSelector = RedditSourceBoundary::isNonRedditFeed,
    )
  }
}
