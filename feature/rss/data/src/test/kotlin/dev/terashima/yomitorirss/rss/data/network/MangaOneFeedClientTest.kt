package dev.terashima.yomitorirss.feature.rss.data.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MangaOneFeedClientTest {
  @Test
  fun `マンガワンの数値話ID URLを対象にして正規URLへ統一する`() {
    val client = MangaOneFeedClient(renderer = FakeMangaOnePageRenderer(samplePage()))

    assertTrue(client.supports("https://manga-one.com/manga/123/chapter/100"))
    assertTrue(
      client.supports(
        "https://www.manga-one.com/manga/123/chapter/100?type=chapter&sort_type=desc&page=11&limit=10#list",
      ),
    )
    assertFalse(client.supports("https://manga-one.com/manga/123/chapter/first"))
    assertFalse(client.supports("https://manga-one.com/title/123/100"))
    assertFalse(client.supports("https://manga-one.com/manga/sample/chapter/100"))
    assertFalse(client.supports("https://manga-one.com/manga/123/chapter/sample"))
    assertFalse(client.supports("https://example.com/manga/123/chapter/100"))
    assertEquals(
      "https://manga-one.com/manga/123/chapter/100",
      client.canonicalWorkUrl(
        "https://www.manga-one.com/manga/123/chapter/100?type=chapter&sort_type=desc&page=11&limit=10#list",
      ),
    )
  }

  @Test
  fun `赤い無料ラベルの話だけをフィードとして返す`() = runBlocking {
    val renderer = FakeMangaOnePageRenderer(
      MangaOneRenderedPage(
        title = "サンプル作品",
        chapters = listOf(
          MangaOneRenderedChapter(
            title = "第3話 無料公開",
            url = "https://manga-one.com/manga/123/chapter/300",
            label = "無料",
            dateText = "2026年8月17日",
          ),
          MangaOneRenderedChapter(
            title = "第2話 毎日無料",
            url = "https://manga-one.com/manga/123/chapter/200",
            label = "毎日無料",
            dateText = "2026/08/10",
          ),
          MangaOneRenderedChapter(
            title = "第4話 先読み",
            url = "https://manga-one.com/manga/123/chapter/400",
            label = "先読み",
            dateText = "2026/08/24",
          ),
          MangaOneRenderedChapter(
            title = "第1話 通常話",
            url = "https://manga-one.com/manga/123/chapter/100",
            label = "",
            dateText = "",
          ),
        ),
      ),
    )
    val client = MangaOneFeedClient(
      renderer = renderer,
      now = { Instant.parse("2026-08-17T12:00:00Z") },
    )

    val result = client.fetchFeed(
      "https://www.manga-one.com/manga/123/chapter/100?type=chapter&sort_type=desc&page=11&limit=10",
    )
    val feed = requireNotNull(result.feed)

    assertEquals("https://manga-one.com/manga/123/chapter/100", renderer.lastUrl)
    assertEquals("123", renderer.lastMangaId)
    assertEquals("サンプル作品", feed.title)
    assertEquals("https://manga-one.com/manga/123/chapter/100", feed.feedUrl)
    assertEquals(1, feed.articles.size)
    assertEquals("第3話 無料公開", feed.articles.single().title)
    assertEquals("https://manga-one.com/manga/123/chapter/300", feed.articles.single().url)
    assertEquals("2026-08-16T15:00:00Z", feed.articles.single().publishedAt)
    assertEquals(null, result.etag)
    assertEquals(null, result.lastModified)
  }

  @Test
  fun `公開日が取れない場合は発見時刻を使う`() = runBlocking {
    val renderer = FakeMangaOnePageRenderer(
      MangaOneRenderedPage(
        title = "サンプル作品",
        chapters = listOf(
          MangaOneRenderedChapter(
            title = "第1話",
            url = "https://manga-one.com/manga/123/chapter/100",
            label = "無料",
            dateText = "",
          ),
        ),
      ),
    )
    val client = MangaOneFeedClient(
      renderer = renderer,
      now = { Instant.parse("2026-08-17T12:34:56Z") },
    )

    val article = requireNotNull(client.fetchFeed("https://manga-one.com/manga/123/chapter/100").feed)
      .articles.single()

    assertEquals("2026-08-17T12:34:56Z", article.publishedAt)
  }

  private fun samplePage(): MangaOneRenderedPage = MangaOneRenderedPage(
    title = "サンプル作品",
    chapters = listOf(
      MangaOneRenderedChapter(
        title = "第1話",
        url = "https://manga-one.com/manga/123/chapter/100",
        label = "無料",
        dateText = "",
      ),
    ),
  )
}

private class FakeMangaOnePageRenderer(
  private val page: MangaOneRenderedPage,
) : MangaOnePageRenderer {
  var lastUrl: String? = null
    private set
  var lastMangaId: String? = null
    private set

  override suspend fun render(url: String, mangaId: String): MangaOneRenderedPage {
    lastUrl = url
    lastMangaId = mangaId
    return page
  }
}
