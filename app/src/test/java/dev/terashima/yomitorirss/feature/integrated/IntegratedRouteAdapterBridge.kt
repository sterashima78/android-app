package dev.terashima.yomitorirss.feature.integrated

import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedItemAction
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedTab
import dev.terashima.yomitorirss.feature.mail.MailUiState
import dev.terashima.yomitorirss.feature.reddit.RedditUiState
import dev.terashima.yomitorirss.feature.rss.RssUiState
import dev.terashima.yomitorirss.feature.youtube.YouTubeUiState

internal typealias IntegratedTarget = dev.terashima.yomitorirss.ui.IntegratedTarget
internal typealias IntegratedEntry = dev.terashima.yomitorirss.ui.IntegratedEntry

internal fun integratedEntries(
  rssState: RssUiState,
  redditState: RedditUiState,
  youtubeState: YouTubeUiState,
  mailState: MailUiState,
  tab: IntegratedTab = IntegratedTab.UNREAD,
): List<IntegratedEntry> = dev.terashima.yomitorirss.ui.integratedEntries(
  rssState = rssState,
  redditState = redditState,
  youtubeState = youtubeState,
  mailState = mailState,
  tab = tab,
)

internal fun integratedItemActions(
  target: IntegratedTarget,
  redditState: RedditUiState,
  onOpenArticle: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onSubscribeRedditThread: (Article) -> Unit,
  onUnsubscribeRedditThread: (Article) -> Unit,
): List<IntegratedItemAction> = dev.terashima.yomitorirss.ui.integratedItemActions(
  target = target,
  redditState = redditState,
  onOpenArticle = onOpenArticle,
  onSummarize = onSummarize,
  onSubscribeRedditThread = onSubscribeRedditThread,
  onUnsubscribeRedditThread = onUnsubscribeRedditThread,
)
