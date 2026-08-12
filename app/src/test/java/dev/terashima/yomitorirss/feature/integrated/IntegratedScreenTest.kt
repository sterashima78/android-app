package dev.terashima.yomitorirss.feature.integrated

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.mail.MailAccount
import dev.terashima.yomitorirss.feature.mail.MailThread
import dev.terashima.yomitorirss.feature.mail.MailUiState
import dev.terashima.yomitorirss.feature.reddit.RedditUiState
import dev.terashima.yomitorirss.feature.rss.RssUiState
import dev.terashima.yomitorirss.feature.youtube.YouTubeUiState
import dev.terashima.yomitorirss.feature.youtube.YouTubeVideo
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IntegratedScreenTest {
  @Test
  fun `各機能の未処理アイテムを時刻の新しい順に統合する`() {
    val rss = article("rss", "2026-08-11T09:00:00Z")
    val reddit = article("reddit", "2026-08-11T10:00:00Z")
    val youtube = YouTubeVideo(
      id = "youtube",
      channelId = "channel",
      channelTitle = "Channel",
      title = "YouTube",
      url = "https://example.com/youtube",
      publishedAtEpochMillis = Instant.parse("2026-08-11T11:00:00Z").toEpochMilli(),
      isRead = false,
      isWatchLater = false,
    )
    val mail = MailThread(
      id = "mail",
      accountId = "account",
      subject = "Mail",
      snippet = "snippet",
      lastMessageAtEpochMillis = Instant.parse("2026-08-11T12:00:00Z").toEpochMilli(),
      messageCount = 1,
      isInInbox = true,
      isUnread = true,
      isStarred = false,
    )

    val items = integratedItems(
      rssState = RssUiState(initialized = true, unread = listOf(rss)),
      redditState = RedditUiState(initialized = true, unread = listOf(reddit)),
      youtubeState = YouTubeUiState(initialized = true, unread = listOf(youtube)),
      mailState = MailUiState(
        initialized = true,
        accounts = listOf(MailAccount(id = "account", email = "user@example.com")),
        threads = listOf(mail),
      ),
    )

    assertEquals(
      listOf(IntegratedSource.MAIL, IntegratedSource.YOUTUBE, IntegratedSource.REDDIT, IntegratedSource.RSS),
      items.map(IntegratedItem::source),
    )
  }

  @Test
  fun `あとで読むアイテムを各機能から古い順に統合する`() {
    val rss = article("rss-later", "2026-08-11T09:00:00Z")
    val reddit = article("reddit-later", "2026-08-11T10:00:00Z")
    val youtube = YouTubeVideo(
      id = "youtube-later",
      channelId = "channel",
      channelTitle = "Channel",
      title = "YouTube later",
      url = "https://example.com/youtube-later",
      publishedAtEpochMillis = Instant.parse("2026-08-11T11:00:00Z").toEpochMilli(),
      isRead = false,
      isWatchLater = true,
    )
    val mail = MailThread(
      id = "mail-later",
      accountId = "account",
      subject = "Mail later",
      snippet = "snippet",
      lastMessageAtEpochMillis = Instant.parse("2026-08-11T12:00:00Z").toEpochMilli(),
      messageCount = 1,
      isInInbox = true,
      isUnread = false,
      isStarred = false,
      isReadLater = true,
    )

    val items = integratedItems(
      rssState = RssUiState(
        initialized = true,
        readLater = listOf(BookmarkedArticle(article = rss, savedAt = "2026-08-11T09:05:00Z")),
      ),
      redditState = RedditUiState(
        initialized = true,
        readLater = listOf(BookmarkedArticle(article = reddit, savedAt = "2026-08-11T10:05:00Z")),
      ),
      youtubeState = YouTubeUiState(initialized = true, watchLater = listOf(youtube)),
      mailState = MailUiState(
        initialized = true,
        accounts = listOf(MailAccount(id = "account", email = "user@example.com")),
        threads = listOf(mail),
      ),
      tab = IntegratedTab.READ_LATER,
    )

    assertEquals(
      listOf(IntegratedSource.RSS, IntegratedSource.REDDIT, IntegratedSource.YOUTUBE, IntegratedSource.MAIL),
      items.map(IntegratedItem::source),
    )
  }

  @Test
  fun `非表示中の記事と未読受信トレイ外のメールは統合しない`() {
    val hidden = article("hidden", "2026-08-11T09:00:00Z")
    val archivedUnread = MailThread(
      id = "archived",
      accountId = "account",
      subject = "Archived",
      snippet = "",
      lastMessageAtEpochMillis = 1L,
      messageCount = 1,
      isInInbox = false,
      isUnread = true,
      isStarred = false,
    )

    val items = integratedItems(
      rssState = RssUiState(
        initialized = true,
        unread = listOf(hidden),
        hiddenArticleIds = setOf(hidden.id),
      ),
      redditState = RedditUiState(initialized = true),
      youtubeState = YouTubeUiState(initialized = true),
      mailState = MailUiState(initialized = true, threads = listOf(archivedUnread)),
    )

    assertFalse(items.isNotEmpty())
  }

  private fun article(id: String, publishedAt: String): Article = Article(
    id = id,
    feedId = null,
    externalId = id,
    identityKey = id,
    url = "https://example.com/$id",
    title = id,
    publishedAt = publishedAt,
    fetchedAt = publishedAt,
    readAt = null,
    sourceTitle = id,
    sourceFeedUrl = "https://example.com/$id.xml",
  )
}
