package dev.terashima.yomitorirss.feature.rss

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.core.designsystem.MarkdownText
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
internal fun ReadLaterReviewScreen(
  initialArticles: List<BookmarkedArticle>,
  currentReadLater: List<BookmarkedArticle>,
  summaryArticleId: String?,
  summaryText: String?,
  summaryLoading: Boolean,
  summaryError: String?,
  onPrepareSummary: (Article) -> Unit,
  onRetrySummary: (Article) -> Unit,
  onOpen: (Article) -> Unit,
  onMoveToUncategorized: (Article) -> Unit,
  onDelete: (Article) -> Unit,
  onRestoreReadLater: (BookmarkedArticle) -> Unit,
  onExit: () -> Unit,
) {
  val sessionIds by rememberSaveable {
    mutableStateOf(initialArticles.map { it.article.id })
  }
  var currentIndex by rememberSaveable { mutableIntStateOf(0) }
  val currentById = currentReadLater.associateBy { it.article.id }
  val currentId = sessionIds.getOrNull(currentIndex)
  val current = currentId?.let(currentById::get)
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  LaunchedEffect(currentId, current) {
    if (currentId != null && current == null) {
      currentIndex += 1
    }
  }

  current?.let { reviewed ->
    LaunchedEffect(reviewed.article.id) {
      onPrepareSummary(reviewed.article)
    }
  }

  fun advance() {
    currentIndex += 1
  }

  fun showUndo(message: String, restore: () -> Unit) {
    scope.launch {
      snackbarHostState.currentSnackbarData?.dismiss()
      if (
        snackbarHostState.showSnackbar(
          message = message,
          actionLabel = "元に戻す",
          withDismissAction = true,
        ) == SnackbarResult.ActionPerformed
      ) {
        restore()
      }
    }
  }

  Box(Modifier.fillMaxSize()) {
    when {
      currentIndex >= sessionIds.size -> ReviewCompleted(
        remainingCount = currentReadLater.size,
        onExit = onExit,
      )

      current == null -> Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
      ) {
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 32.dp))
      }

      else -> Column(Modifier.fillMaxSize()) {
        ReviewHeader(
          current = currentIndex + 1,
          total = sessionIds.size,
          onExit = onExit,
        )

        ArticleHeader(current.article)
        HorizontalDivider()

        LazyColumn(
          modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
          item {
            SummaryContent(
              articleId = current.article.id,
              summaryArticleId = summaryArticleId,
              summaryText = summaryText,
              summaryLoading = summaryLoading,
              summaryError = summaryError,
              onRetry = { onRetrySummary(current.article) },
            )
          }
        }

        HorizontalDivider()
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = { onOpen(current.article) },
            modifier = Modifier.weight(1f),
          ) {
            Text("記事を開く")
          }
          OutlinedButton(
            onClick = {
              onOpen(
                current.article.copy(
                  url = "https://b.hatena.ne.jp/entry?url=${Uri.encode(current.article.url)}",
                ),
              )
            },
            modifier = Modifier.weight(1f),
          ) {
            Text("はてブを見る")
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = ::advance,
            modifier = Modifier.weight(1f),
          ) {
            Text("保留")
          }
          Button(
            onClick = {
              val item = current
              advance()
              onMoveToUncategorized(item.article)
              showUndo("未分類へ移動しました") { onRestoreReadLater(item) }
            },
            modifier = Modifier.weight(1f),
          ) {
            Text("未分類へ")
          }
          Button(
            onClick = {
              val item = current
              advance()
              onDelete(item.article)
              showUndo("ブックマークを削除しました") { onRestoreReadLater(item) }
            },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          ) {
            Text("削除")
          }
        }
      }
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp),
    )
  }
}

@Composable
private fun ReviewHeader(
  current: Int,
  total: Int,
  onExit: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onExit) {
      Icon(Icons.Default.ArrowBack, contentDescription = "レビューを終了")
    }
    Text(
      "あとで読むレビュー",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.SemiBold,
      modifier = Modifier.weight(1f),
    )
    Text(
      "$current / $total",
      style = MaterialTheme.typography.labelLarge,
      modifier = Modifier.padding(end = 12.dp),
    )
  }
}

@Composable
private fun ArticleHeader(article: Article) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
  ) {
    Text(
      article.title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 4,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(Modifier.height(6.dp))
    Text(
      listOfNotNull(
        article.sourceTitle.takeIf(String::isNotBlank),
        reviewTimeLabel(article.publishedAt).takeIf(String::isNotBlank),
      ).joinToString(" · "),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun SummaryContent(
  articleId: String,
  summaryArticleId: String?,
  summaryText: String?,
  summaryLoading: Boolean,
  summaryError: String?,
  onRetry: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
  ) {
    Text(
      "要約",
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(12.dp))

    when {
      summaryArticleId != articleId || summaryLoading -> {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
          if (summaryArticleId == articleId) "要約を準備しています" else "要約を読み込んでいます",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      summaryError != null -> {
        Text(
          summaryError,
          color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) {
          Text("再生成")
        }
      }

      summaryText != null -> MarkdownText(summaryText)

      else -> Text(
        "要約はありません",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun ReviewCompleted(
  remainingCount: Int,
  onExit: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      "レビュー完了",
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(12.dp))
    Text("今回の対象をすべて確認しました。")
    Spacer(Modifier.height(8.dp))
    Text(
      "あとで読む: ${remainingCount}件",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = onExit) {
      Text("一覧へ戻る")
    }
  }
}

private fun reviewTimeLabel(value: String): String = runCatching {
  Instant.parse(value)
    .atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("M/d HH:mm"))
}.getOrDefault("")
