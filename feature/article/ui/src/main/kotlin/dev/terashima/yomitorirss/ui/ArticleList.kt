@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.terashima.yomitorirss.feature.article

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.core.designsystem.SwipeAction
import dev.terashima.yomitorirss.core.designsystem.SwipeActionListItem
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SwipeChoice(
  val label: String,
  val color: Color,
  val action: (Article) -> Unit,
)

data class ArticleMenuAction(
  val label: String,
  val action: () -> Unit,
)

@Composable
fun ArticleList(
  modifier: Modifier,
  articles: List<Article>,
  emptyText: String,
  bookmarkDetails: Map<String, BookmarkedArticle> = emptyMap(),
  left: SwipeChoice? = null,
  right: SwipeChoice? = null,
  farRight: SwipeChoice? = null,
  onOpen: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onEditTags: (Article) -> Unit,
  onMoveFolder: (Article) -> Unit = {},
  extraMenuActions: (Article) -> List<ArticleMenuAction> = { emptyList() },
) {
  val groups = articles.groupBy(::dateLabel)
  LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
    if (articles.isEmpty()) {
      item {
        Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
          Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    groups.forEach { (date, values) ->
      stickyHeader(key = "header-$date") {
        Surface(color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f)) {
          Text(
            date,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      items(values, key = Article::id) { article ->
        SwipeArticleItem(
          article = article,
          bookmarkDetails = bookmarkDetails[article.id],
          left = left,
          right = right,
          farRight = farRight,
          onOpen = onOpen,
          onSummarize = onSummarize,
          onEditTags = onEditTags,
          onMoveFolder = onMoveFolder,
          extraMenuActions = extraMenuActions,
        )
      }
    }
  }
}

@Composable
private fun LazyItemScope.SwipeArticleItem(
  article: Article,
  bookmarkDetails: BookmarkedArticle?,
  left: SwipeChoice?,
  right: SwipeChoice?,
  farRight: SwipeChoice?,
  onOpen: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onEditTags: (Article) -> Unit,
  onMoveFolder: (Article) -> Unit,
  extraMenuActions: (Article) -> List<ArticleMenuAction>,
) {
  SwipeActionListItem(
    itemKey = article.id,
    left = left?.toSwipeAction(article),
    right = right?.toSwipeAction(article),
    farRight = farRight?.toSwipeAction(article),
  ) {
    ArticleContent(
      article = article,
      bookmarkDetails = bookmarkDetails,
      onOpen = onOpen,
      onSummarize = onSummarize,
      onEditTags = onEditTags,
      onMoveFolder = onMoveFolder,
      extraMenuActions = extraMenuActions,
    )
  }
}

private fun SwipeChoice.toSwipeAction(article: Article): SwipeAction = SwipeAction(
  label = label,
  color = color,
  onCommit = { action(article) },
)

@Composable
private fun ArticleContent(
  article: Article,
  bookmarkDetails: BookmarkedArticle?,
  onOpen: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onEditTags: (Article) -> Unit,
  onMoveFolder: (Article) -> Unit,
  extraMenuActions: (Article) -> List<ArticleMenuAction>,
) {
  var menuOpen by remember(article.id) { mutableStateOf(false) }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .combinedClickable(onClick = { onOpen(article) }, onLongClick = { onSummarize(article) })
      .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(
        article.title,
        style = MaterialTheme.typography.titleMedium,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
      )
      Spacer(Modifier.height(7.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          article.sourceTitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f),
        )
        Text(
          timeLabel(article.publishedAt),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      bookmarkDetails?.let { details ->
        Spacer(Modifier.height(6.dp))
        Text(
          "フォルダ: ${details.folder?.name ?: "未分類"}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (details.tags.isNotEmpty()) {
          Spacer(Modifier.height(4.dp))
          Text(
            details.tags.joinToString(" · ") { it.name },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
    Box {
      IconButton(onClick = { menuOpen = true }) {
        Icon(Icons.Default.MoreVert, "記事メニュー")
      }
      DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
        DropdownMenuItem(
          text = { Text("はてなブックマークコメントを見る") },
          leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
          onClick = {
            menuOpen = false
            onOpen(article.copy(url = "https://b.hatena.ne.jp/entry?url=${android.net.Uri.encode(article.url)}"))
          },
        )
        extraMenuActions(article).forEach { menuAction ->
          DropdownMenuItem(
            text = { Text(menuAction.label) },
            onClick = {
              menuOpen = false
              menuAction.action()
            },
          )
        }
        DropdownMenuItem(
          text = { Text("要約") },
          leadingIcon = { Icon(Icons.Default.SmartToy, null) },
          onClick = {
            menuOpen = false
            onSummarize(article)
          },
        )
        if (bookmarkDetails != null) {
          DropdownMenuItem(
            text = { Text("フォルダを移動") },
            leadingIcon = { Icon(Icons.Default.Folder, null) },
            onClick = {
              menuOpen = false
              onMoveFolder(article)
            },
          )
          DropdownMenuItem(
            text = { Text("タグを編集") },
            leadingIcon = { Icon(Icons.Default.Label, null) },
            onClick = {
              menuOpen = false
              onEditTags(article)
            },
          )
        }
      }
    }
  }
}

private fun dateLabel(article: Article): String = runCatching {
  val date = Instant.parse(article.publishedAt).atZone(ZoneId.systemDefault()).toLocalDate()
  when (date) {
    LocalDate.now() -> "今日"
    LocalDate.now().minusDays(1) -> "昨日"
    else -> date.format(DateTimeFormatter.ofPattern("M月d日（E）"))
  }
}.getOrDefault("日付不明")

private fun timeLabel(value: String): String = runCatching {
  Instant.parse(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M/d HH:mm"))
}.getOrDefault("")
