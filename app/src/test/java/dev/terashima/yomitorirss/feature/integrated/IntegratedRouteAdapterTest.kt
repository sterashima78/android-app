package dev.terashima.yomitorirss.feature.integrated

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedItemAction
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedSource
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedTab
import dev.terashima.yomitorirss.feature.mail.MailAccount
import dev.terashima.yomitorirss.feature.mail.MailThread
import dev.terashima.yomitorirss.feature.mail.MailUiState
import dev.terashima.yomitorirss.feature.reddit.RedditSubscription
import dev.terashima.yomitorirss.feature.reddit.RedditSubscriptionKind
import dev.terashima.yomitorirss.feature.reddit.RedditUiState
import dev.terashima.yomitorirss.feature.rss.RssUiState
import dev.terashima.yomitorirss.feature.youtube.YouTubeUiState
import dev.terashima.yomitorirss.feature.youtube.YouTubeVideo
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IntegratedRouteAdapterTest {
  @Test
  fun `各機能の未処理アイテムを時刻の新しい順に統合する`() {
    val rss = article("rss", "2026-08-11T09:00:00Z")
    val reddit = article("reddit", "2026-08-11T10:00:00Z")
    val youtube = youtube("youtube", "2026-08-11T11:00:00Z")
    val mail = mail("mail", "2026-08-11T12:00:00Z", isUnread = true)

    val entries = integratedEntries(
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
      entries.map { it.item.source },
    )
    assertEquals("user@example.com · snippet", entries.first().item.subtitle)
  }

  @Test
  fun `あとで読むアイテムを各機能から古い順に統合する`() {
    val rss = article("rss-later", "2026-08-11T09:00:00Z")
    val reddit = article("reddit-later", "2026-08-11T10:00:00Z")
    val youtube = youtube("youtube-later", "2026-08-11T11:00:00Z", isWatchLater = true)
    val mail = mail(
      "mail-later",
      "2026-08-11T12:00:00Z",
      isUnread = false,
      isReadLater = true,
    )

    val entries = integratedEntries(
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
      entries.map { it.item.source },
    )
    assertEquals(true, entries.last().item.isDeferred)
  }

  @Test
  fun `履歴を各機能から新しい順に統合し記事は既読日時を優先する`() {
    val rss = article("rss-history", "2026-08-11T09:00:00Z").copy(readAt = "2026-08-11T17:00:00Z")
    val reddit = article("reddit-history", "2026-08-11T10:00:00Z").copy(readAt = "2026-08-11T14:00:00Z")
    val youtube = youtube("youtube-history", "2026-08-11T15:00:00Z", isRead = true)
    val mail = mail("mail-history", "2026-08-11T16:00:00Z", isUnread = false)

    val entries = integratedEntries(
      rssState = RssUiState(initialized = true, history = listOf(rss)),
      redditState = RedditUiState(initialized = true, history = listOf(reddit)),
      youtubeState = YouTubeUiState(initialized = true, history = listOf(youtube)),
      mailState = MailUiState(
        initialized = true,
        accounts = listOf(MailAccount(id = "account", email = "user@example.com")),
        threads = listOf(mail),
      ),
      tab = IntegratedTab.HISTORY,
    )

    assertEquals(
      listOf(IntegratedSource.RSS, IntegratedSource.MAIL, IntegratedSource.YOUTUBE, IntegratedSource.REDDIT),
      entries.map { it.item.source },
    )
    assertEquals(Instant.parse("2026-08-11T17:00:00Z").toEpochMilli(), entries.first().item.timestamp)
  }

  @Test
  fun `履歴には未読メールと受信トレイ外メールを含めない`() {
    val unread = mail("unread", "2026-08-11T10:00:00Z", isUnread = true)
    val archivedRead = mail("archived-read", "2026-08-11T11:00:00Z", isUnread = false, isInInbox = false)

    val entries = integratedEntries(
      rssState = RssUiState(initialized = true),
      redditState = RedditUiState(initialized = true),
      youtubeState = YouTubeUiState(initialized = true),
      mailState = MailUiState(initialized = true, threads = listOf(unread, archivedRead)),
      tab = IntegratedTab.HISTORY,
    )

    assertFalse(entries.isNotEmpty())
  }

  @Test
  fun `非表示中の記事と未読受信トレイ外のメールは統合しない`() {
    val hidden = article("hidden", "2026-08-11T09:00:00Z")
    val archivedUnread = mail("archived", "2026-08-11T09:00:00Z", isUnread = true, isInInbox = false)

    val entries = integratedEntries(
      rssState = RssUiState(
        initialized = true,
        unread = listOf(hidden),
        hiddenArticleIds = setOf(hidden.id),
      ),
      redditState = RedditUiState(initialized = true),
      youtubeState = YouTubeUiState(initialized = true),
      mailState = MailUiState(initialized = true, threads = listOf(archivedUnread)),
    )

    assertFalse(entries.isNotEmpty())
  }

  @Test
  fun `表示モデルから元機能の対象を保持する`() {
    val rss = article("rss-target", "2026-08-11T09:00:00Z")
    val entry = integratedEntries(
      rssState = RssUiState(initialized = true, unread = listOf(rss)),
      redditState = RedditUiState(initialized = true),
      youtubeState = YouTubeUiState(initialized = true),
      mailState = MailUiState(initialized = true),
    ).single()

    assertEquals("rss:rss-target", entry.item.key)
    assertEquals(IntegratedTarget.Rss(rss), entry.target)
  }

  @Test
  fun `補助メニューは統合UIではなく元機能の対象から構築する`() {
    val rss = article("rss-menu", "2026-08-11T09:00:00Z")
    val redditArticle = article("reddit-menu", "2026-08-11T10:00:00Z").copy(
      url = "https://www.reddit.com/r/androiddev/comments/abc123/sample/",
    )
    val redditState = RedditUiState(
      initialized = true,
      subscriptions = listOf(
        RedditSubscription(
          id = "thread",
          title = "thread",
          feedUrl = "https://www.reddit.com/r/androiddev/comments/abc123/sample/.rss",
          kind = RedditSubscriptionKind.THREAD,
          lastFetchedAt = null,
          lastError = null,
        ),
      ),
    )

    assertEquals(
      listOf("はてなブックマークコメントを見る"),
      actionLabels(IntegratedTarget.Rss(rss), redditState),
    )
    assertEquals(
      listOf("はてなブックマークコメントを見る", "スレッドの購読を解除"),
      actionLabels(IntegratedTarget.Reddit(redditArticle), redditState),
    )
    assertEquals(
      emptyList<String>(),
      actionLabels(IntegratedTarget.YouTube(youtube("youtube", "2026-08-11T11:00:00Z")), redditState),
    )
  }

  private fun actionLabels(
    target: IntegratedTarget,
    redditState: RedditUiState,
  ): List<String> = integratedItemActions(
    target = target,
    redditState = redditState,
    onOpenArticle = {},
    onSubscribeRedditThread = {},
    onUnsubscribeRedditThread = {},
  ).map(IntegratedItemAction::label)

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

  private fun youtube(
    id: String,
    publishedAt: String,
    isWatchLater: Boolean = false,
    isRead: Boolean = false,
  ) = YouTubeVideo(
    id = id,
    channelId = "channel",
    channelTitle = "Channel",
    title = "YouTube",
    url = "https://example.com/$id",
    publishedAtEpochMillis = Instant.parse(publishedAt).toEpochMilli(),
    isRead = isRead,
    isWatchLater = isWatchLater,
  )

  private fun mail(
    id: String,
    publishedAt: String,
    isUnread: Boolean,
    isInInbox: Boolean = true,
    isReadLater: Boolean = false,
  ) = MailThread(
    id = id,
    accountId = "account",
    subject = "Mail",
    snippet = "snippet",
    lastMessageAtEpochMillis = Instant.parse(publishedAt).toEpochMilli(),
    messageCount = 1,
    isInInbox = isInInbox,
    isUnread = isUnread,
    isStarred = false,
    isReadLater = isReadLater,
  )
}
