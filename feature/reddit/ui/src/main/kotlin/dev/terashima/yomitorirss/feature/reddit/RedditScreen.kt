package dev.terashima.yomitorirss.feature.reddit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleList
import dev.terashima.yomitorirss.feature.article.ArticleMenuAction
import dev.terashima.yomitorirss.feature.article.SwipeChoice

enum class RedditTab(val label: String) {
  UNREAD("未読"),
  SUBSCRIPTIONS("購読管理"),
}

@Composable
fun RedditScreen(
  modifier: Modifier,
  tab: RedditTab,
  state: RedditUiState,
  onMarkRead: (Article) -> Unit,
  onSaveAndRead: (Article) -> Unit,
  onOpen: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onSubscribeThread: (Article) -> Unit,
  onUnsubscribeThread: (Article) -> Unit,
  onAddCommunity: (String) -> Unit,
  onDeleteSubscription: (RedditSubscription) -> Unit,
) {
  when (tab) {
    RedditTab.UNREAD -> RedditUnreadScreen(
      modifier = modifier,
      state = state,
      onMarkRead = onMarkRead,
      onSaveAndRead = onSaveAndRead,
      onOpen = onOpen,
      onSummarize = onSummarize,
      onSubscribeThread = onSubscribeThread,
      onUnsubscribeThread = onUnsubscribeThread,
    )

    RedditTab.SUBSCRIPTIONS -> RedditSubscriptionsScreen(
      modifier = modifier,
      subscriptions = state.subscriptions,
      onAddCommunity = onAddCommunity,
      onDeleteSubscription = onDeleteSubscription,
    )
  }
}

@Composable
private fun RedditUnreadScreen(
  modifier: Modifier,
  state: RedditUiState,
  onMarkRead: (Article) -> Unit,
  onSaveAndRead: (Article) -> Unit,
  onOpen: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onSubscribeThread: (Article) -> Unit,
  onUnsubscribeThread: (Article) -> Unit,
) {
  val subscribedThreadIds = state.subscriptions
    .filter { it.kind == RedditSubscriptionKind.THREAD }
    .mapNotNull { redditThreadId(it.feedUrl) }
    .toSet()

  ArticleList(
    modifier = modifier,
    articles = state.unread.filterNot { it.id in state.hiddenArticleIds },
    emptyText = "Redditの未読はありません",
    left = SwipeChoice("既読", MaterialTheme.colorScheme.primary, onMarkRead),
    right = SwipeChoice("ブックマーク", MaterialTheme.colorScheme.secondary, onSaveAndRead),
    onOpen = onOpen,
    onSummarize = onSummarize,
    onEditTags = {},
    extraMenuActions = { article ->
      val threadId = redditThreadId(article.url)
      if (threadId == null) {
        emptyList()
      } else if (threadId in subscribedThreadIds) {
        listOf(ArticleMenuAction("スレッドの購読を解除") { onUnsubscribeThread(article) })
      } else {
        listOf(ArticleMenuAction("スレッドを購読") { onSubscribeThread(article) })
      }
    },
  )
}

@Composable
private fun RedditSubscriptionsScreen(
  modifier: Modifier,
  subscriptions: List<RedditSubscription>,
  onAddCommunity: (String) -> Unit,
  onDeleteSubscription: (RedditSubscription) -> Unit,
) {
  var showAddDialog by remember { mutableStateOf(false) }

  Column(modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.End,
    ) {
      Button(onClick = { showAddDialog = true }) {
        Text("コミュニティを追加")
      }
    }

    if (subscriptions.isEmpty()) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Redditの購読はありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    } else {
      LazyColumn(Modifier.fillMaxSize()) {
        items(subscriptions, key = RedditSubscription::id) { subscription ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 4.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth().padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(Modifier.weight(1f)) {
                Text(subscription.title, style = MaterialTheme.typography.titleMedium)
                Text(
                  if (subscription.kind == RedditSubscriptionKind.COMMUNITY) "コミュニティ" else "スレッド",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                subscription.lastError?.let { error ->
                  Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                  )
                }
              }
              IconButton(onClick = { onDeleteSubscription(subscription) }) {
                Icon(Icons.Default.Delete, contentDescription = "購読解除")
              }
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    AddRedditCommunityDialog(
      onDismiss = { showAddDialog = false },
      onAdd = { input ->
        showAddDialog = false
        onAddCommunity(input)
      },
    )
  }
}

@Composable
private fun AddRedditCommunityDialog(
  onDismiss: () -> Unit,
  onAdd: (String) -> Unit,
) {
  var input by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Redditコミュニティを追加") },
    text = {
      OutlinedTextField(
        value = input,
        onValueChange = { input = it },
        label = { Text("r/androiddev または Reddit URL") },
        singleLine = true,
      )
    },
    confirmButton = {
      TextButton(onClick = { onAdd(input) }, enabled = input.isNotBlank()) {
        Text("追加")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  )
}
