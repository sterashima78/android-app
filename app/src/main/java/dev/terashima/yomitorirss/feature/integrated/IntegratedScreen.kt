@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.terashima.yomitorirss.feature.integrated

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.core.designsystem.PullToRefreshContainer
import dev.terashima.yomitorirss.core.designsystem.SwipeAction
import dev.terashima.yomitorirss.core.designsystem.SwipeActionListItem
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.mail.MailThread
import dev.terashima.yomitorirss.feature.mail.MailUiState
import dev.terashima.yomitorirss.feature.reddit.RedditUiState
import dev.terashima.yomitorirss.feature.rss.RssUiState
import dev.terashima.yomitorirss.feature.youtube.YouTubeUiState
import dev.terashima.yomitorirss.feature.youtube.YouTubeVideo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class IntegratedTab(val label: String) {
  UNREAD("未読"),
  READ_LATER("あとで読む"),
}

enum class IntegratedSource(val label: String) {
  ALL("すべて"),
  RSS("RSS"),
  REDDIT("Reddit"),
  YOUTUBE("YouTube"),
  MAIL("メール"),
}

sealed interface IntegratedItem {
  val key: String
  val source: IntegratedSource
  val title: String
  val subtitle: String
  val timestamp: Long

  data class Rss(val article: Article) : IntegratedItem {
    override val key = "rss:${article.id}"
    override val source = IntegratedSource.RSS
    override val title = article.title
    override val subtitle = article.sourceTitle
    override val timestamp = article.eventTimeMillis()
  }

  data class Reddit(val article: Article) : IntegratedItem {
    override val key = "reddit:${article.id}"
    override val source = IntegratedSource.REDDIT
    override val title = article.title
    override val subtitle = article.sourceTitle
    override val timestamp = article.eventTimeMillis()
  }

  data class YouTube(val video: YouTubeVideo) : IntegratedItem {
    override val key = "youtube:${video.id}"
    override val source = IntegratedSource.YOUTUBE
    override val title = video.title
    override val subtitle = video.channelTitle
    override val timestamp = video.publishedAtEpochMillis
  }

  data class Mail(
    val thread: MailThread,
    val accountLabel: String,
  ) : IntegratedItem {
    override val key = "mail:${thread.accountId}:${thread.id}"
    override val source = IntegratedSource.MAIL
    override val title = thread.subject.ifBlank { "（件名なし）" }
    override val subtitle = buildString {
      append(accountLabel)
      if (thread.snippet.isNotBlank()) {
        append(" · ")
        append(thread.snippet)
      }
    }
    override val timestamp = thread.lastMessageAtEpochMillis
  }
}

data class IntegratedItemAction(
  val label: String,
  val action: () -> Unit,
)

internal enum class IntegratedSwipeOperation {
  MARK_PROCESSED,
  SAVE,
  DEFER,
  UNSAVE,
  REMOVE_DEFERRED,
  TOGGLE_STARRED,
  ARCHIVE,
}

internal enum class IntegratedSwipeTone {
  PRIMARY,
  SECONDARY,
  TERTIARY,
  ERROR,
}

internal data class IntegratedSwipeActionSpec(
  val label: String,
  val tone: IntegratedSwipeTone,
  val dismissesItem: Boolean,
  val operation: IntegratedSwipeOperation,
)

internal data class IntegratedSwipeActions(
  val left: IntegratedSwipeActionSpec?,
  val right: IntegratedSwipeActionSpec?,
  val farRight: IntegratedSwipeActionSpec?,
)

@Composable
fun IntegratedScreen(
  selectedTab: IntegratedTab,
  rssState: RssUiState,
  redditState: RedditUiState,
  youtubeState: YouTubeUiState,
  mailState: MailUiState,
  isRefreshing: Boolean,
  onSelectTab: (IntegratedTab) -> Unit,
  onRefresh: () -> Unit,
  onMarkProcessed: (IntegratedItem) -> Unit,
  onSave: (IntegratedItem) -> Unit,
  onDefer: (IntegratedItem) -> Unit,
  onUnsave: (IntegratedItem) -> Unit,
  onRemoveDeferred: (IntegratedItem) -> Unit,
  onToggleMailStarred: (IntegratedItem.Mail) -> Unit,
  onArchive: (IntegratedItem.Mail) -> Unit,
  onOpen: (IntegratedItem) -> Unit,
  actionsForItem: (IntegratedItem) -> List<IntegratedItemAction> = { emptyList() },
  modifier: Modifier = Modifier,
) {
  val items = integratedItems(
    rssState = rssState,
    redditState = redditState,
    youtubeState = youtubeState,
    mailState = mailState,
    tab = selectedTab,
  )
  var selectedSourceName by rememberSaveable { mutableStateOf(IntegratedSource.ALL.name) }
  val selectedSource = IntegratedSource.entries.firstOrNull { it.name == selectedSourceName }
    ?: IntegratedSource.ALL
  val visibleItems = if (selectedSource == IntegratedSource.ALL) {
    items
  } else {
    items.filter { it.source == selectedSource }
  }

  Column(modifier = modifier.fillMaxSize()) {
    InboxSummary(total = items.size, tab = selectedTab)
    SourceFilters(
      selected = selectedSource,
      counts = items.groupingBy(IntegratedItem::source).eachCount(),
      onSelect = { selectedSourceName = it.name },
    )
    PullToRefreshContainer(
      modifier = Modifier.weight(1f),
      isRefreshing = isRefreshing,
      onRefresh = onRefresh,
    ) {
      LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
      ) {
        if (visibleItems.isEmpty()) {
          item {
            Box(
              modifier = Modifier.fillParentMaxSize().padding(24.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = emptyMessage(selectedTab, selectedSource),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          items(visibleItems, key = IntegratedItem::key) { item ->
            IntegratedSwipeRow(
              item = item,
              tab = selectedTab,
              onMarkProcessed = { onMarkProcessed(item) },
              onSave = { onSave(item) },
              onDefer = { onDefer(item) },
              onUnsave = { onUnsave(item) },
              onRemoveDeferred = { onRemoveDeferred(item) },
              onToggleMailStarred = if (item is IntegratedItem.Mail) {
                { onToggleMailStarred(item) }
              } else {
                null
              },
              onArchive = if (item is IntegratedItem.Mail) {
                { onArchive(item) }
              } else {
                null
              },
              onOpen = { onOpen(item) },
              actions = actionsForItem(item),
            )
          }
        }
      }
    }
    NavigationBar {
      IntegratedTab.entries.forEach { tab ->
        NavigationBarItem(
          selected = selectedTab == tab,
          onClick = { onSelectTab(tab) },
          icon = {
            Icon(
              imageVector = when (tab) {
                IntegratedTab.UNREAD -> Icons.Default.RssFeed
                IntegratedTab.READ_LATER -> Icons.Default.AccessTime
              },
              contentDescription = tab.label,
            )
          },
          label = { Text(tab.label, maxLines = 1) },
        )
      }
    }
  }
}

@Composable
private fun InboxSummary(total: Int, tab: IntegratedTab) {
  Surface(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    shape = MaterialTheme.shapes.large,
    tonalElevation = 1.dp,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = if (tab == IntegratedTab.UNREAD) "未処理" else "あとで読む",
          style = MaterialTheme.typography.labelLarge,
        )
        Text(
          text = when (tab) {
            IntegratedTab.UNREAD -> if (total == 0) "インボックスゼロ" else "$total 件を仕分けできます"
            IntegratedTab.READ_LATER -> if (total == 0) "保留中のアイテムはありません" else "$total 件をあとで確認できます"
          },
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
      }
      Text(
        text = total.toString(),
        style = MaterialTheme.typography.headlineMedium,
        color = if (total == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun SourceFilters(
  selected: IntegratedSource,
  counts: Map<IntegratedSource, Int>,
  onSelect: (IntegratedSource) -> Unit,
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .horizontalScroll(rememberScrollState())
      .padding(horizontal = 12.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    IntegratedSource.entries.forEach { source ->
      val count = if (source == IntegratedSource.ALL) counts.values.sum() else counts[source] ?: 0
      FilterChip(
        selected = selected == source,
        onClick = { onSelect(source) },
        leadingIcon = if (source == IntegratedSource.ALL) {
          null
        } else {
          { Icon(source.icon(), contentDescription = null) }
        },
        label = { Text("${source.label} $count") },
      )
    }
  }
}

@Composable
private fun LazyItemScope.IntegratedSwipeRow(
  item: IntegratedItem,
  tab: IntegratedTab,
  onMarkProcessed: () -> Unit,
  onSave: () -> Unit,
  onDefer: () -> Unit,
  onUnsave: () -> Unit,
  onRemoveDeferred: () -> Unit,
  onToggleMailStarred: (() -> Unit)?,
  onArchive: (() -> Unit)?,
  onOpen: () -> Unit,
  actions: List<IntegratedItemAction>,
) {
  var menuOpen by remember(item.key) { mutableStateOf(false) }
  val actionSpecs = integratedSwipeActions(item, tab)
  val onOperation: (IntegratedSwipeOperation) -> Unit = { operation ->
    when (operation) {
      IntegratedSwipeOperation.MARK_PROCESSED -> onMarkProcessed()
      IntegratedSwipeOperation.SAVE -> onSave()
      IntegratedSwipeOperation.DEFER -> onDefer()
      IntegratedSwipeOperation.UNSAVE -> onUnsave()
      IntegratedSwipeOperation.REMOVE_DEFERRED -> onRemoveDeferred()
      IntegratedSwipeOperation.TOGGLE_STARRED -> onToggleMailStarred?.invoke()
      IntegratedSwipeOperation.ARCHIVE -> onArchive?.invoke()
    }
  }

  SwipeActionListItem(
    itemKey = item.key,
    left = actionSpecs.left?.toSwipeAction(onOperation),
    right = actionSpecs.right?.toSwipeAction(onOperation),
    farRight = actionSpecs.farRight?.toSwipeAction(onOperation),
  ) {
    Box {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .combinedClickable(
            onClick = onOpen,
            onLongClick = if (actions.isEmpty()) null else ({ menuOpen = true }),
          )
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
      ) {
        Icon(
          imageVector = item.source.icon(),
          contentDescription = item.source.label,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = item.source.label,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.weight(1f))
            Text(
              text = formatTime(item.timestamp),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Spacer(Modifier.height(4.dp))
          Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (item.subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
              text = item.subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        if (item is IntegratedItem.Mail) {
          IconButton(
            onClick = if (tab == IntegratedTab.UNREAD) onDefer else onRemoveDeferred,
          ) {
            Icon(
              imageVector = Icons.Default.AccessTime,
              contentDescription = if (item.thread.isReadLater) "あとで読むを解除" else "あとで読む",
              tint = if (item.thread.isReadLater) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
            )
          }
          IconButton(onClick = { onToggleMailStarred?.invoke() }) {
            Icon(
              imageVector = if (item.thread.isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
              contentDescription = if (item.thread.isStarred) "スターを外す" else "スター",
            )
          }
        }
      }
      DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false },
      ) {
        actions.forEach { itemAction ->
          DropdownMenuItem(
            text = { Text(itemAction.label) },
            onClick = {
              menuOpen = false
              itemAction.action()
            },
          )
        }
      }
    }
  }
}

@Composable
private fun IntegratedSwipeActionSpec.toSwipeAction(
  onOperation: (IntegratedSwipeOperation) -> Unit,
): SwipeAction = SwipeAction(
  label = label,
  color = tone.color(),
  dismissesItem = dismissesItem,
  onCommit = { onOperation(operation) },
)

@Composable
private fun IntegratedSwipeTone.color(): Color = when (this) {
  IntegratedSwipeTone.PRIMARY -> MaterialTheme.colorScheme.primary
  IntegratedSwipeTone.SECONDARY -> MaterialTheme.colorScheme.secondary
  IntegratedSwipeTone.TERTIARY -> MaterialTheme.colorScheme.tertiary
  IntegratedSwipeTone.ERROR -> MaterialTheme.colorScheme.error
}

internal fun integratedSwipeActions(
  item: IntegratedItem,
  tab: IntegratedTab,
): IntegratedSwipeActions = when (tab) {
  IntegratedTab.UNREAD -> when (item) {
    is IntegratedItem.Rss,
    is IntegratedItem.Reddit -> IntegratedSwipeActions(
      left = IntegratedSwipeActionSpec(
        label = "既読",
        tone = IntegratedSwipeTone.PRIMARY,
        dismissesItem = true,
        operation = IntegratedSwipeOperation.MARK_PROCESSED,
      ),
      right = IntegratedSwipeActionSpec(
        label = "ブックマーク",
        tone = IntegratedSwipeTone.SECONDARY,
        dismissesItem = true,
        operation = IntegratedSwipeOperation.SAVE,
      ),
      farRight = IntegratedSwipeActionSpec(
        label = "あとで読む",
        tone = IntegratedSwipeTone.TERTIARY,
        dismissesItem = true,
        operation = IntegratedSwipeOperation.DEFER,
      ),
    )

    is IntegratedItem.YouTube -> IntegratedSwipeActions(
      left = IntegratedSwipeActionSpec(
        label = "既読",
        tone = IntegratedSwipeTone.PRIMARY,
        dismissesItem = false,
        operation = IntegratedSwipeOperation.MARK_PROCESSED,
      ),
      right = IntegratedSwipeActionSpec(
        label = "保存",
        tone = IntegratedSwipeTone.TERTIARY,
        dismissesItem = false,
        operation = IntegratedSwipeOperation.SAVE,
      ),
      farRight = IntegratedSwipeActionSpec(
        label = "あとで見る",
        tone = IntegratedSwipeTone.SECONDARY,
        dismissesItem = false,
        operation = IntegratedSwipeOperation.DEFER,
      ),
    )

    is IntegratedItem.Mail -> IntegratedSwipeActions(
      left = IntegratedSwipeActionSpec(
        label = "既読",
        tone = IntegratedSwipeTone.PRIMARY,
        dismissesItem = true,
        operation = IntegratedSwipeOperation.MARK_PROCESSED,
      ),
      right = IntegratedSwipeActionSpec(
        label = if (item.thread.isReadLater) "あとで読む解除" else "あとで読む",
        tone = IntegratedSwipeTone.SECONDARY,
        dismissesItem = !item.thread.isReadLater,
        operation = if (item.thread.isReadLater) {
          IntegratedSwipeOperation.REMOVE_DEFERRED
        } else {
          IntegratedSwipeOperation.DEFER
        },
      ),
      farRight = IntegratedSwipeActionSpec(
        label = "アーカイブ",
        tone = IntegratedSwipeTone.TERTIARY,
        dismissesItem = true,
        operation = IntegratedSwipeOperation.ARCHIVE,
      ),
    )
  }

  IntegratedTab.READ_LATER -> when (item) {
    is IntegratedItem.Rss,
    is IntegratedItem.Reddit -> IntegratedSwipeActions(
      left = IntegratedSwipeActionSpec(
        label = "ブックマーク解除",
        tone = IntegratedSwipeTone.ERROR,
        dismissesItem = true,
        operation = IntegratedSwipeOperation.UNSAVE,
      ),
      right = IntegratedSwipeActionSpec(
        label = "未分類へ",
        tone = IntegratedSwipeTone.SECONDARY,
        dismissesItem = true,
        operation = IntegratedSwipeOperation.REMOVE_DEFERRED,
      ),
      farRight = null,
    )

    is IntegratedItem.YouTube -> IntegratedSwipeActions(
      left = IntegratedSwipeActionSpec(
        label = "既読",
        tone = IntegratedSwipeTone.PRIMARY,
        dismissesItem = false,
        operation = IntegratedSwipeOperation.MARK_PROCESSED,
      ),
      right = IntegratedSwipeActionSpec(
        label = "保存",
        tone = IntegratedSwipeTone.TERTIARY,
        dismissesItem = false,
        operation = IntegratedSwipeOperation.SAVE,
      ),
      farRight = IntegratedSwipeActionSpec(
        label = "未読へ戻す",
        tone = IntegratedSwipeTone.SECONDARY,
        dismissesItem = false,
        operation = IntegratedSwipeOperation.REMOVE_DEFERRED,
      ),
    )

    is IntegratedItem.Mail -> IntegratedSwipeActions(
      left = IntegratedSwipeActionSpec(
        label = "あとで読む解除",
        tone = IntegratedSwipeTone.PRIMARY,
        dismissesItem = true,
        operation = IntegratedSwipeOperation.REMOVE_DEFERRED,
      ),
      right = IntegratedSwipeActionSpec(
        label = if (item.thread.isStarred) "スター解除" else "スター",
        tone = IntegratedSwipeTone.SECONDARY,
        dismissesItem = false,
        operation = IntegratedSwipeOperation.TOGGLE_STARRED,
      ),
      farRight = IntegratedSwipeActionSpec(
        label = "アーカイブ",
        tone = IntegratedSwipeTone.TERTIARY,
        dismissesItem = false,
        operation = IntegratedSwipeOperation.ARCHIVE,
      ),
    )
  }
}

internal fun integratedItems(
  rssState: RssUiState,
  redditState: RedditUiState,
  youtubeState: YouTubeUiState,
  mailState: MailUiState,
  tab: IntegratedTab = IntegratedTab.UNREAD,
): List<IntegratedItem> {
  val accountLabels = mailState.accounts.associate { account ->
    account.id to (account.displayName?.takeIf(String::isNotBlank) ?: account.email)
  }
  val items = buildList {
    when (tab) {
      IntegratedTab.UNREAD -> {
        rssState.unread
          .filterNot { it.id in rssState.hiddenArticleIds }
          .forEach { add(IntegratedItem.Rss(it)) }
        redditState.unread
          .filterNot { it.id in redditState.hiddenArticleIds }
          .forEach { add(IntegratedItem.Reddit(it)) }
        youtubeState.unread.forEach { add(IntegratedItem.YouTube(it)) }
        mailState.threads
          .filter { it.isUnread && it.isInInbox }
          .forEach { thread ->
            add(
              IntegratedItem.Mail(
                thread = thread,
                accountLabel = accountLabels[thread.accountId] ?: thread.accountId,
              ),
            )
          }
      }

      IntegratedTab.READ_LATER -> {
        rssState.readLater
          .filterNot { it.article.id in rssState.hiddenArticleIds }
          .forEach { add(IntegratedItem.Rss(it.article)) }
        redditState.readLater
          .filterNot { it.article.id in redditState.hiddenArticleIds }
          .forEach { add(IntegratedItem.Reddit(it.article)) }
        youtubeState.watchLater.forEach { add(IntegratedItem.YouTube(it)) }
        mailState.threads
          .filter(MailThread::isReadLater)
          .forEach { thread ->
            add(
              IntegratedItem.Mail(
                thread = thread,
                accountLabel = accountLabels[thread.accountId] ?: thread.accountId,
              ),
            )
          }
      }
    }
  }
  return when (tab) {
    IntegratedTab.UNREAD -> items.sortedByDescending(IntegratedItem::timestamp)
    IntegratedTab.READ_LATER -> items.sortedBy(IntegratedItem::timestamp)
  }
}

private fun emptyMessage(tab: IntegratedTab, source: IntegratedSource): String = when (tab) {
  IntegratedTab.UNREAD -> if (source == IntegratedSource.ALL) {
    "インボックスは空です"
  } else {
    "${source.label} の未処理アイテムはありません"
  }
  IntegratedTab.READ_LATER -> if (source == IntegratedSource.ALL) {
    "あとで読むアイテムはありません"
  } else {
    "${source.label} のあとで読むアイテムはありません"
  }
}

private fun IntegratedSource.icon(): ImageVector = when (this) {
  IntegratedSource.ALL -> Icons.Default.RssFeed
  IntegratedSource.RSS -> Icons.Default.RssFeed
  IntegratedSource.REDDIT -> Icons.Default.Forum
  IntegratedSource.YOUTUBE -> Icons.Default.PlayArrow
  IntegratedSource.MAIL -> Icons.Default.Email
}

private fun Article.eventTimeMillis(): Long =
  sequenceOf(publishedAt, fetchedAt)
    .mapNotNull { value -> runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() }
    .firstOrNull()
    ?: 0L

private fun formatTime(epochMillis: Long): String {
  if (epochMillis <= 0L) return ""
  return runCatching {
    TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
  }.getOrDefault("")
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm")
