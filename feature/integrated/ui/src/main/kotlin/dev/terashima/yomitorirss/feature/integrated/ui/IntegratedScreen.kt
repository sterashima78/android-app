@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.terashima.yomitorirss.feature.integrated.ui

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

data class IntegratedItem(
  val key: String,
  val source: IntegratedSource,
  val title: String,
  val subtitle: String,
  val timestamp: Long,
  val isDeferred: Boolean = false,
  val isStarred: Boolean = false,
)

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
  items: List<IntegratedItem>,
  isRefreshing: Boolean,
  onSelectTab: (IntegratedTab) -> Unit,
  onRefresh: () -> Unit,
  onMarkProcessed: (IntegratedItem) -> Unit,
  onSave: (IntegratedItem) -> Unit,
  onDefer: (IntegratedItem) -> Unit,
  onUnsave: (IntegratedItem) -> Unit,
  onRemoveDeferred: (IntegratedItem) -> Unit,
  onToggleMailStarred: (IntegratedItem) -> Unit,
  onArchive: (IntegratedItem) -> Unit,
  onOpen: (IntegratedItem) -> Unit,
  actionsForItem: (IntegratedItem) -> List<IntegratedItemAction> = { emptyList() },
  modifier: Modifier = Modifier,
) {
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
              onToggleMailStarred = if (item.source == IntegratedSource.MAIL) {
                { onToggleMailStarred(item) }
              } else {
                null
              },
              onArchive = if (item.source == IntegratedSource.MAIL) {
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
        if (item.source == IntegratedSource.MAIL) {
          IconButton(
            onClick = if (tab == IntegratedTab.UNREAD) onDefer ?: {} else onRemoveDeferred,
          ) {
            Icon(
              imageVector = Icons.Default.AccessTime,
              contentDescription = if (item.isDeferred) "あとで読むを解除" else "あとで読む",
              tint = if (item.isDeferred) {
                MaterialTheme.colorScheme.primary
              } else {
                MaterialTheme.colorScheme.onSurfaceVariant
              },
            )
          }
          IconButton(onClick = { onToggleMailStarred?.invoke() }) {
            Icon(
              imageVector = if (item.isStarred) Icons.Default.Star else Icons.Outlined.StarBorder,
              contentDescription = if (item.isStarred) "スターを外す" else "スター",
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
  IntegratedTab.UNREAD -> when (item.source) {
    IntegratedSource.RSS,
    IntegratedSource.REDDIT -> IntegratedSwipeActions(
      left = IntegratedSwipeActionSpec("既読", IntegratedSwipeTone.PRIMARY, true, IntegratedSwipeOperation.MARK_PROCESSED),
      right = IntegratedSwipeActionSpec("ブックマーク", IntegratedSwipeTone.SECONDARY, true, IntegratedSwipeOperation.SAVE),
      farRight = IntegratedSwipeActionSpec("あとで読む", IntegratedSwipeTone.TERTIARY, true, IntegratedSwipeOperation.DEFER),
    )
    IntegratedSource.YOUTUBE -> IntegratedSwipeActions(
      left = IntegratedSwipeActionSpec("既読", IntegratedSwipeTone.PRIMARY, false, IntegratedSwipeOperation.MARK_PROCESSED),
      right = IntegratedSwipeActionSpec("保存", IntegratedSwipeTone.TERTIARY, false, IntegratedSwipeOperation.SAVE),
      farRight = IntegratedSwipeActionSpec("あとで見る", IntegratedSwipeTone.SECONDARY, false, IntegratedSwipeOperation.DEFER),
    )
    IntegratedSource.MAIL -> IntegratedSwipeActions(
      left = IntegratedSwipeActionSpec("既読", IntegratedSwipeTone.PRIMARY, true, IntegratedSwipeOperation.MARK_PROCESSED),
      right = if (item.isDeferred) {
        IntegratedSwipeActionSpec("あとで読む解除", IntegratedSwipeTone.SECONDARY, false, IntegratedSwipeOperation.REMOVE_DEFERRED)
      } else {
        IntegratedSwipeActionSpec("あとで読む", IntegratedSwipeTone.SECONDARY, true, IntegratedSwipeOperation.DEFER)
      },
      farRight = IntegratedSwipeActionSpec("アーカイブ", IntegratedSwipeTone.TERTIARY, true, IntegratedSwipeOperation.ARCHIVE),
    )
    IntegratedSource.ALL -> noSwipeActions()
  }

  IntegratedTab.READ_LATER -> when (item.source) {
    IntegratedSource.RSS,
    IntegratedSource.REDDIT -> IntegratedSwipeActions(
      left = IntegratedSwipeActionSpec("ブックマーク解除", IntegratedSwipeTone.ERROR, true, IntegratedSwipeOperation.UNSAVE),
      right = IntegratedSwipeActionSpec("未分類へ", IntegratedSwipeTone.SECONDARY, true, IntegratedSwipeOperation.REMOVE_DEFERRED),
      farRight = null,
    )
    IntegratedSource.YOUTUBE -> IntegratedSwipeActions(
      left = IntegratedSwipeActionSpec("既読", IntegratedSwipeTone.PRIMARY, false, IntegratedSwipeOperation.MARK_PROCESSED),
      right = IntegratedSwipeActionSpec("保存", IntegratedSwipeTone.TERTIARY, false, IntegratedSwipeOperation.SAVE),
      farRight = IntegratedSwipeActionSpec("未読へ戻す", IntegratedSwipeTone.SECONDARY, false, IntegratedSwipeOperation.REMOVE_DEFERRED),
    )
    IntegratedSource.MAIL -> IntegratedSwipeActions(
      left = IntegratedSwipeActionSpec("あとで読む解除", IntegratedSwipeTone.PRIMARY, true, IntegratedSwipeOperation.REMOVE_DEFERRED),
      right = IntegratedSwipeActionSpec(
        if (item.isStarred) "スター解除" else "スター",
        IntegratedSwipeTone.SECONDARY,
        false,
        IntegratedSwipeOperation.TOGGLE_STARRED,
      ),
      farRight = IntegratedSwipeActionSpec("アーカイブ", IntegratedSwipeTone.TERTIARY, false, IntegratedSwipeOperation.ARCHIVE),
    )
    IntegratedSource.ALL -> noSwipeActions()
  }
}

private fun noSwipeActions() = IntegratedSwipeActions(left = null, right = null, farRight = null)

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
  IntegratedSource.ALL,
  IntegratedSource.RSS -> Icons.Default.RssFeed
  IntegratedSource.REDDIT -> Icons.Default.Forum
  IntegratedSource.YOUTUBE -> Icons.Default.PlayArrow
  IntegratedSource.MAIL -> Icons.Default.Email
}

private fun formatTime(epochMillis: Long): String {
  if (epochMillis <= 0L) return ""
  return runCatching {
    TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
  }.getOrDefault("")
}

private val TIME_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm")
