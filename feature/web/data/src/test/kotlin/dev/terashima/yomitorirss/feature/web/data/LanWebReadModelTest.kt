package dev.terashima.yomitorirss.feature.web.data

import dev.terashima.yomitorirss.feature.web.LanWebArticleItem
import dev.terashima.yomitorirss.feature.web.LanWebContentGateway
import dev.terashima.yomitorirss.feature.web.LanWebFeedItem
import dev.terashima.yomitorirss.feature.web.LanWebSourceKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LanWebReadModelTest {
  @Test
  fun `未読viewはRSS記事だけを表示する`() = runBlocking {
    val readModel = LanWebReadModel(
      FakeGateway(
        unread = listOf(
          article("rss", LanWebSourceKind.RSS),
          article("reddit", LanWebSourceKind.REDDIT),
        ),
      ),
    )

    val page = readModel.loadHome(LanWebViews.UNREAD)
    val content = page.content as LanWebContent.Articles

    assertEquals(listOf("rss"), content.articles.map { it.title })
  }

  @Test
  fun `Reddit viewはReddit記事だけを表示する`() = runBlocking {
    val readModel = LanWebReadModel(
      FakeGateway(
        unread = listOf(
          article("rss", LanWebSourceKind.RSS),
          article("reddit", LanWebSourceKind.REDDIT),
        ),
      ),
    )

    val page = readModel.loadHome(LanWebViews.REDDIT)
    val content = page.content as LanWebContent.Articles

    assertEquals(listOf("reddit"), content.articles.map { it.title })
  }

  @Test
  fun `Feed viewはRSS feedだけを表示する`() = runBlocking {
    val readModel = LanWebReadModel(
      FakeGateway(
        feeds = listOf(
          feed("rss", LanWebSourceKind.RSS),
          feed("reddit", LanWebSourceKind.REDDIT),
        ),
      ),
    )

    val page = readModel.loadHome(LanWebViews.FEEDS)
    val content = page.content as LanWebContent.Feeds

    assertEquals(listOf("rss"), content.feeds.map { it.title })
  }

  private fun article(title: String, sourceKind: LanWebSourceKind) = LanWebArticleItem(
    title = title,
    url = "https://example.com/$title",
    sourceTitle = "source",
    publishedAt = "2026-08-28T00:00:00Z",
    sourceKind = sourceKind,
  )

  private fun feed(title: String, sourceKind: LanWebSourceKind) = LanWebFeedItem(
    title = title,
    feedUrl = "https://example.com/$title.xml",
    siteUrl = "https://example.com/$title",
    sourceKind = sourceKind,
  )

  private class FakeGateway(
    private val unread: List<LanWebArticleItem> = emptyList(),
    private val saved: List<LanWebArticleItem> = emptyList(),
    private val readLater: List<LanWebArticleItem> = emptyList(),
    private val feeds: List<LanWebFeedItem> = emptyList(),
  ) : LanWebContentGateway {
    override suspend fun listUnreadArticles(): List<LanWebArticleItem> = unread

    override suspend fun listSavedArticles(): List<LanWebArticleItem> = saved

    override suspend fun listReadLaterArticles(): List<LanWebArticleItem> = readLater

    override suspend fun listFeeds(): List<LanWebFeedItem> = feeds
  }
}
