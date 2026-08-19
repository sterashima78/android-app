package dev.terashima.yomitorirss.feature.workout

import android.content.ClipData
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun WorkoutScreen(
  viewModel: WorkoutViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  val haptic = LocalHapticFeedback.current
  val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }
  DisposableEffect(toneGenerator) { onDispose { toneGenerator.release() } }
  LaunchedEffect(state.intervalCompletionToken) {
    if (state.intervalCompletionToken > 0) {
      toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
  }

  if (!state.initialized) {
    Column(modifier.fillMaxSize().padding(24.dp)) { Text("ワークアウトを読み込んでいます…") }
    return
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
        WorkoutTab.entries.forEach { tab ->
          NavigationBarItem(
            selected = state.selectedTab == tab,
            onClick = { viewModel.selectTab(tab) },
            icon = {
              Icon(
                imageVector = when (tab) {
                  WorkoutTab.WORKOUT -> Icons.Default.FitnessCenter
                  WorkoutTab.TIMER -> Icons.Default.AccessTime
                  WorkoutTab.HISTORY -> Icons.Default.History
                  WorkoutTab.SETTINGS -> Icons.Default.Settings
                },
                contentDescription = tab.label,
              )
            },
            label = { Text(tab.label, maxLines = 1) },
          )
        }
      }
    },
  ) { padding ->
    val contentModifier = Modifier.fillMaxSize().padding(padding)
    when (state.selectedTab) {
      WorkoutTab.WORKOUT -> WorkoutLogScreen(state, viewModel, contentModifier)
      WorkoutTab.TIMER -> WorkoutTimerScreen(state, viewModel, contentModifier)
      WorkoutTab.HISTORY -> WorkoutHistoryScreen(state, contentModifier)
      WorkoutTab.SETTINGS -> WorkoutSettingsScreen(state, viewModel, contentModifier)
    }
  }
}

@Composable
private fun WorkoutLogScreen(state: WorkoutUiState, viewModel: WorkoutViewModel, modifier: Modifier) {
  val active = state.activeExercise
  LazyColumn(
    modifier = modifier,
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(state.snapshot.today.date, style = MaterialTheme.typography.labelLarge)
          Text(
            if (state.snapshot.today.startedAt == null) "今日の運動を始める" else "ワークアウトを継続中",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
          )
          Text("${state.snapshot.today.sets.size} セット記録済み")
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::startWorkout, enabled = state.snapshot.today.startedAt == null) { Text("開始") }
            OutlinedButton(onClick = viewModel::finishWorkout, enabled = state.snapshot.today.sets.isNotEmpty()) { Text("終了して保存") }
            TextButton(onClick = viewModel::resetToday) { Text("リセット") }
          }
        }
      }
    }
    item {
      Card(Modifier.fillMaxWidth()) {
        Row(
          Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column {
            Text("インターバル", style = MaterialTheme.typography.labelLarge)
            Text(formatDuration(state.intervalRemainingSeconds), style = MaterialTheme.typography.headlineMedium)
          }
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = viewModel::startInterval, enabled = !state.intervalRunning) { Text("開始") }
            OutlinedButton(onClick = viewModel::pauseInterval, enabled = state.intervalRunning) { Text("停止") }
          }
        }
      }
    }
    item {
      Text("今日のメニュー", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(8.dp))
      LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.snapshot.exercises, key = { it.id }) { exercise ->
          FilterChip(
            selected = active?.id == exercise.id,
            onClick = { viewModel.selectExercise(exercise.id) },
            label = {
              val count = state.snapshot.today.sets.count { it.exerciseId == exercise.id }
              Text("${exercise.name} $count/${exercise.targetSets}")
            },
          )
        }
      }
    }
    if (active != null) {
      item {
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(active.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val total = state.activeSets.sumOf { it.amount }
            val steps = state.activeSets.sumOf { it.steps ?: 0 }
            Text(
              buildString {
                append("${state.activeSets.size}/${active.targetSets} セット · 合計 ")
                append(if (active.unit == WorkoutUnit.SECONDS) formatDuration(total) else "$total${active.unit.label}")
                if (steps > 0) append(" · ${steps}段")
              },
            )
            when (active.type) {
              WorkoutExerciseType.PLANK -> StopwatchControls(
                seconds = state.plankSeconds,
                running = state.plankRunning,
                onStart = viewModel::startPlank,
                onPause = viewModel::pausePlank,
                onReset = viewModel::resetPlank,
                onRecord = viewModel::recordPlank,
              )
              WorkoutExerciseType.STEP_UP -> {
                StopwatchControls(
                  seconds = state.stepUpSeconds,
                  running = state.stepUpRunning,
                  onStart = viewModel::startStepUp,
                  onPause = viewModel::pauseStepUp,
                  onReset = viewModel::resetStepUp,
                  onRecord = viewModel::recordStepUp,
                )
                OutlinedTextField(
                  value = state.stepCount,
                  onValueChange = viewModel::updateStepCount,
                  modifier = Modifier.fillMaxWidth(),
                  label = { Text("段数") },
                  singleLine = true,
                )
              }
              WorkoutExerciseType.REPS,
              WorkoutExerciseType.TIMED -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(onClick = { viewModel.adjustAmount(-1) }) { Text("−") }
                  OutlinedTextField(
                    value = state.amount,
                    onValueChange = viewModel::updateAmount,
                    modifier = Modifier.weight(1f),
                    label = { Text(active.unit.label) },
                    singleLine = true,
                  )
                  OutlinedButton(onClick = { viewModel.adjustAmount(1) }) { Text("+") }
                }
                Button(onClick = viewModel::recordSet, modifier = Modifier.fillMaxWidth()) { Text("セットを記録") }
              }
            }
            OutlinedTextField(
              value = state.memo,
              onValueChange = viewModel::updateMemo,
              modifier = Modifier.fillMaxWidth(),
              label = { Text("セットメモ") },
              minLines = 2,
            )
            if (state.activeSets.isNotEmpty()) {
              TextButton(onClick = viewModel::undoActiveSet) { Text("直前のセットを取り消す") }
              HorizontalDivider()
              state.activeSets.forEachIndexed { index, set ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                  Text("セット ${index + 1}: ${formatSet(set)}", fontWeight = FontWeight.SemiBold)
                  if (set.memo.isNotBlank()) Text(set.memo, style = MaterialTheme.typography.bodySmall)
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StopwatchControls(
  seconds: Int,
  running: Boolean,
  onStart: () -> Unit,
  onPause: () -> Unit,
  onReset: () -> Unit,
  onRecord: () -> Unit,
) {
  Text(formatDuration(seconds), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(onClick = onStart, enabled = !running) { Text("開始") }
    OutlinedButton(onClick = onPause, enabled = running) { Text("一時停止") }
    OutlinedButton(onClick = onReset) { Text("リセット") }
  }
  Button(onClick = onRecord, enabled = seconds > 0, modifier = Modifier.fillMaxWidth()) { Text("計測値を記録") }
}

@Composable
private fun WorkoutTimerScreen(state: WorkoutUiState, viewModel: WorkoutViewModel, modifier: Modifier) {
  LazyColumn(
    modifier = modifier,
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text("休憩タイマー", style = MaterialTheme.typography.titleLarge)
          Text(formatDuration(state.intervalRemainingSeconds), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(30, 60, 90, 120).forEach { seconds ->
              FilterChip(
                selected = state.intervalDurationSeconds == seconds,
                onClick = { viewModel.setIntervalDuration(seconds) },
                label = { Text("${seconds}秒") },
              )
            }
          }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::startInterval, enabled = !state.intervalRunning) { Text("開始") }
            OutlinedButton(onClick = viewModel::pauseInterval, enabled = state.intervalRunning) { Text("一時停止") }
            OutlinedButton(onClick = viewModel::resetInterval) { Text("リセット") }
          }
        }
      }
    }
    item {
      Text("プランク・踏み台昇降は「記録」タブで種目を選択すると、専用ストップウォッチからそのままセットとして保存できます。")
    }
  }
}

@Composable
private fun WorkoutHistoryScreen(state: WorkoutUiState, modifier: Modifier) {
  val clipboard = LocalClipboard.current
  val coroutineScope = rememberCoroutineScope()
  LazyColumn(
    modifier = modifier,
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (state.snapshot.history.isEmpty()) {
      item { Text("保存済みのワークアウトはありません。") }
    }
    items(state.snapshot.history, key = { it.id }) { history ->
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              history.date,
              modifier = Modifier.weight(1f),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
            )
            IconButton(
              onClick = {
                coroutineScope.launch {
                  clipboard.setClipEntry(
                    ClipEntry(
                      ClipData.newPlainText(
                        "ワークアウト ${history.date}",
                        formatWorkoutHistoryForCopy(history),
                      ),
                    ),
                  )
                }
              },
            ) {
              Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "${history.date}のワークアウト履歴をコピー",
              )
            }
          }
          Text("${history.sets.size} セット", style = MaterialTheme.typography.bodySmall)
          history.sets.groupBy { it.exerciseId }.values.forEach { sets ->
            Text(formatWorkoutHistoryExercise(sets))
          }
        }
      }
    }
  }
}

@Composable
private fun WorkoutSettingsScreen(state: WorkoutUiState, viewModel: WorkoutViewModel, modifier: Modifier) {
  var name by remember { mutableStateOf("") }
  var targetSets by remember { mutableStateOf("3") }
  var unit by remember { mutableStateOf(WorkoutUnit.REPS) }
  LazyColumn(
    modifier = modifier,
    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("種目を追加", style = MaterialTheme.typography.titleLarge)
          OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("種目名") })
          OutlinedTextField(value = targetSets, onValueChange = { targetSets = it.filter(Char::isDigit).take(2) }, modifier = Modifier.fillMaxWidth(), label = { Text("目標セット数") }, singleLine = true)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WorkoutUnit.entries.forEach { candidate ->
              FilterChip(selected = unit == candidate, onClick = { unit = candidate }, label = { Text(candidate.label) })
            }
          }
          Button(
            onClick = {
              viewModel.addExercise(name, targetSets.toIntOrNull() ?: 1, unit)
              name = ""
            },
            enabled = name.isNotBlank(),
          ) { Text("追加") }
        }
      }
    }
    item {
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("登録済み種目", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = viewModel::restoreDefaultExercises) { Text("初期メニューに戻す") }
      }
    }
    items(state.snapshot.exercises, key = { it.id }) { exercise ->
      Card(Modifier.fillMaxWidth()) {
        Row(
          Modifier.fillMaxWidth().padding(14.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(Modifier.weight(1f)) {
            Text(exercise.name, fontWeight = FontWeight.SemiBold)
            Text("目標 ${exercise.targetSets}セット · ${exercise.unit.label}", style = MaterialTheme.typography.bodySmall)
          }
          Spacer(Modifier.width(8.dp))
          TextButton(onClick = { viewModel.removeExercise(exercise.id) }) { Text("削除") }
        }
      }
    }
  }
}

private fun formatSet(set: WorkoutSet): String = buildString {
  append(if (set.unit == WorkoutUnit.SECONDS) formatDuration(set.amount) else "${set.amount}${set.unit.label}")
  set.steps?.takeIf { it > 0 }?.let { append(" · ${it}段") }
}

private fun formatDuration(totalSeconds: Int): String {
  val seconds = totalSeconds.coerceAtLeast(0)
  val hours = seconds / 3600
  val minutes = (seconds % 3600) / 60
  val rest = seconds % 60
  return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%02d:%02d".format(minutes, rest)
}
