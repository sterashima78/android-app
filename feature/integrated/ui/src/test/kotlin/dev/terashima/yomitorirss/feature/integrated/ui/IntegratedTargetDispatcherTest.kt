package dev.terashima.yomitorirss.feature.integrated.ui

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.mail.MailThread
import dev.terashima.yomitorirss.feature.youtube.YouTubeVideo
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class IntegratedTargetDispatcherTest {
  @Test
  fun `元sourceに応じて処理済みとあとで読む操作を委譲する`() {
    val calls = mutableListOf<String>()
    val dispatcher = dispatcher(calls)
    val rss = article("rss")
    val reddit = article("reddit")
    val youtube = youtube("youtube")
    val mail = mail("mail")

    dispatcher.markProcessed(IntegratedTarget.Rss(rss))
    dispatcher.markProcessed(IntegratedTarget.Reddit(reddit))
    dispatcher.markProcessed(IntegratedTarget.YouTube(youtube))
    dispatcher.markProcessed(IntegratedTarget.Mail(mail))
    dispatcher.defer(IntegratedTarget.Rss(rss))
    dispatcher.defer(IntegratedTarget.Reddit(reddit))
    dispatcher.defer(IntegratedTarget.YouTube(youtube))
    dispatcher.defer(IntegratedTarget.Mail(mail))

    assertEquals(
      listOf(
        "rss:read:rss",
        "reddit:read:reddit",
        "youtube:read:youtube",
        "mail:read:mail",
        "rss:later:rss",
        "reddit:later:reddit",
        "youtube:later:youtube",
        "mail:later:mail",
      ),
      calls,
    )
  }

  @Test
  fun `source固有でない操作は何もせずopenだけ正しいcallbackへ委譲する`() {
    val calls = mutableListOf<String>()
    val dispatcher = dispatcher(calls)
    val youtube = youtube("youtube")
    val mail = mail("mail")

    dispatcher.unsave(IntegratedTarget.YouTube(youtube))
    dispatcher.toggleMailStarred(IntegratedTarget.YouTube(youtube))
    dispatcher.archive(IntegratedTarget.YouTube(youtube))
    dispatcher.open(IntegratedTarget.YouTube(youtube))
    dispatcher.open(IntegratedTarget.Mail(mail))
    dispatcher.open(null)

    assertEquals(listOf("open:youtube:youtube", "open:mail:mail"), calls)
  }

  private fun dispatcher(calls: MutableList<String>) = IntegratedTargetDispatcher(
    rss = IntegratedArticleTargetActions(
      markRead = { calls += "rss:read:${it.id}" },
      markUnread = { calls += "rss:unread:${it.id}" },
      saveAndRead = { calls += "rss:save:${it.id}" },
      readLater = { calls += "rss:later:${it.id}" },
      unsave = { calls += "rss:unsave:${it.id}" },
      removeReadLater = { calls += "rss:remove-later:${it.id}" },
    ),
    reddit = IntegratedArticleTargetActions(
      markRead = { calls += "reddit:read:${it.id}" },
      markUnread = { calls += "reddit:unread:${it.id}" },
      saveAndRead = { calls += "reddit:save:${it.id}" },
      readLater = { calls += "reddit:later:${it.id}" },
      unsave = { calls += "reddit:unsave:${it.id}" },
      removeReadLater = { calls += "reddit:remove-later:${it.id}" },
    ),
    youtube = IntegratedYouTubeTargetActions(
      markRead = { calls += "youtube:read:${it.id}" },
      markUnread = { calls += "youtube:unread:${it.id}" },
      saveAndRead = { calls += "youtube:save:${it.id}" },
      toggleWatchLater = { calls += "youtube:later:${it.id}" },
    ),
    mail = IntegratedMailTargetActions(
      toggleRead = { calls += "mail:read:${it.id}" },
      toggleReadLater = { calls += "mail:later:${it.id}" },
      toggleStarred = { calls += "mail:star:${it.id}" },
      archive = { calls += "mail:archive:${it.id}" },
    ),
    onOpenArticle = { calls += "open:article:${it.id}" },
    onOpenMail = { calls += "open:mail:${it.id}" },
    onOpenYouTube = { calls += "open:youtube:${it.id}" },
  )

  private fun article(id: String) = Article(
    id = id,
    feedId = null,
    externalId = id,
    identityKey = id,
    url = "https://example.com/$id",
    title = id,
    publishedAt = "2026-08-26T00:00:00Z",
    fetchedAt = "2026-08-26T00:00:00Z",
    readAt = null,
    sourceTitle = "source",
    sourceFeedUrl = "https://example.com/feed.xml",
  )

  private fun youtube(id: String) = YouTubeVideo(
    id = id,
    channelId = "channel",
    channelTitle = "Channel",
    title = id,
    url = "https://example.com/$id",
    publishedAtEpochMillis = Instant.parse("2026-08-26T00:00:00Z").toEpochMilli(),
    isRead = false,
    isWatchLater = false,
  )

  private fun mail(id: String) = MailThread(
    id = id,
    accountId = "account",
    subject = id,
    snippet = "snippet",
    lastMessageAtEpochMillis = Instant.parse("2026-08-26T00:00:00Z").toEpochMilli(),
    messageCount = 1,
    isInInbox = true,
    isUnread = true,
    isStarred = false,
    isReadLater = false,
  )
}
