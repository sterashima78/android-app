package dev.terashima.yomitorirss.feature.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HealthRoute(
  viewModelFactory: HealthViewModel.Factory,
  readPermissions: Set<String>,
  modifier: Modifier = Modifier,
) {
  val viewModel: HealthViewModel = viewModel(factory = viewModelFactory)
  val state by viewModel.state.collectAsState()
  val permissionLauncher = rememberLauncherForActivityResult(
    PermissionController.createRequestPermissionResultContract(),
  ) {
    viewModel.onPermissionResult()
  }

  HealthScreen(
    state = state,
    onSelectPeriod = viewModel::selectPeriod,
    onSelectDate = viewModel::selectDate,
    onPrevious = viewModel::movePrevious,
    onNext = viewModel::moveNext,
    onGoToday = viewModel::goToday,
    onRefresh = viewModel::refresh,
    onRequestPermissions = { permissionLauncher.launch(readPermissions) },
    modifier = modifier,
  )
}

@Composable
private fun HealthScreen(
  state: HealthUiState,
  onSelectPeriod: (HealthPeriod) -> Unit,
  onSelectDate: (LocalDate) -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onGoToday: () -> Unit,
  onRefresh: () -> Unit,
  onRequestPermissions: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when (state) {
    HealthUiState.Loading -> CenteredMessage(modifier) {
      CircularProgressIndicator()
      Text("Health Connect からデータを取得中")
    }
    HealthUiState.PermissionRequired -> CenteredMessage(modifier) {
      Text("Health Connect のアクセス権限が必要です", style = MaterialTheme.typography.titleMedium)
      Text("歩数・活動消費カロリー・運動・心拍・睡眠・体重・体脂肪率・栄養を読み取り、このアプリで終了したワークアウトのみ運動データとして書き込みます。")
      Button(onClick = onRequestPermissions) { Text("アクセスを許可") }
    }
    HealthUiState.HistoryPermissionRequired -> CenteredMessage(modifier) {
      Text("過去データへのアクセス権限が必要です", style = MaterialTheme.typography.titleMedium)
      Text("選択した期間は通常の30日履歴より前を含みます。Health Connect の「過去のデータ」権限を許可すると、その期間を直接再取得できます。")
      Button(onClick = onRequestPermissions) { Text("過去データへのアクセスを許可") }
      OutlinedButton(onClick = onGoToday) { Text("今日に戻る") }
    }
    HealthUiState.HistoryUnsupported -> CenteredMessage(modifier) {
      Text("この端末では古い履歴を取得できません", style = MaterialTheme.typography.titleMedium)
      Text("Health Connect の履歴読み取り機能が利用できないため、30日より前を含む期間は表示できません。")
      OutlinedButton(onClick = onGoToday) { Text("今日に戻る") }
    }
    HealthUiState.Unavailable -> CenteredMessage(modifier) {
      Text("この端末では Health Connect を利用できません")
    }
    HealthUiState.ProviderUpdateRequired -> CenteredMessage(modifier) {
      Text("Health Connect を利用できる状態に更新してください")
      OutlinedButton(onClick = onRefresh) { Text("再確認") }
    }
    is HealthUiState.Error -> CenteredMessage(modifier) {
      Text(state.message)
      OutlinedButton(onClick = onRefresh) { Text("再読み込み") }
      OutlinedButton(onClick = onGoToday) { Text("今日に戻る") }
    }
    is HealthUiState.Content -> HealthContent(
      state = state,
      onSelectPeriod = onSelectPeriod,
      onSelectDate = onSelectDate,
      onPrevious = onPrevious,
      onNext = onNext,
      onGoToday = onGoToday,
      onRefresh = onRefresh,
      onRequestPermissions = onRequestPermissions,
      modifier = modifier,
    )
  }
}

@Composable
private fun HealthContent(
  state: HealthUiState.Content,
  onSelectPeriod: (HealthPeriod) -> Unit,
  onSelectDate: (LocalDate) -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onGoToday: () -> Unit,
  onRefresh: () -> Unit,
  onRequestPermissions: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showDatePicker by remember { mutableStateOf(false) }
  val latestBodyFatPercentage = latestBodyFatPercentage(state.overview.bodyFatMeasurements)
  val isDay = state.period == HealthPeriod.DAY
  val metrics = listOf(
    Metric(if (isDay) "歩数" else "合計歩数", formatLong(state.overview.steps), "歩"),
    Metric(if (isDay) "活動消費" else "活動消費合計", formatCalories(state.overview.activeCaloriesKcal), "kcal"),
    Metric(if (isDay) "運動" else "運動合計", formatLong(state.overview.exerciseMinutes), "分"),
    Metric("平均心拍", formatLong(state.overview.averageHeartRateBpm), "bpm"),
    Metric(if (isDay) "睡眠" else "睡眠合計", formatLong(state.overview.sleepMinutes), "分"),
    Metric("平均体重", formatWeight(state.overview.averageWeightKg), "kg"),
    Metric("最新体脂肪率", formatBodyFat(latestBodyFatPercentage), "%"),
  )

  if (showDatePicker) {
    HealthDatePicker(
      selectedDate = state.selectedDate,
      onDismiss = { showDatePicker = false },
      onSelect = { date ->
        showDatePicker = false
        onSelectDate(date)
      },
    )
  }

  LazyColumn(
    modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        HealthPeriod.entries.forEach { period ->
          if (period == state.period) {
            Button(onClick = { onSelectPeriod(period) }) {
              Text(period.label)
            }
          } else {
            OutlinedButton(onClick = { onSelectPeriod(period) }) {
              Text(period.label)
            }
          }
        }
      }
    }
    item {
      PeriodNavigation(
        state = state,
        onPrevious = onPrevious,
        onNext = onNext,
        onChooseDate = { showDatePicker = true },
        onGoToday = onGoToday,
      )
    }
    item {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
          onClick = onRefresh,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("選択期間を Health Connect から再取得")
        }
        Text(
          "最終取得: ${formatRefreshTime(state.refreshedAt)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    items(metrics) { metric -> MetricCard(metric) }
    if (!isDay) {
      item {
        DailySummaryCard(state.overview.dailySummaries)
      }
    }
    if (state.period != HealthPeriod.MONTH) {
      item {
        ExerciseHistoryCard(state.overview.exerciseSessions)
      }
    }
    item {
      NutritionHistoryCard(state.overview.nutritionDailyIntakes)
    }
    item {
      BodyFatHistoryChart(state.overview.bodyFatMeasurements)
    }
    item {
      OutlinedButton(
        onClick = onRequestPermissions,
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
      ) {
        Text("Health Connect 権限を確認")
      }
    }
  }
}

@Composable
private fun PeriodNavigation(
  state: HealthUiState.Content,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onChooseDate: () -> Unit,
  onGoToday: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(onClick = onPrevious) { Text("前へ") }
      OutlinedButton(
        onClick = onChooseDate,
        modifier = Modifier.weight(1f),
      ) {
        Text(formatRangeLabel(state.period, state.range))
      }
      OutlinedButton(onClick = onNext, enabled = state.canMoveNext) { Text("次へ") }
    }
    TextButton(onClick = onGoToday, modifier = Modifier.align(Alignment.End)) {
      Text("今日を表示")
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthDatePicker(
  selectedDate: LocalDate,
  onDismiss: () -> Unit,
  onSelect: (LocalDate) -> Unit,
) {
  val selectedMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
  val pickerState = rememberDatePickerState(initialSelectedDateMillis = selectedMillis)
  DatePickerDialog(
    onDismissRequest = onDismiss,
    confirmButton = {
      TextButton(
        onClick = {
          pickerState.selectedDateMillis?.let { millis ->
            onSelect(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
          } ?: onDismiss()
        },
      ) {
        Text("選択")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("キャンセル") }
    },
  ) {
    DatePicker(state = pickerState)
  }
}

@Composable
private fun DailySummaryCard(summaries: List<DailyHealthSummary>) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("日別", style = MaterialTheme.typography.titleMedium)
      if (summaries.isEmpty()) {
        Text("日別データはありません", color = MaterialTheme.colorScheme.onSurfaceVariant)
      } else {
        summaries.forEachIndexed { index, summary ->
          if (index > 0) HorizontalDivider()
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatDailyDate(summary.date), style = MaterialTheme.typography.labelLarge)
            Text(
              "${formatLong(summary.steps)} 歩  ・  ${formatCalories(summary.activeCaloriesKcal)} kcal  ・  運動 ${formatLong(summary.exerciseMinutes)} 分",
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              "睡眠 ${formatLong(summary.sleepMinutes)} 分  ・  心拍 ${formatLong(summary.averageHeartRateBpm)} bpm  ・  体重 ${formatWeight(summary.averageWeightKg)} kg",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }
}

private data class Metric(val title: String, val value: String, val unit: String)

@Composable
private fun MetricCard(metric: Metric) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(metric.title, style = MaterialTheme.typography.labelLarge)
      Text(
        if (metric.value == "—") metric.value else "${metric.value} ${metric.unit}",
        style = MaterialTheme.typography.headlineSmall,
      )
    }
  }
}

@Composable
private fun CenteredMessage(
  modifier: Modifier,
  content: @Composable () -> Unit,
) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      content()
    }
  }
}

private fun formatRangeLabel(period: HealthPeriod, range: HealthDateRange): String = when (period) {
  HealthPeriod.DAY -> range.startDate.format(DAY_FORMATTER)
  HealthPeriod.WEEK ->
    "${range.startDate.format(SHORT_DATE_FORMATTER)} 〜 ${range.endDateExclusive.minusDays(1).format(SHORT_DATE_FORMATTER)}"
  HealthPeriod.MONTH -> range.startDate.format(MONTH_FORMATTER)
}

private fun formatDailyDate(date: LocalDate): String = date.format(DAILY_FORMATTER)

private fun formatRefreshTime(instant: Instant): String = REFRESH_FORMATTER.format(instant)

private fun formatLong(value: Long?): String = value?.toString() ?: "—"

private fun formatCalories(value: Double?): String =
  value?.let { String.format(Locale.getDefault(), "%.0f", it) } ?: "—"

private fun formatWeight(value: Double?): String =
  value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"

private fun formatBodyFat(value: Double?): String = value?.let(::formatBodyFatPercentage) ?: "—"

private val DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月d日(E)", Locale.JAPANESE)
private val SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPANESE)
private val MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy年M月", Locale.JAPANESE)
private val DAILY_FORMATTER = DateTimeFormatter.ofPattern("M/d(E)", Locale.JAPANESE)
private val REFRESH_FORMATTER = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.JAPANESE)
  .withZone(ZoneId.systemDefault())
