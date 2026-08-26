package dev.terashima.yomitorirss.feature.bookmark

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.feature.article.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookmarkEditController internal constructor() {
  internal var editTagsFor by mutableStateOf<Article?>(null)
  internal var moveFolderFor by mutableStateOf<Article?>(null)

  fun editTags(article: Article) {
    editTagsFor = article
  }

  fun moveFolder(article: Article) {
    moveFolderFor = article
  }
}

@Composable
fun rememberBookmarkEditController(): BookmarkEditController = remember { BookmarkEditController() }

@Composable
fun BookmarkRoute(
  modifier: Modifier,
  tab: BookmarkTab,
  bookmarkViewModel: BookmarkViewModel,
  editController: BookmarkEditController,
  onOpen: (Article) -> Unit,
  onSummarize: (Article) -> Unit,
  onReprocessEnrichment: suspend () -> Int,
  onImportCompleted: () -> Unit,
) {
  val state by bookmarkViewModel.state.collectAsState()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var isReprocessingEnrichment by remember { mutableStateOf(false) }
  val csvImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(bookmarkViewModel::importCsv) }
  val htmlImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(bookmarkViewModel::importHtml) }

  LaunchedEffect(tab) {
    if (tab == BookmarkTab.BOOKMARKS) {
      bookmarkViewModel.selectTag(null)
      bookmarkViewModel.selectFolder(null)
    }
  }
  LaunchedEffect(state.importCompleted) {
    if (state.importCompleted) {
      onImportCompleted()
      bookmarkViewModel.consumeImportCompleted()
    }
  }
  LaunchedEffect(state.message) {
    val message = state.message ?: return@LaunchedEffect
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    bookmarkViewModel.dismissMessage()
  }

  if (!state.initialized) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      CircularProgressIndicator()
    }
    return
  }

  BookmarkScreen(
    modifier = modifier,
    tab = tab,
    state = state,
    onTagSelected = bookmarkViewModel::selectTag,
    onFolderSelected = bookmarkViewModel::selectFolder,
    onOpen = onOpen,
    onSummarize = onSummarize,
    onEditTags = editController::editTags,
    onMoveFolder = editController::moveFolder,
    onMoveToLibrary = bookmarkViewModel::moveToLibrary,
    onSetContentType = bookmarkViewModel::setArticleContentType,
    onUnsave = bookmarkViewModel::unsave,
    onCreateFolder = bookmarkViewModel::createFolder,
    onRenameFolder = bookmarkViewModel::renameFolder,
    onDeleteFolder = bookmarkViewModel::deleteFolder,
    onCreateTag = bookmarkViewModel::createTag,
    onRenameTag = bookmarkViewModel::renameTag,
    onDeleteTag = bookmarkViewModel::deleteTag,
    onDeleteUnusedTags = bookmarkViewModel::deleteUnusedTags,
    onReprocessEnrichment = {
      if (!isReprocessingEnrichment) {
        isReprocessingEnrichment = true
        scope.launch {
          val result = withContext(Dispatchers.IO) {
            runCatching { onReprocessEnrichment() }
          }
          result
            .onSuccess { count ->
              val message = if (count > 0) {
                "要約とタグ付けの再実行を${count}件予約しました"
              } else {
                "再実行できるブックマークはありません"
              }
              Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            .onFailure { error ->
              Toast.makeText(
                context,
                error.message ?: "要約とタグ付けの一括再実行を開始できませんでした",
                Toast.LENGTH_LONG,
              ).show()
            }
          isReprocessingEnrichment = false
        }
      }
    },
    isReprocessingEnrichment = isReprocessingEnrichment,
    onImportCsv = {
      csvImportLauncher.launch(
        arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain"),
      )
    },
    onImportHtml = {
      htmlImportLauncher.launch(
        arrayOf("text/html", "application/xhtml+xml", "text/plain"),
      )
    },
  )
}

@Composable
fun BookmarkEditHost(
  bookmarkViewModel: BookmarkViewModel,
  controller: BookmarkEditController,
) {
  val state by bookmarkViewModel.state.collectAsState()

  LaunchedEffect(bookmarkViewModel) {
    bookmarkViewModel.refresh()
  }

  controller.editTagsFor?.let { article ->
    ArticleTagsDialog(
      article = article,
      bookmarkDetails = state.bookmarkDetails[article.id],
      tags = state.tags,
      onDismiss = { controller.editTagsFor = null },
      onSave = {
        controller.editTagsFor = null
        bookmarkViewModel.replaceArticleTags(article, it)
      },
    )
  }
  controller.moveFolderFor?.let { article ->
    ArticleFolderDialog(
      article = article,
      bookmarkDetails = state.bookmarkDetails[article.id],
      folders = state.folders,
      onDismiss = { controller.moveFolderFor = null },
      onSave = { folderId ->
        controller.moveFolderFor = null
        bookmarkViewModel.moveArticleToFolder(article, folderId)
      },
    )
  }
}