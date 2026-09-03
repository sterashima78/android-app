package dev.terashima.yomitorirss.feature.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleList
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.SwipeChoice
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle

enum class RssTab(val label: String) {
  UNREAD("未読"),
  READ_LATER("あとで読む"),
  FEEDS("フィード管理"),
  SETTINGS("設定"),
}

@Composable
fun RssScreen(
  modifier: Modifier,
  tab: RssTab,
  state: RssUiState,
  onMarkRead: (Article) -> Unit,
  onSaveAndRead: (Article) -> Unit,
  onReadLater: (Article) -> Unit,
  onUnsave: (Article) -> Unit,
  onRemoveReadLater: (Article) -> Unit,
  onReviewUnsave: (Article) -> Unit,
  onReviewRemoveReadLater: (Article) -> Unit,
  onRestoreReadLater: (BookmarkedArticle) -> Unit,
  onOpen: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onPrepareReviewSummary: (Article) -> Unit,
  onRetryReviewSummary: (Article) -> Unit,
  onStopReviewSummary: () -> Unit,
  reviewSummaryArticleId: String?,
  reviewSummaryText: String?,
  reviewSummaryLoading: Boolean,
  reviewSummaryError: String?,
  onEditTags: (Article) -> Unit,
  onMoveFolder: (Article) -> Unit,
  onSetContentType: (Article, ContentType?) -> Unit,
) {
  when (tab) {
    RssTab.UNREAD -> ArticleList(
      modifier = modifier,
      articles = state.unread.filterNot { it.id in state.hiddenArticleIds },
      emptyText = "未読記事はありません",
      left = SwipeChoice("既読", MaterialTheme.colorScheme.primary, onMarkRead),
      right = SwipeChoice("ブックマーク", MaterialTheme.colorScheme.secondary, onSaveAndRead),
      farRight = SwipeChoice("あとで読む", MaterialTheme.colorScheme.tertiary, onReadLater),
      onOpen = onOpen,
      onSummarize = onSummarize,
      onEditTags = onEditTags,
      onMoveFolder = onMoveFolder,
      onSetContentType = onSetContentType,
    )

    RssTab.READ_LATER -> {
      var oldestFirst by rememberSaveable { mutableStateOf(true) }
      var reviewing by rememberSaveable { mutableStateOf(false) }
      val visibleReadLater = state.readLater.filterNot { it.article.id in state.hiddenArticleIds }
      val bookmarkedArticles = visibleReadLater.let { visible ->
        if (oldestFirst) visible.sortedBy { it.article.publishedAt }
        else visible.sortedByDescending { it.article.publishedAt }
      }

      if (reviewing) {
        DisposableEffect(Unit) {
          onDispose(onStopReviewSummary)
        }
        ReadLaterReviewScreen(
          initialArticles = bookmarkedArticles,
          currentReadLater = visibleReadLater,
          summaryArticleId = reviewSummaryArticleId,
          summaryText = reviewSummaryText,
          summaryLoading = reviewSummaryLoading,
          summaryError = reviewSummaryError,
          onPrepareSummary = onPrepareReviewSummary,
          onRetrySummary = onRetryReviewSummary,
          onOpen = onOpen,
          onMoveToUncategorized = onReviewRemoveReadLater,
          onDelete = onReviewUnsave,
          onRestoreReadLater = onRestoreReadLater,
          onExit = { reviewing = false },
        )
      } else {
        val articles = bookmarkedArticles.map(BookmarkedArticle::article)
        Column(modifier.fillMaxSize()) {
          Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(
              onClick = { reviewing = true },
              enabled = bookmarkedArticles.isNotEmpty(),
            ) {
              Text("レビュー")
            }
            TextButton(onClick = { oldestFirst = !oldestFirst }) {
              Text(if (oldestFirst) "古い順 ↑" else "新しい順 ↓")
            }
          }
          ArticleList(
            modifier = Modifier.weight(1f),
            articles = articles,
            bookmarkDetails = bookmarkedArticles.associateBy { it.article.id },
            emptyText = "あとで読む記事はありません",
            left = SwipeChoice("ブックマーク解除", MaterialTheme.colorScheme.error, onUnsave),
            right = SwipeChoice("未分類へ", MaterialTheme.colorScheme.secondary, onRemoveReadLater),
            onOpen = onOpen,
            onSummarize = onSummarize,
            onEditTags = onEditTags,
            onMoveFolder = onMoveFolder,
            onSetContentType = onSetContentType,
          )
        }
      }
    }

    RssTab.FEEDS,
    RssTab.SETTINGS -> Unit
  }
}
