package dev.terashima.yomitorirss.feature.article

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun ArticleContentTypeDialog(
  article: Article,
  onDismiss: () -> Unit,
  onSelect: (ContentType?) -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("コンテンツ種別") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(androidx.compose.ui.unit.dp(4f))) {
        Text(article.title, style = MaterialTheme.typography.bodyMedium)
        ContentTypeChoice(
          label = "継承（現在: ${article.effectiveContentType.displayLabel()}）",
          selected = article.contentTypeOverride == null,
          onClick = { onSelect(null) },
        )
        ContentTypeChoice(
          label = "記事",
          selected = article.contentTypeOverride == ContentType.ARTICLE,
          onClick = { onSelect(ContentType.ARTICLE) },
        )
        ContentTypeChoice(
          label = "漫画",
          selected = article.contentTypeOverride == ContentType.COMIC,
          onClick = { onSelect(ContentType.COMIC) },
        )
      }
    },
    confirmButton = {},
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
}

@Composable
private fun ContentTypeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
  TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
    Text(if (selected) "選択中 · $label" else label, modifier = Modifier.fillMaxWidth())
  }
}

internal fun ContentType.displayLabel(): String = when (this) {
  ContentType.ARTICLE -> "記事"
  ContentType.COMIC -> "漫画"
}
