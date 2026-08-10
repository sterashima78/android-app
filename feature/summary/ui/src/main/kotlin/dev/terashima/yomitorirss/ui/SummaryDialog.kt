@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.summary
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.terashima.yomitorirss.feature.article.Article

@Composable
fun SummaryDialog(
  article: Article,
  text: String?,
  loading: Boolean,
  progress: String?,
  onDismiss: () -> Unit,
  onRetry: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.9f)
        .padding(horizontal = 24.dp),
    ) {
      Text("記事の要約", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
            Text(text.orEmpty(), style = MaterialTheme.typography.bodyLarge)
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
          TextButton(onClick = onRetry) { Text("再生成") }
        }
        TextButton(onClick = onDismiss) { Text("閉じる") }
      }
    }
  }
}
