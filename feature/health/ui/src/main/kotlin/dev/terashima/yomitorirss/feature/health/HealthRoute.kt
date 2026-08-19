package dev.terashima.yomitorirss.feature.health

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
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
    onRefresh = viewModel::refresh,
    onRequestPermissions = { permissionLauncher.launch(readPermissions) },
    modifier = modifier,
  )
}

@Composable
private fun HealthScreen(
  state: HealthUiState,
  onSelectPeriod: (HealthPeriod) -> Unit,
  onRefresh: () -> Unit,
  onRequestPermissions: () -> Unit,
  modifier: Modifier = Modifier,
) {
  when (state) {
    HealthUiState.Loading -> CenteredMessage(modifier) {
      CircularProgressIndicator()
      Text("ヘルスデータを読み込み中")
    }
    HealthUiState.PermissionRequired -> CenteredMessage(modifier) {
      Text("Health Connect の読み取り権限が必要です", style = MaterialTheme.typography.titleMedium)
      Text("歩数・運動・心拍・睡眠・体重のみを読み取ります。")
      Button(onClick = onRequestPermissions) { Text("アクセスを許可") }
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
    }
    is HealthUiState.Content -> HealthContent(
      state = state,
      onSelectPeriod = onSelectPeriod,
      onRefresh = onRefresh,
      modifier = modifier,
    )
  }
}

@Composable
private fun HealthContent(
  state: HealthUiState.Content,
  onSelectPeriod: (HealthPeriod) -> Unit,
  onRefresh: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val metrics = listOf(
    Metric("歩数", formatLong(state.overview.steps), "歩"),
    Metric("運動", formatLong(state.overview.exerciseMinutes), "分"),
    Metric("平均心拍", formatLong(state.overview.averageHeartRateBpm), "bpm"),
    Metric("睡眠", formatLong(state.overview.sleepMinutes), "分"),
    Metric("平均体重", formatWeight(state.overview.averageWeightKg), "kg"),
  )

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
            Button(onClick = { onSelectPeriod(period) }, modifier = Modifier.weight(1f)) {
              Text(period.label)
            }
          } else {
            OutlinedButton(onClick = { onSelectPeriod(period) }, modifier = Modifier.weight(1f)) {
              Text(period.label)
            }
          }
        }
      }
    }
    items(metrics) { metric -> MetricCard(metric) }
    item {
      OutlinedButton(
        onClick = onRefresh,
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
      ) {
        Text("最新のデータに更新")
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

private fun formatLong(value: Long?): String = value?.toString() ?: "—"

private fun formatWeight(value: Double?): String =
  value?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"
