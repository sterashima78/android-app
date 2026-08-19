package dev.terashima.yomitorirss.feature.bookmark

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun BookmarkImportScreen(
  modifier: Modifier,
  tagCount: Int,
  onImportCsv: () -> Unit,
  onImportHtml: () -> Unit,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(bottom = 24.dp),
  ) {
    item {
      BookmarkImportRow(
        title = "CSVからインポート",
        supporting = "登録済みタグ ${tagCount}件",
        onClick = onImportCsv,
      )
    }
    item {
      BookmarkImportRow(
        title = "HTMLからインポート",
        onClick = onImportHtml,
      )
    }
  }
}

@Composable
private fun BookmarkImportRow(
  title: String,
  supporting: String? = null,
  onClick: () -> Unit,
) {
  ListItem(
    modifier = Modifier.clickable(onClick = onClick),
    headlineContent = { Text(title) },
    supportingContent = supporting?.let { { Text(it) } },
    leadingContent = { Icon(Icons.Default.UploadFile, contentDescription = null) },
    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
  )
}
