package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun LibrarySeriesThumbnail(
  name: String,
  count: Int,
  coverBook: LibraryBook,
  onClick: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
  ) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(0.68f),
    ) {
      LibraryBookCover(
        book = coverBook,
        modifier = Modifier.fillMaxSize(),
      )
    }

    Spacer(Modifier.height(6.dp))
    Text(
      "シリーズ",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.primary,
      maxLines = 1,
    )
    Text(
      name,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      "$count 冊",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
internal fun LibraryBookThumbnail(
  book: LibraryBook,
  actionLabel: String,
  onOpenSmbBook: () -> Unit,
  onAction: () -> Unit,
  onEditSeries: () -> Unit,
  allowWebMetadataRefresh: Boolean = true,
) {
  val uriHandler = LocalUriHandler.current
  val smbFileActions = LocalSmbBookFileActionBinding.current
  val webDeleteHandler = LocalWebLibraryDeleteHandler.current
  val webSettingsBinding = LocalWebLibrarySettingsUiBinding.current
  var actionMenuExpanded by remember(book.source, book.sourceId) { mutableStateOf(false) }
  var renameDialogVisible by remember(book.source, book.sourceId) { mutableStateOf(false) }
  var deleteDialogVisible by remember(book.source, book.sourceId) { mutableStateOf(false) }
  var webDeleteDialogVisible by remember(book.source, book.sourceId) { mutableStateOf(false) }
  val tapAction = remember(book) { book.tapAction() }
  val canOpen = tapAction != LibraryBookTapAction.OpenMenu

  Box(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.fillMaxWidth()) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(0.68f)
          .combinedClickable(
            onClickLabel = if (canOpen) "書籍を開く" else "操作メニュー",
            onLongClickLabel = "操作メニュー",
            onLongClick = { actionMenuExpanded = true },
            onClick = {
              when (val action = tapAction) {
                LibraryBookTapAction.OpenSmbBook -> onOpenSmbBook()
                is LibraryBookTapAction.OpenExternalUri -> uriHandler.openUri(action.uri)
                LibraryBookTapAction.OpenMenu -> actionMenuExpanded = true
              }
            },
          ),
      ) {
        LibraryBookCover(
          book = book,
          modifier = Modifier.fillMaxSize(),
        )
      }

      Spacer(Modifier.height(6.dp))
      Text(
        book.source.label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        book.title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      book.series?.position?.let { position ->
        Text(
          "$position 巻",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
        )
      }
      if (book.authors.isNotEmpty()) {
        Text(
          book.authors.joinToString(", "),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (book.source == LibrarySource.AUDIBLE && book.narrators.isNotEmpty()) {
        Text(
          "ナレーター: ${book.narrators.joinToString(", ")}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (book.source == LibrarySource.AUDIBLE) {
        val audibleDetails = listOfNotNull(
          book.duration?.takeIf(String::isNotBlank)?.let { "再生 $it" },
          book.publishedDate?.takeIf(String::isNotBlank)?.let { "配信 $it" },
        ).joinToString(" / ")
        if (audibleDetails.isNotEmpty()) {
          Text(
            audibleDetails,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }

    DropdownMenu(
      expanded = actionMenuExpanded,
      onDismissRequest = { actionMenuExpanded = false },
    ) {
      DropdownMenuItem(
        text = { Text("シリーズを編集") },
        onClick = {
          actionMenuExpanded = false
          onEditSeries()
        },
      )
      if (book.source == LibrarySource.SMB && smbFileActions != null) {
        DropdownMenuItem(
          text = { Text("ファイル名を変更") },
          onClick = {
            actionMenuExpanded = false
            renameDialogVisible = true
          },
        )
        DropdownMenuItem(
          text = { Text("ファイルを削除") },
          onClick = {
            actionMenuExpanded = false
            deleteDialogVisible = true
          },
        )
      }
      if (allowWebMetadataRefresh && book.source == LibrarySource.WEB && webSettingsBinding != null) {
        DropdownMenuItem(
          text = { Text("metadataを再取得") },
          enabled = !webSettingsBinding.refreshState.running,
          onClick = {
            actionMenuExpanded = false
            webSettingsBinding.onRefresh(book)
          },
        )
      }
      if (book.canDeleteFromLibrary() && webDeleteHandler != null) {
        DropdownMenuItem(
          text = { Text("削除") },
          onClick = {
            actionMenuExpanded = false
            webDeleteDialogVisible = true
          },
        )
      }
      DropdownMenuItem(
        text = { Text(actionLabel) },
        onClick = {
          actionMenuExpanded = false
          onAction()
        },
      )
    }
  }

  if (renameDialogVisible && smbFileActions != null) {
    SmbBookRenameDialog(
      book = book,
      onDismiss = { renameDialogVisible = false },
      onRename = { newFileName ->
        renameDialogVisible = false
        smbFileActions.onRename(book, newFileName)
      },
    )
  }

  if (deleteDialogVisible && smbFileActions != null) {
    SmbBookDeleteDialog(
      book = book,
      onDismiss = { deleteDialogVisible = false },
      onDelete = {
        deleteDialogVisible = false
        smbFileActions.onDelete(book)
      },
    )
  }

  if (webDeleteDialogVisible && webDeleteHandler != null) {
    WebLibraryDeleteDialog(
      book = book,
      onDismiss = { webDeleteDialogVisible = false },
      onDelete = {
        webDeleteDialogVisible = false
        webDeleteHandler(book)
      },
    )
  }
}

@Composable
private fun SmbBookRenameDialog(
  book: LibraryBook,
  onDismiss: () -> Unit,
  onRename: (String) -> Unit,
) {
  var newFileName by remember(book.sourceId) { mutableStateOf(book.title) }
  val trimmed = newFileName.trim()
  val valid = trimmed.isNotEmpty() && '/' !in trimmed && '\\' !in trimmed

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("ファイル名を変更") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          "ファイルサーバ上のファイル名を変更します。拡張子は現在の形式を維持します。",
          style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
          value = newFileName,
          onValueChange = { newFileName = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("新しいファイル名") },
          supportingText = {
            Text(if (valid) "拡張子は入力しなくても維持されます" else "ファイル名を入力してください")
          },
          isError = !valid,
          singleLine = true,
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = valid,
        onClick = { onRename(trimmed) },
      ) {
        Text("変更")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("キャンセル")
      }
    },
  )
}

@Composable
private fun SmbBookDeleteDialog(
  book: LibraryBook,
  onDismiss: () -> Unit,
  onDelete: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("ファイルを削除") },
    text = {
      Text("「${book.title}」をファイルサーバから削除します。この操作は元に戻せません。")
    },
    confirmButton = {
      TextButton(onClick = onDelete) {
        Text("削除", color = MaterialTheme.colorScheme.error)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("キャンセル")
      }
    },
  )
}

@Composable
private fun LibraryBookCover(
  book: LibraryBook,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      "表紙なし",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    book.thumbnailUrl?.takeIf(String::isNotBlank)?.let {
      LibraryThumbnailImage(
        book = book,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
      )
    }
  }
}

