package dev.terashima.yomitorirss.feature.mail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.core.designsystem.PullToRefreshContainer
import dev.terashima.yomitorirss.core.designsystem.SwipeAction
import dev.terashima.yomitorirss.core.designsystem.SwipeActionListItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MailScreen(
  state: MailUiState,
  onAddAccount: () -> Unit,
  onRemoveSelectedAccount: () -> Unit,
  onSelectAccount: (String?) -> Unit,
  onSelectMailbox: (Mailbox) -> Unit,
  onUpdateQuery: (String) -> Unit,
  onSearch: () -> Unit,
  onRefresh: () -> Unit,
  onOpenThread: (MailThread) -> Unit,
  onCloseThread: () -> Unit,
  onToggleRead: (MailThread) -> Unit,
  onToggleStarred: (MailThread) -> Unit,
  onArchive: (MailThread) -> Unit,
  onTrash: (MailThread) -> Unit,
  onApplyLabel: (MailThread, String) -> Unit,
  onDismissMessage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      if (state.accounts.isNotEmpty() && state.selectedThread == null) {
        NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
          MAIL_TRIAGE_MAILBOXES.forEach { mailbox ->
            NavigationBarItem(
              selected = state.mailbox == mailbox,
              onClick = { onSelectMailbox(mailbox) },
              icon = {
                Icon(
                  imageVector = when (mailbox) {
                    Mailbox.UNREAD -> Icons.Default.MarkEmailUnread
                    Mailbox.STARRED -> Icons.Default.Star
                    Mailbox.ALL_MAIL -> Icons.Default.Archive
                    Mailbox.INBOX -> Icons.Default.MarkEmailRead
                  },
                  contentDescription = mailbox.label,
                )
              },
              label = { Text(mailbox.label, maxLines = 1) },
            )
          }
        }
      }
    },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      state.message?.let { message ->
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
          )
          TextButton(onClick = onDismissMessage) { Text("閉じる") }
        }
      }

      if (state.accounts.isEmpty() && state.initialized) {
        EmptyMailState(onAddAccount = onAddAccount, loading = state.loading)
        return@Column
      }

      AccountBar(
        state = state,
        onAddAccount = onAddAccount,
        onRemoveSelectedAccount = onRemoveSelectedAccount,
        onSelectAccount = onSelectAccount,
      )

      MailSyncStatus(
        accounts = state.accounts,
        onRetry = onRefresh,
      )

      state.selectedThread?.let { thread ->
        ThreadDetail(
          thread = thread,
          labels = state.labels,
          loading = state.loading,
          onBack = onCloseThread,
          onToggleRead = onToggleRead,
          onToggleStarred = onToggleStarred,
          onArchive = onArchive,
          onTrash = onTrash,
          onApplyLabel = onApplyLabel,
        )
        return@Column
      }

      OutlinedTextField(
        value = state.query,
        onValueChange = onUpdateQuery,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        singleLine = true,
        label = { Text("Gmail を検索") },
        trailingIcon = {
          IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "検索") }
        },
      )

      val refreshing = state.accounts.any {
        it.syncState == MailSyncState.SYNCING || it.syncState == MailSyncState.WAITING_FOR_NETWORK
      }
      PullToRefreshContainer(
        modifier = Modifier.weight(1f),
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        enabled = !state.loading,
      ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
          if (state.loading && state.threads.isEmpty()) {
            item {
              Column(
                modifier = Modifier.fillParentMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
              ) {
                CircularProgressIndicator()
              }
            }
          } else if (state.threads.isEmpty()) {
            item {
              Column(
                modifier = Modifier.fillParentMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
              ) {
                Text(
                  when (state.mailbox) {
                    Mailbox.UNREAD -> "未読メールはありません"
                    Mailbox.STARRED -> "スター付きメールはありません"
                    Mailbox.ALL_MAIL -> "アーカイブされたメールはありません"
                    Mailbox.INBOX -> "表示するメールはありません"
                  },
                )
              }
            }
          } else {
            items(
              items = state.threads,
              key = { "${it.accountId}:${it.id}" },
            ) { thread ->
              SwipeThreadRow(
                thread = thread,
                mailbox = state.mailbox,
                account = state.accounts.firstOrNull { it.id == thread.accountId },
                onOpen = { onOpenThread(thread) },
                onToggleRead = { onToggleRead(thread) },
                onToggleStarred = { onToggleStarred(thread) },
                onArchive = { onArchive(thread) },
                onRestoreToInbox = { onApplyLabel(thread, "INBOX") },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun MailSyncStatus(
  accounts: List<MailAccount>,
  onRetry: () -> Unit,
) {
  val active = accounts.filter { account ->
    account.syncState == MailSyncState.SYNCING || account.syncState == MailSyncState.WAITING_FOR_NETWORK
  }
  val failed = accounts.filter { it.syncState == MailSyncState.ERROR }
  if (active.isEmpty() && failed.isEmpty()) return

  val processed = active.sumOf(MailAccount::syncProcessedThreads)
  val waitingForNetwork = active.any { it.syncState == MailSyncState.WAITING_FOR_NETWORK }
  val error = failed.firstOrNull()?.syncError

  Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (active.isNotEmpty()) {
        CircularProgressIndicator(
          modifier = Modifier.width(20.dp).height(20.dp),
          strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(12.dp))
      }
      Column(modifier = Modifier.weight(1f)) {
        if (active.isNotEmpty()) {
          Text(
            if (waitingForNetwork) "ネットワークを待ってメール同期を再試行します" else "メールをバックグラウンドで同期しています",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            if (processed > 0) {
              "$processed スレッド取得済み。取得したメールから順次表示します。"
            } else {
              "アプリを閉じたり別のアプリを開いても同期は継続します。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (failed.isNotEmpty()) {
          Text(
            text = error?.takeIf(String::isNotBlank) ?: "メールのバックグラウンド同期に失敗しました",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
      if (failed.isNotEmpty()) {
        TextButton(onClick = onRetry) { Text("再試行") }
      }
    }
  }
}

@Composable
private fun EmptyMailState(onAddAccount: () -> Unit, loading: Boolean) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text("Gmail アカウントを追加すると、複数アカウントの受信トレイをまとめて表示できます。")
    Spacer(Modifier.height(16.dp))
    Button(onClick = onAddAccount, enabled = !loading) {
      Icon(Icons.Default.Add, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text("Gmail アカウントを追加")
    }
    if (loading) {
      Spacer(Modifier.height(16.dp))
      CircularProgressIndicator()
    }
  }
}

@Composable
private fun AccountBar(
  state: MailUiState,
  onAddAccount: () -> Unit,
  onRemoveSelectedAccount: () -> Unit,
  onSelectAccount: (String?) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    FilterChip(
      selected = state.selectedAccountId == null,
      onClick = { onSelectAccount(null) },
      label = { Text("統合") },
    )
    state.accounts.forEach { account ->
      FilterChip(
        selected = state.selectedAccountId == account.id,
        onClick = { onSelectAccount(account.id) },
        label = { Text(account.displayName ?: account.email, maxLines = 1) },
      )
    }
    TextButton(onClick = onAddAccount) {
      Icon(Icons.Default.Add, contentDescription = null)
      Text("追加")
    }
    if (state.selectedAccountId != null) {
      TextButton(onClick = onRemoveSelectedAccount) { Text("削除") }
    }
  }
}

@Composable
private fun LazyItemScope.SwipeThreadRow(
  thread: MailThread,
  mailbox: Mailbox,
  account: MailAccount?,
  onOpen: () -> Unit,
  onToggleRead: () -> Unit,
  onToggleStarred: () -> Unit,
  onArchive: () -> Unit,
  onRestoreToInbox: () -> Unit,
) {
  val left = when (mailbox) {
    Mailbox.UNREAD -> SwipeAction(
      label = "既読",
      color = MaterialTheme.colorScheme.primary,
      dismissesItem = true,
      onCommit = onToggleRead,
    )
    Mailbox.STARRED -> SwipeAction(
      label = "スター解除",
      color = MaterialTheme.colorScheme.primary,
      dismissesItem = true,
      onCommit = onToggleStarred,
    )
    Mailbox.ALL_MAIL -> SwipeAction(
      label = "受信トレイ",
      color = MaterialTheme.colorScheme.primary,
      dismissesItem = true,
      onCommit = onRestoreToInbox,
    )
    Mailbox.INBOX -> SwipeAction(
      label = "アーカイブ",
      color = MaterialTheme.colorScheme.primary,
      dismissesItem = true,
      onCommit = onArchive,
    )
  }
  val right = SwipeAction(
    label = if (thread.isStarred) "スター解除" else "スター",
    color = MaterialTheme.colorScheme.secondary,
    dismissesItem = mailbox == Mailbox.STARRED && thread.isStarred,
    onCommit = onToggleStarred,
  )
  val farRight = when (mailbox) {
    Mailbox.UNREAD -> SwipeAction(
      label = "アーカイブ",
      color = MaterialTheme.colorScheme.tertiary,
      dismissesItem = true,
      onCommit = onArchive,
    )
    Mailbox.STARRED -> SwipeAction(
      label = "アーカイブ",
      color = MaterialTheme.colorScheme.tertiary,
      dismissesItem = false,
      onCommit = onArchive,
    )
    Mailbox.ALL_MAIL -> null
    Mailbox.INBOX -> null
  }

  SwipeActionListItem(
    itemKey = "${thread.accountId}:${thread.id}",
    left = left,
    right = right,
    farRight = farRight,
  ) {
    ThreadRow(
      thread = thread,
      account = account,
      onOpen = onOpen,
      onToggleStarred = onToggleStarred,
    )
  }
}

@Composable
private fun ThreadRow(
  thread: MailThread,
  account: MailAccount?,
  onOpen: () -> Unit,
  onToggleStarred: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = thread.subject,
        fontWeight = if (thread.isUnread) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (account != null) {
        Text(
          text = account.email,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
        )
      }
      Text(
        text = thread.snippet,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = formatTime(thread.lastMessageAtEpochMillis),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    IconButton(onClick = onToggleStarred) {
      Icon(
        imageVector = if (thread.isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
        contentDescription = if (thread.isStarred) "スターを外す" else "スター",
      )
    }
  }
}

@Composable
private fun ThreadDetail(
  thread: MailThread,
  labels: List<MailLabel>,
  loading: Boolean,
  onBack: () -> Unit,
  onToggleRead: (MailThread) -> Unit,
  onToggleStarred: (MailThread) -> Unit,
  onArchive: (MailThread) -> Unit,
  onTrash: (MailThread) -> Unit,
  onApplyLabel: (MailThread, String) -> Unit,
) {
  var labelMenu by remember(thread.accountId, thread.id) { mutableStateOf(false) }
  Column(modifier = Modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "戻る") }
      Text(
        text = thread.subject,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.titleMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      IconButton(onClick = { onToggleRead(thread) }, enabled = !loading) {
        Icon(
          if (thread.isUnread) Icons.Default.MarkEmailRead else Icons.Default.MarkEmailUnread,
          contentDescription = if (thread.isUnread) "既読" else "未読",
        )
      }
      IconButton(onClick = { onToggleStarred(thread) }, enabled = !loading) {
        Icon(
          if (thread.isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
          contentDescription = "スター",
        )
      }
      IconButton(onClick = { onArchive(thread) }, enabled = !loading) {
        Icon(Icons.Default.Archive, contentDescription = "アーカイブ")
      }
      Column {
        IconButton(onClick = { labelMenu = true }, enabled = !loading) {
          Icon(Icons.Default.Label, contentDescription = "ラベル")
        }
        DropdownMenu(expanded = labelMenu, onDismissRequest = { labelMenu = false }) {
          val userLabels = labels.filter { it.type != "system" }
          if (userLabels.isEmpty()) {
            DropdownMenuItem(text = { Text("ユーザーラベルなし") }, onClick = { labelMenu = false })
          } else {
            userLabels.forEach { label ->
              DropdownMenuItem(
                text = { Text(label.name) },
                onClick = {
                  labelMenu = false
                  onApplyLabel(thread, label.id)
                },
              )
            }
          }
        }
      }
      IconButton(onClick = { onTrash(thread) }, enabled = !loading) {
        Icon(Icons.Default.Delete, contentDescription = "ゴミ箱")
      }
    }
    HorizontalDivider()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
      items(thread.messages, key = { it.id }) { message ->
        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
          Column(modifier = Modifier.padding(16.dp)) {
            Text(message.sender.ifBlank { "送信者不明" }, fontWeight = FontWeight.SemiBold)
            Text(
              formatTime(message.receivedAtEpochMillis),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            MailMessageBody(message)
          }
        }
      }
    }
  }
}

private fun formatTime(epochMillis: Long): String {
  if (epochMillis <= 0L) return ""
  return runCatching {
    FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
  }.getOrDefault("")
}

private val MAIL_TRIAGE_MAILBOXES = listOf(Mailbox.UNREAD, Mailbox.STARRED, Mailbox.ALL_MAIL)
private val FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")