package dev.terashima.yomitorirss.ui

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedItem
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedSource
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedTab
import dev.terashima.yomitorirss.feature.mail.MailThread
import dev.terashima.yomitorirss.feature.mail.MailUiState
import dev.terashima.yomitorirss.feature.reddit.RedditUiState
import dev.terashima.yomitorirss.feature.rss.RssUiState
import dev.terashima.yomitorirss.feature.youtube.YouTubeUiState
import dev.terashima.yomitorirss.feature.youtube.YouTubeVideo
import java.time.Instant

internal sealed interface IntegratedTarget {
  data class Rss(val article: Article) : IntegratedTarget
  data class Reddit(val article: Article) : IntegratedTarget
  data class YouTube(val video: YouTubeVideo) : IntegratedTarget
  data class Mail(val thread: MailThread) : IntegratedTarget
}

internal data class IntegratedEntry(
  val item: IntegratedItem,
  val target: IntegratedTarget,
)

internal fun integratedEntries(
  rssState: RssUiState,
  redditState: RedditUiState,
  youtubeState: YouTubeUiState,
  mailState: MailUiState,
  tab: IntegratedTab = IntegratedTab.UNREAD,
): List<IntegratedEntry> {
  val accountLabels = mailState.accounts.associate { account ->
    account.id to (account.displayName?.takeIf(String::isNotBlank) ?: account.email)
  }
  val entries = buildList {
    when (tab) {
      IntegratedTab.UNREAD -> {
        rssState.unread
          .filterNot { it.id in rssState.hiddenArticleIds }
          .forEach { add(articleEntry(it, IntegratedSource.RSS, IntegratedTarget.Rss(it))) }
        redditState.unread
          .filterNot { it.id in redditState.hiddenArticleIds }
          .forEach { add(articleEntry(it, IntegratedSource.REDDIT, IntegratedTarget.Reddit(it))) }
        youtubeState.unread.forEach { video -> add(youtubeEntry(video)) }
        mailState.threads
          .filter { it.isUnread && it.isInInbox }
          .forEach { thread -> add(mailEntry(thread, accountLabels[thread.accountId] ?: thread.accountId)) }
      }

      IntegratedTab.READ_LATER -> {
        rssState.readLater
          .filterNot { it.article.id in rssState.hiddenArticleIds }
          .forEach { saved -> add(articleEntry(saved.article, IntegratedSource.RSS, IntegratedTarget.Rss(saved.article))) }
        redditState.readLater
          .filterNot { it.article.id in redditState.hiddenArticleIds }
          .forEach { saved -> add(articleEntry(saved.article, IntegratedSource.REDDIT, IntegratedTarget.Reddit(saved.article))) }
        youtubeState.watchLater.forEach { video -> add(youtubeEntry(video)) }
        mailState.threads
          .filter(MailThread::isReadLater)
          .forEach { thread -> add(mailEntry(thread, accountLabels[thread.accountId] ?: thread.accountId)) }
      }

      IntegratedTab.HISTORY -> {
        rssState.history
          .filterNot { it.id in rssState.hiddenArticleIds }
          .forEach {
            add(articleEntry(it, IntegratedSource.RSS, IntegratedTarget.Rss(it), it.historyTimeMillis()))
          }
        redditState.history
          .filterNot { it.id in redditState.hiddenArticleIds }
          .forEach {
            add(articleEntry(it, IntegratedSource.REDDIT, IntegratedTarget.Reddit(it), it.historyTimeMillis()))
          }
        youtubeState.history.forEach { video -> add(youtubeEntry(video)) }
        mailState.threads
          .filter { !it.isUnread && it.isInInbox }
          .forEach { thread -> add(mailEntry(thread, accountLabels[thread.accountId] ?: thread.accountId)) }
      }
    }
  }
  return when (tab) {
    IntegratedTab.UNREAD -> entries.sortedByDescending { it.item.timestamp }
    IntegratedTab.READ_LATER -> entries.sortedBy { it.item.timestamp }
    IntegratedTab.HISTORY -> entries.sortedByDescending { it.item.timestamp }
  }
}

private fun articleEntry(
  article: Article,
  source: IntegratedSource,
  target: IntegratedTarget,
  timestamp: Long = article.eventTimeMillis(),
): IntegratedEntry = IntegratedEntry(
  item = IntegratedItem(
    key = "${source.name.lowercase()}:${article.id}",
    source = source,
    title = article.title,
    subtitle = article.sourceTitle,
    timestamp = timestamp,
  ),
  target = target,
)

private fun youtubeEntry(video: YouTubeVideo): IntegratedEntry = IntegratedEntry(
  item = IntegratedItem(
    key = "youtube:${video.id}",
    source = IntegratedSource.YOUTUBE,
    title = video.title,
    subtitle = video.channelTitle,
    timestamp = video.publishedAtEpochMillis,
    isDeferred = video.isWatchLater,
  ),
  target = IntegratedTarget.YouTube(video),
)

private fun mailEntry(thread: MailThread, accountLabel: String): IntegratedEntry = IntegratedEntry(
  item = IntegratedItem(
    key = "mail:${thread.accountId}:${thread.id}",
    source = IntegratedSource.MAIL,
    title = thread.subject.ifBlank { "（件名なし）" },
    subtitle = buildString {
      append(accountLabel)
      if (thread.snippet.isNotBlank()) {
        append(" · ")
        append(thread.snippet)
      }
    },
    timestamp = thread.lastMessageAtEpochMillis,
    isDeferred = thread.isReadLater,
    isStarred = thread.isStarred,
  ),
  target = IntegratedTarget.Mail(thread),
)

private fun Article.eventTimeMillis(): Long =
  sequenceOf(publishedAt, fetchedAt)
    .mapNotNull { value -> runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() }
    .firstOrNull()
    ?: 0L

private fun Article.historyTimeMillis(): Long =
  sequenceOf(readAt, publishedAt, fetchedAt)
    .mapNotNull { value -> value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } }
    .firstOrNull()
    ?: 0L
