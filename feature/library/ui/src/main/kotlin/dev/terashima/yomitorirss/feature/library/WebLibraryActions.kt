package dev.terashima.yomitorirss.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun WebLibraryActions(
  books: List<LibraryBook>,
  onAdd: (String) -> Unit,
  onRefresh: (LibraryBook) -> Unit,
  onRefreshAll: () -> Unit,
  refreshingSourceIds: Set<String>,
  onMoveToBookmark: (LibraryBook) -> Unit,
  modifier: Modifier = Modifier,
) {
  var visible by remember { mutableStateOf(false) }
  var url by remember { mutableStateOf("") }
  var extractorRules by remember { mutableStateOf(emptyList<WebLibraryMetadataExtractor>()) }
  var editingExtractor by remember { mutableStateOf<WebLibraryMetadataExtractor?>(null) }
  var creatingExtractor by remember { mutableStateOf(false) }
  var extractorBusy by remember { mutableStateOf(false) }
  val refreshing = refreshingSourceIds.isNotEmpty()
  val extractorBinding = LocalWebLibraryMetadataExtractorUiBinding.current
  val scope = rememberCoroutineScope()

  fun reloadExtractorRules() {
    val binding = extractorBinding ?: return
    scope.launch {
      extractorBusy = true
      runCatching { withContext(Dispatchers.IO) { binding.list() } }
        .onSuccess { extractorRules = it }
        .onFailure(binding.onError)
      extractorBusy = false
    }
  }

  LaunchedEffect(visible) {
    if (visible && extractorBinding != null) reloadExtractorRules()
  }

  FloatingActionButton(
    modifier = modifier,
    onClick = { visible = true },
  ) {
    Icon(Icons.Default.Add, contentDescription = "Web蔵書を追加")
  }

  if (visible) {
    AlertDialog(
      onDismissRequest = { visible = false },
      title = { Text("Web蔵書") },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL") },
            placeholder = { Text("https://example.com/book") },
            singleLine = true,
          )
          Button(
            onClick = {
              val normalized = url.trim()
              if (normalized.isNotEmpty()) {
                onAdd(normalized)
                url = ""
              }
            },
            enabled = url.isNotBlank() && !refreshing,
          ) {
            Text("URLから追加")
          }

          extractorBinding?.let { binding ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("タイトル・サムネイル取得ルール")
                  Text("URL パターンに一致したページでは、WebView 内で登録した関数を実行します。")
                }
                TextButton(
                  onClick = { creatingExtractor = true },
                  enabled = !extractorBusy && !refreshing,
                ) {
                  Text("追加")
                }
              }
              if (extractorBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
              }
              extractorRules.forEach { extractor ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = extractor.urlPattern,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                  )
                  TextButton(
                    onClick = { editingExtractor = extractor },
                    enabled = !extractorBusy && !refreshing,
                  ) {
                    Text("編集")
                  }
                  TextButton(
                    onClick = {
                      scope.launch {
                        extractorBusy = true
                        runCatching {
                          withContext(Dispatchers.IO) {
                            binding.delete(extractor.id)
                            binding.list()
                          }
                        }
                          .onSuccess { extractorRules = it }
                          .onFailure(binding.onError)
                        extractorBusy = false
                      }
                    },
                    enabled = !extractorBusy && !refreshing,
                  ) {
                    Text("削除")
                  }
                }
              }
              if (!extractorBusy && extractorRules.isEmpty()) {
                Text("登録済みの取得ルールはありません。")
              }
            }
          }

          if (books.isNotEmpty()) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("Webから追加した蔵書", modifier = Modifier.weight(1f))
              TextButton(
                onClick = onRefreshAll,
                enabled = !refreshing && !extractorBusy,
              ) {
                Text("すべて再取得")
              }
            }
            if (refreshing) {
              LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            books.forEach { book ->
              val bookRefreshing = book.sourceId in refreshingSourceIds
              Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = book.title,
                  modifier = Modifier.weight(1f),
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis,
                )
                TextButton(
                  onClick = { onRefresh(book) },
                  enabled = !refreshing && !extractorBusy,
                ) {
                  Text(if (bookRefreshing) "取得中" else "再取得")
                }
                TextButton(
                  onClick = { onMoveToBookmark(book) },
                  enabled = !refreshing && !extractorBusy,
                ) {
                  Text("ブックマークへ移動")
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { visible = false }) {
          Text("閉じる")
        }
      },
    )
  }

  val editorExtractor = editingExtractor
  if (creatingExtractor || editorExtractor != null) {
    WebLibraryMetadataExtractorEditor(
      extractor = editorExtractor,
      busy = extractorBusy,
      onDismiss = {
        creatingExtractor = false
        editingExtractor = null
      },
      onSave = { id, urlPattern, functionCode ->
        val binding = extractorBinding
        if (binding != null) {
          scope.launch {
            extractorBusy = true
            runCatching {
              withContext(Dispatchers.IO) {
                binding.save(id, urlPattern, functionCode)
                binding.list()
              }
            }
              .onSuccess { updated ->
                extractorRules = updated
                creatingExtractor = false
                editingExtractor = null
              }
              .onFailure(binding.onError)
            extractorBusy = false
          }
        }
      },
    )
  }
}

@Composable
private fun WebLibraryMetadataExtractorEditor(
  extractor: WebLibraryMetadataExtractor?,
  busy: Boolean,
  onDismiss: () -> Unit,
  onSave: (String?, String, String) -> Unit,
) {
  var urlPattern by remember(extractor?.id) { mutableStateOf(extractor?.urlPattern.orEmpty()) }
  var functionCode by remember(extractor?.id) { mutableStateOf(extractor?.functionCode.orEmpty()) }

  AlertDialog(
    onDismissRequest = { if (!busy) onDismiss() },
    title = { Text(if (extractor == null) "取得ルールを追加" else "取得ルールを編集") },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 520.dp)
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("URL パターンでは * を任意長、? を1文字のワイルドカードとして利用できます。HTTPS のみ対象です。")
        OutlinedTextField(
          value = urlPattern,
          onValueChange = { urlPattern = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("URL パターン") },
          placeholder = { Text("https://example.com/books/*") },
          singleLine = true,
        )
        Text("関数は WebView 内で同期実行されます。document を参照でき、{ title, thumbnailUrl } を返してください。値がない項目は null にできます。")
        OutlinedTextField(
          value = functionCode,
          onValueChange = { functionCode = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("関数コード") },
          placeholder = {
            Text(
              "() => ({ title: document.querySelector('h1')?.textContent?.trim() ?? null, " +
                "thumbnailUrl: document.querySelector('img.cover')?.currentSrc ?? null })",
            )
          },
          minLines = 7,
        )
        Text("このコードは専用 WebView profile のページコンテキストで動作します。DOM の読み取りだけを行う関数を推奨します。")
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onSave(extractor?.id, urlPattern, functionCode) },
        enabled = !busy && urlPattern.isNotBlank() && functionCode.isNotBlank(),
      ) {
        Text(if (busy) "保存中" else "保存")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss, enabled = !busy) {
        Text("キャンセル")
      }
    },
  )
}
