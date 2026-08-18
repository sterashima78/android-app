package dev.terashima.yomitorirss.feature.reddit

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.terashima.yomitorirss.core.designsystem.PullToRefreshContainer
import dev.terashima.yomitorirss.feature.article.Article

class RedditRouteController internal constructor() {
  internal var showMarkAllReadConfirmation by mutableStateOf(false)

  fun requestMarkAllRead() {
    showMarkAllReadConfirmation = true
  }
}

@Composable
fun rememberRedditRouteController(): RedditRouteController = remember { RedditRouteController() }

@Composable
fun RedditRoute(
  modifier: Modifier,
  tab: RedditTab,
  redditViewModel: RedditViewModel,
  controller: RedditRouteController,
  onOpen: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
) {
  val state by redditViewModel.state.collectAsState()

  if (!state.initialized) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
  } else {
    PullToRefreshContainer(
      modifier = modifier,
      isRefreshing = state.refreshing,
      onRefresh = redditViewModel::refresh,
    ) {
      RedditScreen(
        modifier = Modifier.fillMaxSize(),
        tab = tab,
        state = state,
        onMarkRead = redditViewModel::markRead,
        onSaveAndRead = redditViewModel::saveAndRead,
        onReadLater = redditViewModel::readLater,
        onUnsave = redditViewModel::unsave,
        onRemoveReadLater = redditViewModel::removeReadLater,
        onOpen = onOpen,
        onSummarize = onSummarize,
        onSubscribeThread = redditViewModel::subscribeThread,
        onUnsubscribeThread = redditViewModel::unsubscribeThread,
        onAddCommunity = redditViewModel::addCommunity,
        onDeleteSubscription = redditViewModel::deleteSubscription,
      )
    }
  }

  if (controller.showMarkAllReadConfirmation) {
    AlertDialog(
      onDismissRequest = { controller.showMarkAllReadConfirmation = false },
      title = { Text("すべて既読にしますか？") },
      text = { Text("この画面の未読をすべて既読にします。") },
      confirmButton = {
        TextButton(
          onClick = {
            controller.showMarkAllReadConfirmation = false
            redditViewModel.markAllUnreadAsRead()
          },
        ) {
          Text("すべて既読")
        }
      },
      dismissButton = {
        TextButton(onClick = { controller.showMarkAllReadConfirmation = false }) {
          Text("キャンセル")
        }
      },
    )
  }
}
