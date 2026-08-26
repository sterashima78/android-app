package dev.terashima.yomitorirss.feature.integrated.ui

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.mail.MailThread
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.youtube.YouTubeVideo
import dev.terashima.yomitorirss.feature.youtube.YouTubeViewModel

internal data class IntegratedArticleTargetActions(
  val markRead: (Article) -> Unit,
  val markUnread: (Article) -> Unit,
  val saveAndRead: (Article) -> Unit,
  val readLater: (Article) -> Unit,
  val unsave: (Article) -> Unit,
  val removeReadLater: (Article) -> Unit,
)

internal data class IntegratedYouTubeTargetActions(
  val markRead: (YouTubeVideo) -> Unit,
  val markUnread: (YouTubeVideo) -> Unit,
  val saveAndRead: (YouTubeVideo) -> Unit,
  val toggleWatchLater: (YouTubeVideo) -> Unit,
)

internal data class IntegratedMailTargetActions(
  val toggleRead: (MailThread) -> Unit,
  val toggleReadLater: (MailThread) -> Unit,
  val toggleStarred: (MailThread) -> Unit,
  val archive: (MailThread) -> Unit,
)

internal class IntegratedTargetDispatcher(
  private val rss: IntegratedArticleTargetActions,
  private val reddit: IntegratedArticleTargetActions,
  private val youtube: IntegratedYouTubeTargetActions,
  private val mail: IntegratedMailTargetActions,
  private val onOpenArticle: (Article) -> Unit,
  private val onOpenMail: (MailThread) -> Unit,
  private val onOpenYouTube: (YouTubeVideo) -> Unit,
) {
  fun markProcessed(target: IntegratedTarget?) {
    when (target) {
      is IntegratedTarget.Rss -> rss.markRead(target.article)
      is IntegratedTarget.Reddit -> reddit.markRead(target.article)
      is IntegratedTarget.YouTube -> youtube.markRead(target.video)
      is IntegratedTarget.Mail -> mail.toggleRead(target.thread)
      null -> Unit
    }
  }

  fun markUnread(target: IntegratedTarget?) {
    when (target) {
      is IntegratedTarget.Rss -> rss.markUnread(target.article)
      is IntegratedTarget.Reddit -> reddit.markUnread(target.article)
      is IntegratedTarget.YouTube -> youtube.markUnread(target.video)
      is IntegratedTarget.Mail -> mail.toggleRead(target.thread)
      null -> Unit
    }
  }

  fun save(target: IntegratedTarget?) {
    when (target) {
      is IntegratedTarget.Rss -> rss.saveAndRead(target.article)
      is IntegratedTarget.Reddit -> reddit.saveAndRead(target.article)
      is IntegratedTarget.YouTube -> youtube.saveAndRead(target.video)
      is IntegratedTarget.Mail,
      null -> Unit
    }
  }

  fun defer(target: IntegratedTarget?) {
    when (target) {
      is IntegratedTarget.Rss -> rss.readLater(target.article)
      is IntegratedTarget.Reddit -> reddit.readLater(target.article)
      is IntegratedTarget.YouTube -> youtube.toggleWatchLater(target.video)
      is IntegratedTarget.Mail -> mail.toggleReadLater(target.thread)
      null -> Unit
    }
  }

  fun unsave(target: IntegratedTarget?) {
    when (target) {
      is IntegratedTarget.Rss -> rss.unsave(target.article)
      is IntegratedTarget.Reddit -> reddit.unsave(target.article)
      is IntegratedTarget.YouTube,
      is IntegratedTarget.Mail,
      null -> Unit
    }
  }

  fun removeDeferred(target: IntegratedTarget?) {
    when (target) {
      is IntegratedTarget.Rss -> rss.removeReadLater(target.article)
      is IntegratedTarget.Reddit -> reddit.removeReadLater(target.article)
      is IntegratedTarget.YouTube -> youtube.toggleWatchLater(target.video)
      is IntegratedTarget.Mail -> mail.toggleReadLater(target.thread)
      null -> Unit
    }
  }

  fun toggleMailStarred(target: IntegratedTarget?) {
    (target as? IntegratedTarget.Mail)?.let { mail.toggleStarred(it.thread) }
  }

  fun archive(target: IntegratedTarget?) {
    (target as? IntegratedTarget.Mail)?.let { mail.archive(it.thread) }
  }

  fun open(target: IntegratedTarget?) {
    when (target) {
      is IntegratedTarget.Rss -> onOpenArticle(target.article)
      is IntegratedTarget.Reddit -> onOpenArticle(target.article)
      is IntegratedTarget.YouTube -> onOpenYouTube(target.video)
      is IntegratedTarget.Mail -> onOpenMail(target.thread)
      null -> Unit
    }
  }
}

internal fun integratedTargetDispatcher(
  rssViewModel: RssViewModel,
  redditViewModel: RedditViewModel,
  youtubeViewModel: YouTubeViewModel,
  mailViewModel: MailViewModel,
  onOpenArticle: (Article) -> Unit,
  onOpenMail: (MailThread) -> Unit,
  onOpenYouTube: (YouTubeVideo) -> Unit,
): IntegratedTargetDispatcher = IntegratedTargetDispatcher(
  rss = IntegratedArticleTargetActions(
    markRead = rssViewModel::markRead,
    markUnread = rssViewModel::markUnread,
    saveAndRead = rssViewModel::saveAndRead,
    readLater = rssViewModel::readLater,
    unsave = rssViewModel::unsave,
    removeReadLater = rssViewModel::removeReadLater,
  ),
  reddit = IntegratedArticleTargetActions(
    markRead = redditViewModel::markRead,
    markUnread = redditViewModel::markUnread,
    saveAndRead = redditViewModel::saveAndRead,
    readLater = redditViewModel::readLater,
    unsave = redditViewModel::unsave,
    removeReadLater = redditViewModel::removeReadLater,
  ),
  youtube = IntegratedYouTubeTargetActions(
    markRead = youtubeViewModel::markRead,
    markUnread = youtubeViewModel::markUnread,
    saveAndRead = youtubeViewModel::saveAndRead,
    toggleWatchLater = youtubeViewModel::toggleWatchLater,
  ),
  mail = IntegratedMailTargetActions(
    toggleRead = mailViewModel::toggleRead,
    toggleReadLater = mailViewModel::toggleReadLater,
    toggleStarred = mailViewModel::toggleStarred,
    archive = mailViewModel::archive,
  ),
  onOpenArticle = onOpenArticle,
  onOpenMail = onOpenMail,
  onOpenYouTube = onOpenYouTube,
)
