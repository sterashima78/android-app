package dev.terashima.yomitorirss.feature.summary

import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.allowsAutomaticAiEnrichment
import dev.terashima.yomitorirss.feature.bookmark.BookmarkReader
import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl
import dev.terashima.yomitorirss.feature.youtube.isYouTubeVideoUrl

class BookmarkAutoEnrichmentUseCase(
  private val articleRepository: ArticleRepository,
  private val enrichmentRequester: BookmarkEnrichmentRequester,
) {
  suspend operator fun invoke(articleId: String) {
    val article = articleRepository.findArticle(articleId) ?: return
    if (
      shouldRequestBookmarkEnrichment(
        url = article.url,
        sourceFeedUrl = article.sourceFeedUrl,
        contentType = article.effectiveContentType,
      )
    ) {
      enrichmentRequester.requestBookmarkEnrichment(articleId)
    }
  }
}

class BackfillBookmarkAutoEnrichmentUseCase(
  private val bookmarks: BookmarkReader,
  private val enrichmentRequester: BookmarkEnrichmentRequester,
) {
  suspend operator fun invoke() {
    val articleIds = bookmarks.listAllSavedArticles()
      .asSequence()
      .map { it.article }
      .filter { article ->
        shouldRequestBookmarkEnrichment(
          url = article.url,
          sourceFeedUrl = article.sourceFeedUrl,
          contentType = article.effectiveContentType,
        )
      }
      .map { it.id }
      .toList()

    enrichmentRequester.enqueueMissingBookmarkEnrichment(articleIds)
  }
}

fun shouldRequestBookmarkEnrichment(
  url: String,
  sourceFeedUrl: String,
  contentType: ContentType = ContentType.ARTICLE,
): Boolean =
  contentType.allowsAutomaticAiEnrichment() &&
    !isYouTubeVideoUrl(url) &&
    !isRedditFeedUrl(sourceFeedUrl) &&
    !isRedditFeedUrl(url)
