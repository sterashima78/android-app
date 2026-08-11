package dev.terashima.yomitorirss.feature.integrated

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun IntegratedScreen(
  rssState: RssUiState,
  redditState: RedditUiState,
  youtubeState: YouTubeUiState,
  mailState: MailUiState,
  isRefreshing: Boolean,
  onRefresh: () -> Unit,
  onMarkProcessed: (IntegratedItem) -> Unit,
  onDefer: (IntegratedItem) -> Unit,
  onArchive: (IntegratedItem.Mail) -> Unit,
  onOpen: (IntegratedItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  val items = integratedItems(rssState, redditState, youtubeState, mailState)
  var selectedSourceName by rememberSaveable { mutableStateOf(IntegratedSource.ALL.name) }
  val selectedSource = IntegratedSource.entries.firstOrNull { it.name == selectedSourceName }
    ?: IntegratedSource.ALL
  val visibleItems = if (selectedSource == IntegratedSource.ALL) {
    items
  } else {
    items.filter { it.source == selectedSource }
  }

  Column(modifier = modifier.fillMaxSize()) {
    InboxSummary(total = items.size)
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
                text = if (selectedSource == IntegratedSource.ALL) {
                  "インボックスは空です"
                } else {
                  "${selectedSource.label} の未処理アイテムはありません"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          items(visibleItems, key = IntegratedItem::key) { item ->
            IntegratedSwipeRow(
              item = item,
              onMarkProcessed = { onMarkProcessed(item) },
              onDefer = { onDefer(item) },
              onArchive = if (item is IntegratedItem.Mail) {
                { onArchive(item) }
              } else {
                null
              },
              onOpen = { onOpen(item) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun InboxSummary(total: Int) {
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
        Text("未処理", style = MaterialTheme.typography.labelLarge)
        Text(
          if (total == 0) "インボックスゼロ" else "$total 件を仕分けできます",
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
  onMarkProcessed: () -> Unit,
  onDefer: () -> Unit,
  onArchive: (() -> Unit)?,
  onOpen: () -> Unit,
) {
  SwipeActionListItem(
    itemKey = item.key,
    left = SwipeAction(
      label = "既読",
      color = MaterialTheme.colorScheme.primary,
      onCommit = onMarkProcessed,
    ),
    right = SwipeAction(
      label = item.deferLabel(),
      color = MaterialTheme.colorScheme.secondary,
      onCommit = onDefer,
    ),
    farRight = onArchive?.let { archive ->
      SwipeAction(
        label = "アーカイブ",
        color = MaterialTheme.colorScheme.tertiary,
        onCommit = archive,
      )
    },
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
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
    }
  }
}

internal fun integratedItems(
  rssState: RssUiState,
  redditState: RedditUiState,
  youtubeState: YouTubeUiState,
  mailState: MailUiState,
): List<IntegratedItem> {
  val accountLabels = mailState.accounts.associate { account ->
    account.id to (account.displayName?.takeIf(String::isNotBlank) ?: account.email)
  }
  return buildList {
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
  }.sortedByDescending(IntegratedItem::timestamp)
}

private fun IntegratedItem.deferLabel(): String = when (this) {
  is IntegratedItem.Rss -> "あとで読む"
  is IntegratedItem.Reddit -> "保存"
  is IntegratedItem.YouTube -> "あとで見る"
  is IntegratedItem.Mail -> "スターして完了"
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
