package dev.terashima.yomitorirss.feature.podcast

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun PodcastProgramEditorDialog(
  program: PodcastProgram?,
  sources: List<PodcastSource>,
  sourceIdsInUse: Set<String>,
  onAddSource: (String, String) -> Unit,
  onDeleteSource: (String) -> Unit,
  onDismiss: () -> Unit,
  onSave: (String?, String, Set<String>, PodcastGenerationProvider, String, Boolean, Int, Int, Int) -> Unit,
) {
  var name by remember(program?.id) { mutableStateOf(program?.name.orEmpty()) }
  var selectedSourceIds by remember(program?.id) {
    mutableStateOf<Set<String>>(
      program?.sourceIds.orEmpty().filter { sourceId ->
        sources.any { it.id == sourceId }
      }.toSet(),
    )
  }
  var newSourceName by remember(program?.id) { mutableStateOf("") }
  var newSourceUrl by remember(program?.id) { mutableStateOf("") }
  var provider by remember(program?.id) { mutableStateOf(program?.provider ?: PodcastGenerationProvider.LOCAL) }
  var exclusionPrompt by remember(program?.id) { mutableStateOf(program?.exclusionPrompt.orEmpty()) }
  var scheduleEnabled by remember(program?.id) { mutableStateOf(program?.schedule?.enabled ?: false) }
  var hour by remember(program?.id) { mutableStateOf((program?.schedule?.hour ?: 7).toString()) }
  var minute by remember(program?.id) { mutableStateOf((program?.schedule?.minute ?: 0).toString()) }
  var maxArticles by remember(program?.id) { mutableStateOf((program?.maxArticlesPerEpisode ?: 12).toString()) }

  val parsedHour = hour.toIntOrNull()
  val parsedMinute = minute.toIntOrNull()
  val parsedMaxArticles = maxArticles.toIntOrNull()
  val valid = name.isNotBlank() &&
    selectedSourceIds.isNotEmpty() &&
    parsedHour != null && parsedHour in 0..23 &&
    parsedMinute != null && parsedMinute in 0..59 &&
    parsedMaxArticles != null && parsedMaxArticles in 1..50

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      Column(Modifier.fillMaxSize()) {
        Text(
          if (program == null) "番組を追加" else "番組を編集",
          modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
          style = MaterialTheme.typography.titleLarge,
        )
        HorizontalDivider()
        Column(
          modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("番組名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )

          Text("生成AI", style = MaterialTheme.typography.titleSmall)
          ProviderOption("ローカルAI", PodcastGenerationProvider.LOCAL, provider) { provider = it }
          ProviderOption("クラウドAI", PodcastGenerationProvider.CLOUD, provider) { provider = it }
          HorizontalDivider()
          Text("ニュース分類には端末内の文章埋め込みモデルを利用します。初回はモデルの取得が必要です。", style = MaterialTheme.typography.bodySmall)

          OutlinedTextField(
            value = exclusionPrompt,
            onValueChange = { exclusionPrompt = it },
            label = { Text("除外条件") },
            placeholder = { Text("例: 特定カテゴリのニュースは除外する") },
            supportingText = { Text("聞かなくてよいニュースの条件を自然文で指定します。空欄ならすべて候補にします。") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth(),
          )

          HorizontalDivider()
          Text("利用するソース", style = MaterialTheme.typography.titleSmall)
          Text(
            "ここで追加したソースはニュースポッドキャスト専用です。RSS購読一覧とは別に管理されます。",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = newSourceName,
            onValueChange = { newSourceName = it },
            label = { Text("ソース名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedTextField(
            value = newSourceUrl,
            onValueChange = { newSourceUrl = it },
            label = { Text("RSS / Atom URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          OutlinedButton(
            enabled = newSourceName.isNotBlank() && newSourceUrl.isNotBlank(),
            onClick = {
              onAddSource(newSourceName, newSourceUrl)
              newSourceName = ""
              newSourceUrl = ""
            },
            modifier = Modifier.fillMaxWidth(),
          ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Podcast用ソースを追加")
          }
          if (sources.isEmpty()) {
            Text("利用できるソースがありません。上からPodcast用ソースを追加してください。")
          } else {
            sources.forEach { source ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = source.id in selectedSourceIds,
                  onCheckedChange = { checked ->
                    selectedSourceIds = if (checked) selectedSourceIds + source.id else selectedSourceIds - source.id
                  },
                )
                Column(Modifier.weight(1f)) {
                  Text(source.name)
                  Text(source.feedUrl, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(
                  enabled = source.id !in sourceIdsInUse,
                  onClick = {
                    selectedSourceIds -= source.id
                    onDeleteSource(source.id)
                  },
                ) {
                  Icon(Icons.Default.Delete, contentDescription = "ソースを削除")
                }
              }
            }
            if (sourceIdsInUse.isNotEmpty()) {
              Text("番組で利用中のソースは、番組から外して保存した後に削除できます。", style = MaterialTheme.typography.bodySmall)
            }
          }

          HorizontalDivider()
          Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("毎日自動生成", modifier = Modifier.weight(1f))
            Switch(checked = scheduleEnabled, onCheckedChange = { scheduleEnabled = it })
          }
          if (scheduleEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = hour,
                onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                label = { Text("時") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
              OutlinedTextField(
                value = minute,
                onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                label = { Text("分") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
              )
            }
          }
          OutlinedTextField(
            value = maxArticles,
            onValueChange = { maxArticles = it.filter(Char::isDigit).take(2) },
            label = { Text("1回に使う最大記事数") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Text(
            "原稿にはRSS/Atomフィード内の本文だけを使用し、記事リンク先は取得しません。",
            style = MaterialTheme.typography.bodySmall,
          )
        }
        HorizontalDivider()
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onDismiss) { Text("キャンセル") }
          TextButton(
            enabled = valid,
            onClick = {
              onSave(
                program?.id,
                name,
                selectedSourceIds,
                provider,
                exclusionPrompt,
                scheduleEnabled,
                requireNotNull(parsedHour),
                requireNotNull(parsedMinute),
                requireNotNull(parsedMaxArticles),
              )
            },
          ) { Text("保存") }
        }
      }
    }
  }
}

@Composable
private fun ProviderOption(
  label: String,
  value: PodcastGenerationProvider,
  selected: PodcastGenerationProvider,
  onSelect: (PodcastGenerationProvider) -> Unit,
) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    RadioButton(selected = value == selected, onClick = { onSelect(value) })
    Text(label)
  }
}

