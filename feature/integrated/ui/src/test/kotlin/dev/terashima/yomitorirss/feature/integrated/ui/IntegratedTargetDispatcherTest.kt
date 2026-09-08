package dev.terashima.yomitorirss.feature.integrated.ui

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.mail.MailThread
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoProviderVideo
import dev.terashima.yomitorirss.feature.video.VideoSource
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
    val video = providerVideo("video")
    val mail = mail("mail")

    dispatcher.markProcessed(IntegratedTarget.Rss(rss))
    dispatcher.markProcessed(IntegratedTarget.Reddit(reddit))
    dispatcher.markProcessed(IntegratedTarget.ProviderVideo(video))
    dispatcher.markProcessed(IntegratedTarget.Mail(mail))
    dispatcher.defer(IntegratedTarget.Rss(rss))
    dispatcher.defer(IntegratedTarget.Reddit(reddit))
    dispatcher.defer(IntegratedTarget.ProviderVideo(video))
    dispatcher.defer(IntegratedTarget.Mail(mail))

    assertEquals(
      listOf(
        "rss:read:rss",
        "reddit:read:reddit",
        "video:read:video",
        "mail:read:mail",
        "rss:later:rss",
        "reddit:later:reddit",
        "video:later:video",
        "mail:later:mail",
      ),
      calls,
    )
  }

  @Test
  fun `source固有でない操作は何もせずopenだけ正しいcallbackへ委譲する`() {
    val calls = mutableListOf<String>()
    val dispatcher = dispatcher(calls)
    val video = providerVideo("video")
    val mail = mail("mail")

    dispatcher.unsave(IntegratedTarget.ProviderVideo(video))
    dispatcher.toggleMailStarred(IntegratedTarget.ProviderVideo(video))
    dispatcher.archive(IntegratedTarget.ProviderVideo(video))
    dispatcher.open(IntegratedTarget.ProviderVideo(video))
    dispatcher.open(IntegratedTarget.Mail(mail))
    dispatcher.open(null)

    assertEquals(listOf("open:video:video", "open:mail:mail"), calls)
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
    providerVideo = IntegratedProviderVideoTargetActions(
      markRead = { calls += "video:read:${it.providerItemId}" },
      markUnread = { calls += "video:unread:${it.providerItemId}" },
      saveAndRead = { calls += "video:save:${it.providerItemId}" },
      toggleWatchLater = { calls += "video:later:${it.providerItemId}" },
    ),
    mail = IntegratedMailTargetActions(
      toggleRead = { calls += "mail:read:${it.id}" },
      toggleReadLater = { calls += "mail:later:${it.id}" },
      toggleStarred = { calls += "mail:star:${it.id}" },
      archive = { calls += "mail:archive:${it.id}" },
    ),
    onOpenArticle = { calls += "open:article:${it.id}" },
    onOpenMail = { calls += "open:mail:${it.id}" },
    onOpenProviderVideo = { calls += "open:video:${it.providerItemId}" },
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

  private fun providerVideo(id: String) = VideoProviderVideo(
    video = VideoItem(
      id = "provider:youtube:$id",
      source = VideoSource.SERVICE,
      sourceId = "youtube:$id",
      title = id,
      pageUrl = "https://example.com/$id",
      updatedAtEpochMillis = Instant.parse("2026-08-26T00:00:00Z").toEpochMilli(),
    ),
    providerId = "youtube",
    providerItemId = id,
    subscriptionId = "youtube:channel",
    subscriptionTitle = "Channel",
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
