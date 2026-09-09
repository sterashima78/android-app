package dev.terashima.yomitorirss.feature.integrated.ui

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
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.mail.MailViewModel
import dev.terashima.yomitorirss.feature.mail.Mailbox
import dev.terashima.yomitorirss.feature.reddit.RedditViewModel
import dev.terashima.yomitorirss.feature.rss.FeedViewModel
import dev.terashima.yomitorirss.feature.rss.RssViewModel

@Composable
fun IntegratedRoute(
  rssViewModel: RssViewModel,
  redditViewModel: RedditViewModel,
  feedViewModel: FeedViewModel,
  mailViewModel: MailViewModel,
  onOpenArticle: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onNavigateToMail: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val rssState by rssViewModel.state.collectAsState()
  val redditState by redditViewModel.state.collectAsState()
  val feedState by feedViewModel.state.collectAsState()
  val mailState by mailViewModel.state.collectAsState()
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

  val initialized = rssState.initialized &&
    redditState.initialized &&
    mailState.initialized
  val entries = integratedEntries(
    rssState = rssState,
    redditState = redditState,
    mailState = mailState,
    tab = selectedTab,
  )
  val targetsByKey = entries.associate { it.item.key to it.target }
  val dispatcher = integratedTargetDispatcher(
    rssViewModel = rssViewModel,
    redditViewModel = redditViewModel,
    mailViewModel = mailViewModel,
    onOpenArticle = onOpenArticle,
    onOpenMail = { thread ->
      mailViewModel.openThread(thread)
      onNavigateToMail()
    },
  )

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
          mailState.loading,
        onSelectTab = { selectedTabName = it.name },
        onRefresh = {
          feedViewModel.refresh()
          redditViewModel.refresh()
          mailViewModel.refresh()
        },
        onMarkProcessed = { item -> dispatcher.markProcessed(targetsByKey[item.key]) },
        onMarkUnread = { item -> dispatcher.markUnread(targetsByKey[item.key]) },
        onSave = { item -> dispatcher.save(targetsByKey[item.key]) },
        onDefer = { item -> dispatcher.defer(targetsByKey[item.key]) },
        onUnsave = { item -> dispatcher.unsave(targetsByKey[item.key]) },
        onRemoveDeferred = { item -> dispatcher.removeDeferred(targetsByKey[item.key]) },
        onToggleMailStarred = { item -> dispatcher.toggleMailStarred(targetsByKey[item.key]) },
        onArchive = { item -> dispatcher.archive(targetsByKey[item.key]) },
        onOpen = { item -> dispatcher.open(targetsByKey[item.key]) },
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