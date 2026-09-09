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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val PODCAST_ROUTE = "podcast"
const val PODCAST_TITLE = "ニュースポッドキャスト"

@Composable
fun PodcastRoute(
  viewModelFactory: PodcastViewModel.Factory,
  modifier: Modifier = Modifier,
) {
  val viewModel: PodcastViewModel = viewModel(factory = viewModelFactory)
  val state by viewModel.state.collectAsStateWithLifecycle()
  val snackbar = remember { SnackbarHostState() }
  var editorProgram by remember { mutableStateOf<PodcastProgram?>(null) }
  var editorVisible by remember { mutableStateOf(false) }
  var deletingProgram by remember { mutableStateOf<PodcastProgram?>(null) }

  LaunchedEffect(state.message) {
    val message = state.message ?: return@LaunchedEffect
    snackbar.showSnackbar(message)
    viewModel.clearMessage()
  }

  Scaffold(
    modifier = modifier,
    snackbarHost = { SnackbarHost(snackbar) },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = {
          editorProgram = null
          editorVisible = true
        },
        icon = { Icon(Icons.Default.Add, contentDescription = null) },
        text = { Text("番組を追加") },
      )
    },
  ) { padding ->
    if (!state.initialized) {
      Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
      }
    } else {
      PodcastContent(
        state = state,
        onSelectProgram = viewModel::selectProgram,
        onGenerate = viewModel::generate,
        onEdit = { program ->
          editorProgram = program
          editorVisible = true
        },
        onDelete = { deletingProgram = it },
        onPlay = viewModel::play,
        onRetry = viewModel::retry,
        modifier = Modifier.fillMaxSize().padding(padding),
      )
    }
  }

  if (editorVisible) {
    PodcastProgramEditorDialog(
      program = editorProgram,
      feeds = state.feeds,
      onDismiss = { editorVisible = false },
      onSave = { id, name, feedIds, provider, scheduleEnabled, hour, minute, maxArticles ->
        viewModel.saveProgram(id, name, feedIds, provider, scheduleEnabled, hour, minute, maxArticles)
        editorVisible = false
      },
    )
  }

  deletingProgram?.let { program ->
    AlertDialog(
      onDismissRequest = { deletingProgram = null },
      title = { Text("番組を削除") },
      text = { Text("「${program.name}」と生成済みエピソードを削除します。") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteProgram(program.id)
            deletingProgram = null
          },
        ) { Text("削除") }
      },
      dismissButton = {
        TextButton(onClick = { deletingProgram = null }) { Text("キャンセル") }
      },
    )
  }
}

@Composable
private fun PodcastContent(
  state: PodcastUiState,
  onSelectProgram: (String) -> Unit,
  onGenerate: (String) -> Unit,
  onEdit: (PodcastProgram) -> Unit,
  onDelete: (PodcastProgram) -> Unit,
  onPlay: (PodcastEpisode) -> Unit,
  onRetry: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    if (state.programs.isEmpty()) {
      Text("番組がありません。右下の「番組を追加」から、利用するフィードと生成方法を設定してください。")
      Spacer(Modifier.height(64.dp))
      return@Column
    }

    Row(
      modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      state.programs.forEach { program ->
        FilterChip(
          selected = state.selectedProgramId == program.id,
          onClick = { onSelectProgram(program.id) },
          label = { Text(program.name) },
        )
      }
    }

    state.selectedProgram?.let { program ->
      ProgramCard(
        program = program,
        generating = program.id in state.busyProgramIds,
        feedCount = program.feedIds.size,
        onGenerate = { onGenerate(program.id) },
        onEdit = { onEdit(program) },
        onDelete = { onDelete(program) },
      )

      Text("エピソード", style = MaterialTheme.typography.titleMedium)
      if (state.episodes.isEmpty()) {
        Text("まだエピソードがありません。生成すると、この番組で未消費の記事だけが使われます。")
      } else {
        state.episodes.forEach { episode ->
          EpisodeCard(
            episode = episode,
            retrying = episode.id in state.busyEpisodeIds,
            onPlay = { onPlay(episode) },
            onRetry = { onRetry(episode.id) },
          )
        }
      }
    }

    Spacer(Modifier.height(72.dp))
  }
}

@Composable
private fun ProgramCard(
  program: PodcastProgram,
  generating: Boolean,
  feedCount: Int,
  onGenerate: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(program.name, style = MaterialTheme.typography.titleLarge)
          Text(
            "${feedCount}フィード・${if (program.provider == PodcastGenerationProvider.LOCAL) "ローカルAI" else "クラウドAI"}",
            style = MaterialTheme.typography.bodyMedium,
          )
          Text(
            if (program.schedule.enabled) {
              "毎日 ${"%02d:%02d".format(program.schedule.hour, program.schedule.minute)} に生成"
            } else {
              "自動生成なし"
            },
            style = MaterialTheme.typography.bodySmall,
          )
        }
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "編集") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "削除") }
      }
      Button(onClick = onGenerate, enabled = !generating, modifier = Modifier.fillMaxWidth()) {
        if (generating) {
          CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
          Spacer(Modifier.width(8.dp))
          Text("生成中")
        } else {
          Icon(Icons.Default.Refresh, contentDescription = null)
          Spacer(Modifier.width(8.dp))
          Text("新しいエピソードを生成")
        }
      }
    }
  }
}

@Composable
private fun EpisodeCard(
  episode: PodcastEpisode,
  retrying: Boolean,
  onPlay: () -> Unit,
  onRetry: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(episode.title, style = MaterialTheme.typography.titleMedium)
      Text(
        "${formatEpisodeTime(episode.createdAtEpochMillis)}・${episode.articles.size}記事",
        style = MaterialTheme.typography.bodySmall,
      )
      when (episode.status) {
        PodcastEpisodeStatus.READY -> {
          OutlinedButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("再生")
          }
        }
        PodcastEpisodeStatus.GENERATING -> Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
          Spacer(Modifier.width(8.dp))
          Text("原稿を生成中")
        }
        PodcastEpisodeStatus.FAILED -> {
          episode.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
          OutlinedButton(onClick = onRetry, enabled = !retrying, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (retrying) "再生成中" else "同じ記事で再生成")
          }
        }
      }
    }
  }
}

@Composable
private fun PodcastProgramEditorDialog(
  program: PodcastProgram?,
  feeds: List<PodcastFeedOption>,
  onDismiss: () -> Unit,
  onSave: (String?, String, Set<String>, PodcastGenerationProvider, Boolean, Int, Int, Int) -> Unit,
) {
  var name by remember(program?.id) { mutableStateOf(program?.name.orEmpty()) }
  var selectedFeedIds by remember(program?.id) { mutableStateOf(program?.feedIds.orEmpty()) }
  var provider by remember(program?.id) { mutableStateOf(program?.provider ?: PodcastGenerationProvider.LOCAL) }
  var scheduleEnabled by remember(program?.id) { mutableStateOf(program?.schedule?.enabled ?: false) }
  var hour by remember(program?.id) { mutableStateOf((program?.schedule?.hour ?: 7).toString()) }
  var minute by remember(program?.id) { mutableStateOf((program?.schedule?.minute ?: 0).toString()) }
  var maxArticles by remember(program?.id) { mutableStateOf((program?.maxArticlesPerEpisode ?: 12).toString()) }

  val parsedHour = hour.toIntOrNull()
  val parsedMinute = minute.toIntOrNull()
  val parsedMaxArticles = maxArticles.toIntOrNull()
  val valid = name.isNotBlank() &&
    selectedFeedIds.isNotEmpty() &&
    parsedHour != null && parsedHour in 0..23 &&
    parsedMinute != null && parsedMinute in 0..59 &&
    parsedMaxArticles != null && parsedMaxArticles in 1..50

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(if (program == null) "番組を追加" else "番組を編集") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
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
        Text("利用するフィード", style = MaterialTheme.typography.titleSmall)
        if (feeds.isEmpty()) {
          Text("利用できるフィードがありません。先にRSSフィードを追加してください。")
        } else {
          feeds.forEach { feed ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = feed.id in selectedFeedIds,
                onCheckedChange = { checked ->
                  selectedFeedIds = if (checked) selectedFeedIds + feed.id else selectedFeedIds - feed.id
                },
              )
              Text(feed.title)
            }
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
    },
    confirmButton = {
      TextButton(
        enabled = valid,
        onClick = {
          onSave(
            program?.id,
            name,
            selectedFeedIds,
            provider,
            scheduleEnabled,
            requireNotNull(parsedHour),
            requireNotNull(parsedMinute),
            requireNotNull(parsedMaxArticles),
          )
        },
      ) { Text("保存") }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } },
  )
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

private fun formatEpisodeTime(epochMillis: Long): String = EPISODE_DATE_FORMATTER.format(
  Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
)

private val EPISODE_DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d HH:mm")
