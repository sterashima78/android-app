package dev.terashima.yomitorirss.feature.integrated

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
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

  @Test
  fun `統合ビューのスワイプ操作は各機能の一覧と一致する`() {
    val rss = IntegratedItem.Rss(article("rss-action", "2026-08-11T09:00:00Z"))
    val reddit = IntegratedItem.Reddit(article("reddit-action", "2026-08-11T10:00:00Z"))
    val youtube = IntegratedItem.YouTube(
      YouTubeVideo(
        id = "youtube-action",
        channelId = "channel",
        channelTitle = "Channel",
        title = "YouTube",
        url = "https://example.com/youtube-action",
        publishedAtEpochMillis = 1L,
        isRead = false,
        isWatchLater = true,
      ),
    )
    val mail = IntegratedItem.Mail(
      thread = MailThread(
        id = "mail-action",
        accountId = "account",
        subject = "Mail",
        snippet = "",
        lastMessageAtEpochMillis = 1L,
        messageCount = 1,
        isInInbox = true,
        isUnread = true,
        isStarred = true,
        isReadLater = true,
      ),
      accountLabel = "user@example.com",
    )

    assertEquals(listOf("既読", "ブックマーク", "あとで読む"), swipeLabels(rss, IntegratedTab.UNREAD))
    assertEquals(listOf("既読", "ブックマーク", "あとで読む"), swipeLabels(reddit, IntegratedTab.UNREAD))
    assertEquals(listOf("既読", "保存", "あとで見る"), swipeLabels(youtube, IntegratedTab.UNREAD))
    assertEquals(listOf("既読", "あとで読む解除", "アーカイブ"), swipeLabels(mail, IntegratedTab.UNREAD))

    assertEquals(listOf("ブックマーク解除", "未分類へ", null), swipeLabels(rss, IntegratedTab.READ_LATER))
    assertEquals(listOf("ブックマーク解除", "未分類へ", null), swipeLabels(reddit, IntegratedTab.READ_LATER))
    assertEquals(listOf("既読", "保存", "未読へ戻す"), swipeLabels(youtube, IntegratedTab.READ_LATER))
    assertEquals(listOf("あとで読む解除", "スター解除", "アーカイブ"), swipeLabels(mail, IntegratedTab.READ_LATER))

    assertEquals(listOf(false, false, false), swipeDismisses(youtube, IntegratedTab.READ_LATER))
    assertEquals(listOf(true, false, false), swipeDismisses(mail, IntegratedTab.READ_LATER))
  }

  @Test
  fun `補助メニューはスワイプや直接操作と重複させない`() {
    val rss = IntegratedItem.Rss(article("rss-menu", "2026-08-11T09:00:00Z"))
    val redditArticle = article("reddit-menu", "2026-08-11T10:00:00Z").copy(
      url = "https://www.reddit.com/r/androiddev/comments/abc123/sample/",
    )
    val reddit = IntegratedItem.Reddit(redditArticle)
    val youtube = IntegratedItem.YouTube(
      YouTubeVideo(
        id = "youtube-menu",
        channelId = "channel",
        channelTitle = "Channel",
        title = "YouTube",
        url = "https://example.com/youtube-menu",
        publishedAtEpochMillis = 1L,
        isRead = false,
        isWatchLater = false,
      ),
    )
    val mail = IntegratedItem.Mail(
      thread = MailThread(
        id = "mail-menu",
        accountId = "account",
        subject = "Mail",
        snippet = "",
        lastMessageAtEpochMillis = 1L,
        messageCount = 1,
        isInInbox = true,
        isUnread = true,
        isStarred = true,
      ),
      accountLabel = "user@example.com",
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

    assertEquals(listOf("はてなブックマークコメントを見る"), actionLabels(rss, redditState))
    assertEquals(
      listOf("はてなブックマークコメントを見る", "スレッドの購読を解除"),
      actionLabels(reddit, redditState),
    )
    assertEquals(emptyList<String>(), actionLabels(youtube, redditState))
    assertEquals(emptyList<String>(), actionLabels(mail, redditState))
  }

  private fun swipeLabels(item: IntegratedItem, tab: IntegratedTab): List<String?> {
    val actions = integratedSwipeActions(item, tab)
    return listOf(actions.left?.label, actions.right?.label, actions.farRight?.label)
  }

  private fun swipeDismisses(item: IntegratedItem, tab: IntegratedTab): List<Boolean?> {
    val actions = integratedSwipeActions(item, tab)
    return listOf(
      actions.left?.dismissesItem,
      actions.right?.dismissesItem,
      actions.farRight?.dismissesItem,
    )
  }

  private fun actionLabels(
    item: IntegratedItem,
    redditState: RedditUiState,
  ): List<String> = integratedItemActions(
    item = item,
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
}
