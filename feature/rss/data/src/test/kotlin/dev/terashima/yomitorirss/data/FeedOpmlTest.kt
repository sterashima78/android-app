package dev.terashima.yomitorirss.feature.rss.data
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class FeedOpmlTest {
  @Test
  fun `Feedly形式のフォルダとフィードを読み取る`() {
    val result = parseFeedOpml(
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <opml version="2.0">
        <head><title>Feedly subscriptions</title></head>
        <body>
          <outline text="Technology" title="Technology">
            <outline type="rss" text="Example Feed" title="Example Feed"
              xmlUrl="https://example.com/feed.xml" htmlUrl="https://example.com/" />
          </outline>
        </body>
      </opml>
      """.trimIndent().reader(),
    )

    assertEquals(1, result.feeds.size)
    assertEquals("Example Feed", result.feeds.single().title)
    assertEquals("https://example.com/feed.xml", result.feeds.single().feedUrl)
    assertEquals("https://example.com/", result.feeds.single().siteUrl)
    assertEquals(listOf("Technology"), result.feeds.single().folders)
    assertEquals(0, result.duplicates)
    assertEquals(0, result.skipped)
  }

  @Test
  fun `重複URLと対応外URLを集計する`() {
    val result = parseFeedOpml(
      """
      <opml version="2.0">
        <body>
          <outline text="One" xmlUrl="HTTPS://EXAMPLE.COM/feed.xml" />
          <outline text="Duplicate" xmlUrl="https://example.com/feed.xml" />
          <outline text="Unsupported" xmlUrl="ftp://example.com/feed.xml" />
        </body>
      </opml>
      """.trimIndent().reader(),
    )

    assertEquals(1, result.feeds.size)
    assertEquals(1, result.duplicates)
    assertEquals(1, result.skipped)
  }

  @Test
  fun `URL比較ではホスト名とスキームの大文字小文字を無視する`() {
    assertEquals(
      normalizedFeedUrlKey("HTTPS://EXAMPLE.COM/feed.xml"),
      normalizedFeedUrlKey("https://example.com/feed.xml#fragment"),
    )
    assertNull(normalizedFeedUrlKey("file:///tmp/feed.xml"))
  }

  @Test
  fun `OPML以外のXMLを拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      parseFeedOpml("<html><body /></html>".reader())
    }
  }
}
