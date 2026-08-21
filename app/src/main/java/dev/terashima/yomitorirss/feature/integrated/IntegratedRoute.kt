package dev.terashima.yomitorirss.feature.integrated

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedItem
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedItemAction
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedScreen
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedSource
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedTab
import dev.terashima.yomitorirss.feature.mail.MailThread
import dev.terashima.yomitorirss.feature.mail.MailUiState
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.mail.Mailbox
import dev.terashima.yomitorirss.feature.reddit.RedditSubscriptionKind
import dev.terashima.yomitorirss.feature.reddit.RedditUiState
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.reddit.redditThreadId
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssUiState
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.youtube.YouTubeUiState
import dev.terashima.yomitorirss.feature.youtube.YouTubeVideo
import dev.terashima.yomitorirss.feature.youtube.YouTubeViewModel
import java.time.Instant

@Composable
fun IntegratedRoute(
  rssViewModel: RssViewModel,
  redditViewModel: RedditViewModel,
  feedViewModel: FeedViewModel,
  mailViewModel: MailViewModel,
  youtubeViewModelFactory: YouTubeViewModel.Factory,
  onOpenArticle: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onOpenMail: (MailThread) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val youtubeViewModel: YouTubeViewModel = viewModel(factory = youtubeViewModelFactory)
  val rssState by rssViewModel.state.collectAsState()
  val redditState by redditViewModel.state.collectAsState()
  val feedState by feedViewModel.state.collectAsState()
  val mailState by mailViewModel.state.collectAsState()
  val youtubeState by youtubeViewModel.state.collectAsState()
  val snackbarHostState = remember { SnackbarHostState() }
  var selectedTabName by rememberSaveable { mutableStateOf(IntegratedTab.UNREAD.name) }
  val selectedTab = IntegratedTab.entries.firstOrNull { it.name == selectedTabName }
    ?: IntegratedTab.UNREAD

  LaunchedEffect(Unit) {
    mailViewModel.updateQuery("")
    mailViewModel.selectAccount(null)
  }
  LaunchedEffect(selectedTab) {
    mailViewModel.selectMailbox(
      when (selectedTab) {
        IntegratedTab.UNREAD -> Mailbox.UNREAD
        IntegratedTab.READ_LATER -> Mailbox.READ_LATER
        IntegratedTab.HISTORY -> Mailbox.INBOX
      },
    )
  }
  LaunchedEffect(mailState.message) {
    val message = mailState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    mailViewModel.dismissMessage()
  }
  LaunchedEffect(youtubeState.message) {
    val message = youtubeState.message ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(message)
    youtubeViewModel.dismissMessage()
  }

  val initialized = rssState.initialized &&
    redditState.initialized &&
    mailState.initialized &&
    youtubeState.initialized
  val entries = integratedEntries(
    rssState = rssState,
    redditState = redditState,
    youtubeState = youtubeState,
    mailState = mailState,
    tab = selectedTab,
  )
  val targetsByKey = entries.associate { it.item.key to it.target }

  Box(modifier = modifier.fillMaxSize()) {
    if (!initialized) {
      CircularProgressIndicator(Modifier.align(Alignment.Center))
    } else {
      IntegratedScreen(
        modifier = Modifier.fillMaxSize(),
        selectedTab = selectedTab,
        items = entries.map(IntegratedEntry::item),
        isRefreshing = feedState.refreshing ||
          redditState.refreshing ||
          youtubeState.refreshing ||
          mailState.loading,
        onSelectTab = { selectedTabName = it.name },
        onRefresh = {
          feedViewModel.refresh()
          redditViewModel.refresh()
          youtubeViewModel.refresh()
          mailViewModel.refresh()
        },
        onMarkProcessed = { item ->
          when (val target = targetsByKey[item.key]) {
            is IntegratedTarget.Rss -> rssViewModel.markRead(target.article)
            is IntegratedTarget.Reddit -> redditViewModel.markRead(target.article)
            is IntegratedTarget.YouTube -> youtubeViewModel.markRead(target.video)
            is IntegratedTarget.Mail -> mailViewModel.toggleRead(target.thread)
            null -> Unit
          }
        },
        onMarkUnread = { item ->
          when (val target = targetsByKey[item.key]) {
            is IntegratedTarget.Rss -> rssViewModel.markUnread(target.article)
            is IntegratedTarget.Reddit -> redditViewModel.markUnread(target.article)
            is IntegratedTarget.YouTube -> youtubeViewModel.markUnread(target.video)
            is IntegratedTarget.Mail -> mailViewModel.toggleRead(target.thread)
            null -> Unit
          }
        },
        onSave = { item ->
          when (val target = targetsByKey[item.key]) {
            is IntegratedTarget.Rss -> rssViewModel.saveAndRead(target.article)
            is IntegratedTarget.Reddit -> redditViewModel.saveAndRead(target.article)
            is IntegratedTarget.YouTube -> youtubeViewModel.saveAndRead(target.video)
            is IntegratedTarget.Mail,
            null -> Unit
          }
        },
        onDefer = { item ->
          when (val target = targetsByKey[item.key]) {
            is IntegratedTarget.Rss -> rssViewModel.readLater(target.article)
            is IntegratedTarget.Reddit -> redditViewModel.readLater(target.article)
            is IntegratedTarget.YouTube -> youtubeViewModel.toggleWatchLater(target.video)
            is IntegratedTarget.Mail -> mailViewModel.toggleReadLater(target.thread)
            null -> Unit
          }
        },
        onUnsave = { item ->
          when (val target = targetsByKey[item.key]) {
            is IntegratedTarget.Rss -> rssViewModel.unsave(target.article)
            is IntegratedTarget.Reddit -> redditViewModel.unsave(target.article)
            is IntegratedTarget.YouTube,
            is IntegratedTarget.Mail,
            null -> Unit
          }
        },
        onRemoveDeferred = { item ->
          when (val target = targetsByKey[item.key]) {
            is IntegratedTarget.Rss -> rssViewModel.removeReadLater(target.article)
            is IntegratedTarget.Reddit -> redditViewModel.removeReadLater(target.article)
            is IntegratedTarget.YouTube -> youtubeViewModel.toggleWatchLater(target.video)
            is IntegratedTarget.Mail -> mailViewModel.toggleReadLater(target.thread)
            null -> Unit
          }
        },
        onToggleMailStarred = { item ->
          (targetsByKey[item.key] as? IntegratedTarget.Mail)?.let { mailViewModel.toggleStarred(it.thread) }
        },
        onArchive = { item ->
          (targetsByKey[item.key] as? IntegratedTarget.Mail)?.let { mailViewModel.archive(it.thread) }
        },
        onOpen = { item ->
          when (val target = targetsByKey[item.key]) {
            is IntegratedTarget.Rss -> onOpenArticle(target.article)
            is IntegratedTarget.Reddit -> onOpenArticle(target.article)
            is IntegratedTarget.Mail -> onOpenMail(target.thread)
            is IntegratedTarget.YouTube -> runCatching {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.video.url)))
            }
            null -> Unit
          }
        },
        actionsForItem = { item ->
          targetsByKey[item.key]?.let { target ->
            integratedItemActions(
              target = target,
              redditState = redditState,
              onOpenArticle = onOpenArticle,
              onSummarize = onSummarize,
              onSubscribeRedditThread = redditViewModel::subscribeThread,
              onUnsubscribeRedditThread = redditViewModel::unsubscribeThread,
            )
          }.orEmpty()
        },
      )
    }
    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter),
    )
  }
}

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

internal fun integratedItemActions(
  target: IntegratedTarget,
  redditState: RedditUiState,
  onOpenArticle: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onSubscribeRedditThread: (Article) -> Unit,
  onUnsubscribeRedditThread: (Article) -> Unit,
): List<IntegratedItemAction> = when (target) {
  is IntegratedTarget.Rss -> listOf(
    IntegratedItemAction("はてなブックマークコメントを見る") {
      onOpenArticle(target.article.withHatenaBookmarkCommentsUrl())
    },
  )

  is IntegratedTarget.Reddit -> buildList {
    add(
      IntegratedItemAction("はてなブックマークコメントを見る") {
        onOpenArticle(target.article.withHatenaBookmarkCommentsUrl())
      },
    )
    add(
      IntegratedItemAction("要約") {
        onSummarize(target.article)
      },
    )
    val threadId = redditThreadId(target.article.url)
    if (threadId != null) {
      val subscribed = redditState.subscriptions.any { subscription ->
        subscription.kind == RedditSubscriptionKind.THREAD &&
          redditThreadId(subscription.feedUrl) == threadId
      }
      add(
        if (subscribed) {
          IntegratedItemAction("スレッドの購読を解除") {
            onUnsubscribeRedditThread(target.article)
          }
        } else {
          IntegratedItemAction("スレッドを購読") {
            onSubscribeRedditThread(target.article)
          }
        },
      )
    }
  }

  is IntegratedTarget.YouTube,
  is IntegratedTarget.Mail -> emptyList()
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

private fun Article.withHatenaBookmarkCommentsUrl(): Article = copy(
  url = "https://b.hatena.ne.jp/entry?url=${Uri.encode(url)}",
)
