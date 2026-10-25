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
  var deletingEpisode by remember { mutableStateOf<PodcastEpisode?>(null) }

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
        onTopicSimilarityThresholdChange = viewModel::updateTopicSimilarityThreshold,
        onShowArchived = viewModel::setShowArchivedEpisodes,
        onGenerate = viewModel::generate,
        onEdit = { program ->
          editorProgram = program
          editorVisible = true
        },
        onDelete = { deletingProgram = it },
        onPlay = viewModel::play,
        onRetry = viewModel::retry,
        onArchiveEpisode = viewModel::archiveEpisode,
        onRestoreEpisode = viewModel::restoreEpisode,
        onDeleteEpisode = { deletingEpisode = it },
        modifier = Modifier.fillMaxSize().padding(padding),
      )
    }
  }

  if (editorVisible) {
    PodcastProgramEditorDialog(
      program = editorProgram,
      sources = state.sources,
      sourceIdsInUse = state.programs.flatMapTo(mutableSetOf(), PodcastProgram::sourceIds),
      onAddSource = viewModel::saveSource,
      onDeleteSource = viewModel::deleteSource,
      onDismiss = { editorVisible = false },
      onSave = { id, name, sourceIds, provider, exclusionPrompt, scheduleEnabled, hour, minute, maxArticles ->
        viewModel.saveProgram(id, name, sourceIds, provider, exclusionPrompt, scheduleEnabled, hour, minute, maxArticles)
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

  deletingEpisode?.let { episode ->
    AlertDialog(
      onDismissRequest = { deletingEpisode = null },
      title = { Text("エピソードを削除") },
      text = { Text("「${episode.title}」を削除します。削除したエピソードは復元できません。") },
      confirmButton = {
        TextButton(
          onClick = {
            viewModel.deleteEpisode(episode.id)
            deletingEpisode = null
          },
        ) { Text("削除") }
      },
      dismissButton = {
        TextButton(onClick = { deletingEpisode = null }) { Text("キャンセル") }
      },
    )
  }
}

@Composable
private fun PodcastContent(
  state: PodcastUiState,
  onSelectProgram: (String) -> Unit,
  onTopicSimilarityThresholdChange: (Double) -> Unit,
  onShowArchived: (Boolean) -> Unit,
  onGenerate: (String) -> Unit,
  onEdit: (PodcastProgram) -> Unit,
  onDelete: (PodcastProgram) -> Unit,
  onPlay: (PodcastEpisode) -> Unit,
  onRetry: (String) -> Unit,
  onArchiveEpisode: (String) -> Unit,
  onRestoreEpisode: (String) -> Unit,
  onDeleteEpisode: (PodcastEpisode) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    if (state.programs.isEmpty()) {
      Text("番組がありません。右下の「番組を追加」から、Podcast用のソースと生成方法を設定してください。")
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
      val generating = state.episodes.any { episode ->
        episode.status == PodcastEpisodeStatus.GENERATING
      }
      val oldestQueuedEpisodeId = state.episodes
        .asSequence()
        .filter { it.status == PodcastEpisodeStatus.QUEUED }
        .minWithOrNull(compareBy<PodcastEpisode> { it.createdAtEpochMillis }.thenBy { it.id })
        ?.id

      ProgramCard(
        program = program,
        generating = generating,
        hasQueuedEpisode = oldestQueuedEpisodeId != null,
        sourceCount = program.sourceIds.size,
        onGenerate = { onGenerate(program.id) },
        onEdit = { onEdit(program) },
        onDelete = { onDelete(program) },
      )

      TopicSimilarityThresholdCard(
        threshold = state.topicSimilarityThreshold,
        onUpdate = onTopicSimilarityThresholdChange,
      )

      Text("エピソード", style = MaterialTheme.typography.titleMedium)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
          selected = !state.showArchivedEpisodes,
          onClick = { onShowArchived(false) },
          label = { Text("現在") },
        )
        FilterChip(
          selected = state.showArchivedEpisodes,
          onClick = { onShowArchived(true) },
          label = { Text("アーカイブ") },
        )
      }
      if (state.episodes.isEmpty()) {
        Text(
          if (state.showArchivedEpisodes) {
            "アーカイブ済みのエピソードはありません。"
          } else {
            "まだエピソードがありません。生成すると、この番組で未消費の記事だけが使われます。"
          },
        )
      } else {
        state.episodes.forEach { episode ->
          EpisodeCard(
            episode = episode,
            busy = episode.id in state.busyEpisodeIds,
            queuedStartEnabled = episode.id == oldestQueuedEpisodeId && !generating,
            queuedStarting = episode.id == oldestQueuedEpisodeId && generating,
            onStartQueued = { onGenerate(program.id) },
            onPlay = { onPlay(episode) },
            onRetry = { onRetry(episode.id) },
            onArchive = { onArchiveEpisode(episode.id) },
            onRestore = { onRestoreEpisode(episode.id) },
            onDelete = { onDeleteEpisode(episode) },
          )
        }
      }
    }

    Spacer(Modifier.height(72.dp))
  }
}

@Composable
private fun TopicSimilarityThresholdCard(
  threshold: Double,
  onUpdate: (Double) -> Unit,
) {
  var sliderValue by remember(threshold) { mutableFloatStateOf(threshold.toFloat()) }
  val displayed = (sliderValue * 100).roundToInt() / 100.0

  Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("関連トピックの類似度しきい値", style = MaterialTheme.typography.titleMedium)
      Text(
        "全番組共通の設定です。値を高くするほど異なるニュースをまとめにくくなります。",
        style = MaterialTheme.typography.bodySmall,
      )
      Text("しきい値: ${"%.2f".format(Locale.ROOT, displayed)}")
      Slider(
        value = sliderValue,
        onValueChange = { sliderValue = it },
        onValueChangeFinished = { onUpdate((sliderValue * 100).roundToInt() / 100.0) },
        valueRange = MIN_PODCAST_TOPIC_SIMILARITY_THRESHOLD.toFloat()..
          MAX_PODCAST_TOPIC_SIMILARITY_THRESHOLD.toFloat(),
        steps = 28,
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("緩い (0.70)", style = MaterialTheme.typography.bodySmall)
        Text("厳しい (0.99)", style = MaterialTheme.typography.bodySmall)
      }
      OutlinedButton(
        onClick = { onUpdate(DEFAULT_PODCAST_TOPIC_SIMILARITY_THRESHOLD) },
        enabled = displayed != DEFAULT_PODCAST_TOPIC_SIMILARITY_THRESHOLD,
      ) {
        Text("既定値 (0.95) に戻す")
      }
      Text(
        "新しく分類するエピソードに反映されます。保存済みエピソードは「現在の条件で作り直す」で再評価できます。",
        style = MaterialTheme.typography.bodySmall,
      )
    }
  }
}

@Composable
private fun ProgramCard(
  program: PodcastProgram,
  generating: Boolean,
  hasQueuedEpisode: Boolean,
  sourceCount: Int,
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
            "${sourceCount}ソース・${if (program.provider == PodcastGenerationProvider.LOCAL) "ローカルAI" else "クラウドAI"}",
            style = MaterialTheme.typography.bodyMedium,
          )
          if (program.exclusionPrompt.isNotBlank()) {
            Text("除外条件あり", style = MaterialTheme.typography.bodySmall)
          }
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
          Text(if (hasQueuedEpisode) "生成待ちのエピソードを開始" else "エピソードを生成")
        }
      }
      if (hasQueuedEpisode) {
        Text("生成待ちのエピソードを先に処理してください。", style = MaterialTheme.typography.bodySmall)
      }
    }
  }
}

@Composable
private fun EpisodeCard(
  episode: PodcastEpisode,
  busy: Boolean,
  queuedStartEnabled: Boolean,
  queuedStarting: Boolean,
  onStartQueued: () -> Unit,
  onPlay: () -> Unit,
  onRetry: () -> Unit,
  onArchive: () -> Unit,
  onRestore: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(episode.title, style = MaterialTheme.typography.titleMedium)
      Text(
        "${formatEpisodeTime(episode.createdAtEpochMillis)}・${episode.articles.size}記事",
        style = MaterialTheme.typography.bodySmall,
      )
      podcastClusteringStatusMessage(episode.clusteringStatus)?.let { warning ->
        Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
      }
      when (episode.status) {
        PodcastEpisodeStatus.READY -> {
          OutlinedButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("再生")
          }
          OutlinedButton(onClick = onRetry, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "処理中" else "現在の条件で作り直す")
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(onClick = onArchive, enabled = !busy) { Text("アーカイブ") }
            TextButton(onClick = onDelete, enabled = !busy) { Text("削除") }
          }
        }
        PodcastEpisodeStatus.QUEUED -> {
          if (queuedStarting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
              Spacer(Modifier.width(8.dp))
              Text("生成を開始中")
            }
          } else {
            Text("生成待ち", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
              onClick = onStartQueued,
              enabled = queuedStartEnabled,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Icon(Icons.Default.Refresh, contentDescription = null)
              Spacer(Modifier.width(8.dp))
              Text(if (queuedStartEnabled) "生成を開始" else "前の生成待ちを先に処理")
            }
          }
        }
        PodcastEpisodeStatus.GENERATING -> Row(verticalAlignment = Alignment.CenterVertically) {
          CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
          Spacer(Modifier.width(8.dp))
          Text("原稿を生成中")
        }
        PodcastEpisodeStatus.FAILED -> {
          episode.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
          OutlinedButton(onClick = onRetry, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (busy) "処理中" else "現在の条件で作り直す")
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(onClick = onDelete, enabled = !busy) { Text("削除") }
          }
        }
        PodcastEpisodeStatus.ARCHIVED -> {
          OutlinedButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("再生")
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(onClick = onRestore, enabled = !busy) { Text("復元") }
            TextButton(onClick = onDelete, enabled = !busy) { Text("削除") }
          }
        }
        PodcastEpisodeStatus.DELETED -> Unit
      }
    }
  }
}


internal fun podcastClusteringStatusMessage(status: PodcastClusteringStatus?): String? = when (status) {
  PodcastClusteringStatus.FALLBACK_INFERENCE_ERROR -> "分類処理に失敗したため、記事を1件ずつ分けました"
  PodcastClusteringStatus.FALLBACK_INVALID_OUTPUT -> "分類結果が不正だったため、記事を1件ずつ分けました"
  PodcastClusteringStatus.SUCCESS, PodcastClusteringStatus.SKIPPED, null -> null
}


private fun formatEpisodeTime(epochMillis: Long): String = EPISODE_DATE_FORMATTER.format(
  Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
)

private val EPISODE_DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d HH:mm")
