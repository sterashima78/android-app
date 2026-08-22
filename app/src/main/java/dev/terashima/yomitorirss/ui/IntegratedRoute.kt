package dev.terashima.yomitorirss.ui

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
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedScreen
import dev.terashima.yomitorirss.feature.integrated.ui.IntegratedTab
import dev.terashima.yomitorirss.feature.mail.MailThread
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.mail.Mailbox
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel
import dev.terashima.yomitorirss.feature.youtube.YouTubeViewModel

@Composable
internal fun IntegratedRoute(
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
