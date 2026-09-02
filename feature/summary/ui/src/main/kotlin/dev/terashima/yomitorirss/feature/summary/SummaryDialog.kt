package dev.terashima.yomitorirss.feature.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.terashima.yomitorirss.core.designsystem.MarkdownText
import dev.terashima.yomitorirss.feature.article.Article

@Composable
fun SummaryDialog(
  article: Article,
  text: String?,
  loading: Boolean,
  progress: String?,
  onDismiss: () -> Unit,
  onRetry: (replaceBookmarkTags: Boolean) -> Unit,
) {
  var replaceBookmarkTags by remember(article.id) { mutableStateOf(false) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnClickOutside = false,
    ),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing),
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 24.dp),
      ) {
        Text(
          "記事の要約",
          modifier = Modifier.padding(top = 20.dp),
          style = MaterialTheme.typography.headlineSmall,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
          article.title,
          style = MaterialTheme.typography.labelLarge,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
          item {
            if (loading) {
              LinearProgressIndicator(Modifier.fillMaxWidth())
              Spacer(Modifier.height(8.dp))
              Text(progress ?: "記事本文を取得しています")
            } else {
              MarkdownText(text.orEmpty())
            }
          }
        }
        if (!loading && text != null) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(
              checked = replaceBookmarkTags,
              onCheckedChange = { replaceBookmarkTags = it },
            )
            Column(modifier = Modifier.weight(1f)) {
              Text("ブックマークのタグも再生成")
              Text(
                "ONにすると既存タグを生成されたタグで置き換えます",
                style = MaterialTheme.typography.bodySmall,
              )
            }
          }
        }
        Spacer(Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (!loading && text != null) {
            TextButton(onClick = { onRetry(replaceBookmarkTags) }) { Text("再生成") }
          }
          TextButton(onClick = onDismiss) { Text("閉じる") }
        }
      }
    }
  }
}
