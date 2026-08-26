package dev.terashima.yomitorirss.feature.rss

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.core.designsystem.PullToRefreshContainer
import dev.terashima.yomitorirss.feature.article.Article

class RssRouteController internal constructor() {
  internal var showAddFeed by mutableStateOf(false)
  internal var showMarkAllReadConfirmation by mutableStateOf(false)

  fun requestAddFeed() {
    showAddFeed = true
  }

  fun requestMarkAllRead() {
    showMarkAllReadConfirmation = true
  }
}

@Composable
fun rememberRssRouteController(): RssRouteController = remember { RssRouteController() }

@Composable
fun RssRoute(
  modifier: Modifier,
  tab: RssTab,
  rssViewModel: RssViewModel,
  feedViewModel: FeedViewModel,
  controller: RssRouteController,
  onOpen: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onEditTags: (Article) -> Unit,
  onMoveFolder: (Article) -> Unit,
) {
  val rssState by rssViewModel.state.collectAsState()
  val feedState by feedViewModel.state.collectAsState()

  if (!rssState.initialized) {
    LoadingFeature(modifier)
  } else {
    PullToRefreshContainer(
      modifier = modifier,
      isRefreshing = feedState.refreshing,
      onRefresh = feedViewModel::refresh,
    ) {
      RssScreen(
        modifier = Modifier.fillMaxSize(),
        tab = tab,
        state = rssState,
        onMarkRead = rssViewModel::markRead,
        onSaveAndRead = rssViewModel::saveAndRead,
        onReadLater = rssViewModel::readLater,
        onUnsave = rssViewModel::unsave,
        onRemoveReadLater = rssViewModel::removeReadLater,
        onOpen = onOpen,
        onSummarize = onSummarize,
        onEditTags = onEditTags,
        onMoveFolder = onMoveFolder,
        onSetContentType = rssViewModel::setArticleContentType,
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
            rssViewModel.markAllUnreadAsRead()
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

@Composable
fun FeedRoute(
  modifier: Modifier,
  feedViewModel: FeedViewModel,
  controller: RssRouteController,
  onFeedReady: () -> Unit,
) {
  val state by feedViewModel.state.collectAsState()
  var showWebScrapingRules by remember { mutableStateOf(false) }

  LaunchedEffect(state.importCompleted) {
    if (state.importCompleted) {
      onFeedReady()
      feedViewModel.consumeImportCompleted()
    }
  }
  LaunchedEffect(state.feedAdded) {
    if (state.feedAdded) {
      onFeedReady()
      feedViewModel.consumeFeedAdded()
    }
  }

  if (!state.initialized) {
    LoadingFeature(modifier)
  } else {
    Box(modifier = modifier) {
      PullToRefreshContainer(
        modifier = Modifier.fillMaxSize(),
        isRefreshing = state.refreshing,
        onRefresh = feedViewModel::refresh,
      ) {
        FeedScreen(
          modifier = Modifier.fillMaxSize(),
          feeds = state.feeds,
          folders = state.folders,
          onAdd = controller::requestAddFeed,
          onRenameFeed = feedViewModel::renameFeed,
          onDelete = feedViewModel::deleteFeed,
          onCreateFolder = feedViewModel::createFolder,
          onRenameFolder = feedViewModel::renameFolder,
          onDeleteFolder = feedViewModel::deleteFolder,
          onMoveFeed = feedViewModel::moveFeedToFolder,
          onSetFeedContentType = feedViewModel::setFeedContentType,
          onSetFolderContentType = feedViewModel::setFolderContentType,
        )
      }
      ExtendedFloatingActionButton(
        onClick = { showWebScrapingRules = true },
        icon = { Icon(Icons.Default.Tune, contentDescription = null) },
        text = { Text("Web取得ルール") },
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(16.dp),
      )
    }
  }

  if (controller.showAddFeed) {
    AddFeedDialog(
      onDismiss = { controller.showAddFeed = false },
      onAdd = {
        controller.showAddFeed = false
        feedViewModel.inspectAndAddFeed(it)
      },
    )
  }
  if (state.feedCandidates.isNotEmpty()) {
    CandidateDialog(
      candidates = state.feedCandidates,
      onDismiss = feedViewModel::dismissFeedCandidates,
      onSelect = feedViewModel::addFeedCandidate,
    )
  }
  if (showWebScrapingRules) {
    RssWebScrapingRulesUi(
      rules = state.webScrapingRules,
      testState = state.webScrapingRuleTest,
      onSave = feedViewModel::saveWebScrapingRule,
      onDelete = feedViewModel::deleteWebScrapingRule,
      onTest = feedViewModel::testWebScrapingRule,
      onClearTest = feedViewModel::clearWebScrapingRuleTest,
      onDismiss = {
        feedViewModel.clearWebScrapingRuleTest()
        showWebScrapingRules = false
      },
    )
  }
}

@Composable
private fun LoadingFeature(modifier: Modifier) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}
