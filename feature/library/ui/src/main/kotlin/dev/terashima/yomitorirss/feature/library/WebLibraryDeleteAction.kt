package dev.terashima.yomitorirss.feature.library

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalWebLibraryDeleteHandler =
  staticCompositionLocalOf<((LibraryBook) -> Unit)?> { null }

@Composable
internal fun WebLibraryDeleteDialog(
  book: LibraryBook,
  onDismiss: () -> Unit,
  onDelete: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("蔵書から削除") },
    text = {
      Text("「${book.title}」を蔵書から削除します。この操作は元に戻せません。")
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
