package dev.terashima.yomitorirss.feature.asset

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.core.webcollector.SecureWebCollectorDialog
import dev.terashima.yomitorirss.core.webcollector.WebCollectorConfig
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssetUiState(
  val loading: Boolean = true,
  val overview: AssetOverview? = null,
  val message: String? = null,
)

class AssetViewModel(private val repository: AssetRepository) : ViewModel() {
  private val mutableState = MutableStateFlow(AssetUiState())
  val state: StateFlow<AssetUiState> = mutableState.asStateFlow()

  init {
    reload()
  }

  fun importDelimited(uri: String) = runOperation("資産履歴をインポートしました") {
    repository.importDelimited(uri)
  }

  fun importMoneyForward(json: String) = runOperation("MoneyForward の資産を記録しました") {
    repository.importMoneyForwardJson(json)
  }

  fun setCategory(assetName: String, category: String) = runOperation("カテゴリを更新しました") {
    repository.setCategory(assetName, category)
  }

  fun dismissMessage() = mutableState.update { it.copy(message = null) }

  private fun reload() {
    viewModelScope.launch {
      mutableState.update { it.copy(loading = true) }
      runCatching { repository.loadOverview() }
        .onSuccess { overview -> mutableState.value = AssetUiState(loading = false, overview = overview) }
        .onFailure { error -> mutableState.update { it.copy(loading = false, message = error.message ?: "資産データを読み込めませんでした") } }
    }
  }

  private fun runOperation(successMessage: String, operation: suspend () -> Unit) {
    viewModelScope.launch {
      mutableState.update { it.copy(loading = true, message = null) }
      runCatching { operation() }
        .onSuccess {
          val overview = repository.loadOverview()
          mutableState.value = AssetUiState(loading = false, overview = overview, message = successMessage)
        }
        .onFailure { error -> mutableState.update { it.copy(loading = false, message = error.message ?: "処理に失敗しました") } }
    }
  }

  class Factory(private val repository: AssetRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AssetViewModel(repository) as T
  }
}

@Composable
fun AssetManagementDialog(
  viewModel: AssetViewModel,
  onDismiss: () -> Unit,
) {
  val state by viewModel.state.collectAsStateWithLifecycleCompat()
  var showMoneyForward by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<AssetCategorySetting?>(null) }
  var categoryText by remember { mutableStateOf("") }
  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.toString()?.let(viewModel::importDelimited)
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
  ) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("資産管理", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
          TextButton(onClick = onDismiss) { Text("閉じる") }
        }
        HorizontalDivider()
        if (state.loading && state.overview == null) {
          Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
          }
        } else {
          val overview = state.overview ?: AssetOverview(null, 0, emptyMap(), emptyList(), emptyList())
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
              Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                Button(
                  modifier = Modifier.weight(1f),
                  onClick = { importLauncher.launch(arrayOf("text/csv", "text/tab-separated-values", "text/plain", "application/csv")) },
                ) { Text("CSV / TSV") }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = { showMoneyForward = true }) {
                  Text("MoneyForward")
                }
              }
            }
            state.message?.let { message ->
              item {
                Text(
                  message,
                  modifier = Modifier.fillMaxWidth().clickable(viewModel::dismissMessage).padding(horizontal = 16.dp, vertical = 8.dp),
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            item {
              Column(modifier = Modifier.padding(16.dp)) {
                Text("資産総額", style = MaterialTheme.typography.labelLarge)
                Text(formatYen(overview.total), style = MaterialTheme.typography.headlineMedium)
                overview.latestDate?.let { Text("${it} 時点", style = MaterialTheme.typography.bodySmall) }
              }
            }
            if (overview.history.isNotEmpty()) {
              item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                  Text("資産推移", style = MaterialTheme.typography.titleMedium)
                  Spacer(Modifier.height(8.dp))
                  AssetHistoryChart(overview.history)
                }
              }
            }
            item {
              Text("最新のカテゴリ別内訳", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
            items(overview.latestByCategory.entries.sortedByDescending { it.value }) { entry ->
              ListItem(
                headlineContent = { Text(entry.key) },
                trailingContent = { Text(formatYen(entry.value)) },
              )
            }
            item { HorizontalDivider() }
            item {
              Text("資産項目のカテゴリ設定", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
            }
            items(overview.categorySettings, key = { it.assetName }) { setting ->
              ListItem(
                modifier = Modifier.clickable {
                  editing = setting
                  categoryText = setting.category
                },
                headlineContent = { Text(setting.assetName) },
                supportingContent = { Text(setting.category) },
              )
            }
            item { Spacer(Modifier.height(24.dp)) }
          }
        }
      }
    }
  }

  if (showMoneyForward) {
    SecureWebCollectorDialog(
      config = moneyForwardCollectorConfig(),
      onDismiss = { showMoneyForward = false },
      onResult = viewModel::importMoneyForward,
    )
  }

  editing?.let { setting ->
    AlertDialog(
      onDismissRequest = { editing = null },
      title = { Text("カテゴリ設定") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(setting.assetName)
          OutlinedTextField(
            value = categoryText,
            onValueChange = { categoryText = it },
            label = { Text("カテゴリ") },
            singleLine = true,
          )
        }
      },
      confirmButton = {
        TextButton(
          enabled = categoryText.isNotBlank(),
          onClick = {
            viewModel.setCategory(setting.assetName, categoryText)
            editing = null
          },
        ) { Text("保存") }
      },
      dismissButton = { TextButton(onClick = { editing = null }) { Text("キャンセル") } },
    )
  }
}

@Composable
private fun AssetHistoryChart(points: List<AssetHistoryPoint>) {
  val lineColor = MaterialTheme.colorScheme.primary
  val mutedColor = MaterialTheme.colorScheme.outlineVariant
  val totals = points.map { it.total }
  val min = totals.minOrNull() ?: 0L
  val max = totals.maxOrNull() ?: 0L
  Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
    drawLine(mutedColor, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f))
    if (points.size == 1) {
      drawCircle(lineColor, 6f, Offset(size.width / 2f, size.height / 2f))
      return@Canvas
    }
    val range = (max - min).takeIf { it > 0 } ?: 1L
    val path = Path()
    points.forEachIndexed { index, point ->
      val x = size.width * index / (points.size - 1f)
      val normalized = (point.total - min).toFloat() / range.toFloat()
      val y = size.height - (normalized * (size.height - 16f)) - 8f
      if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, lineColor, style = Stroke(width = 4f))
  }
  Row(modifier = Modifier.fillMaxWidth()) {
    Text(points.first().date.toString(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
    Text(points.last().date.toString(), style = MaterialTheme.typography.labelSmall)
  }
}

private fun moneyForwardCollectorConfig() = WebCollectorConfig(
  title = "MoneyForward ME 資産取得",
  startUrl = MONEY_FORWARD_PORTFOLIO_URL,
  profileName = "mosaic-moneyforward-assets",
  allowedBridgeOrigins = setOf("https://moneyforward.com"),
  allowedNavigationHosts = setOf("moneyforward.com"),
  collectableUrlPrefixes = setOf(MONEY_FORWARD_PORTFOLIO_URL),
  collectScript = MONEY_FORWARD_COLLECT_SCRIPT,
)

private val MONEY_FORWARD_COLLECT_SCRIPT =
  """
    (() => {
      const bridge = window.MosaicCollectorBridge;
      const send = (value) => bridge.postMessage(JSON.stringify(value));
      try {
        const clean = (value) => (value || '').replace(/\s+/g, ' ').trim();
        const amount = (value) => {
          const normalized = clean(value).replace(/[,，円\\]/g, '').replace(/\s/g, '');
          const match = normalized.match(/-?\d+/);
          return match ? Number(match[0]) : NaN;
        };
        const nameHeaders = new Set(['種類・名称', '銘柄名', '名称']);
        const amountHeaders = new Set(['残高', '評価額', '現在価値']);
        const accountHeaders = new Set(['保有金融機関']);
        const entries = [];
        const root = document.querySelector('#bs-portfolio') || document;
        root.querySelectorAll('table').forEach((table) => {
          const headerRow = Array.from(table.querySelectorAll('tr')).find((row) => row.querySelectorAll('th').length > 0);
          if (!headerRow) return;
          const headers = Array.from(headerRow.querySelectorAll('th')).map((cell) => clean(cell.textContent));
          const nameIndex = headers.findIndex((header) => nameHeaders.has(header));
          const amountIndex = headers.findIndex((header) => amountHeaders.has(header));
          const accountIndex = headers.findIndex((header) => accountHeaders.has(header));
          if (nameIndex < 0 || amountIndex < 0) return;
          table.querySelectorAll('tr').forEach((row) => {
            const cells = Array.from(row.querySelectorAll('td'));
            if (!cells.length || cells.length <= Math.max(nameIndex, amountIndex)) return;
            const name = clean(cells[nameIndex]?.textContent);
            const value = amount(cells[amountIndex]?.textContent);
            const account = accountIndex >= 0 ? clean(cells[accountIndex]?.textContent) : '';
            if (name && Number.isFinite(value)) entries.push({ name, amount: value, account });
          });
        });
        if (!entries.length) throw new Error('資産テーブルを解析できませんでした。MoneyForward の資産ページを表示してから再実行してください。');
        const now = new Date();
        const date = [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
        send({
          type: 'result',
          payload: JSON.stringify({ format: 'moneyforward-asset-snapshot', version: 1, date, entries }),
        });
      } catch (error) {
        send({ type: 'error', message: error?.message || 'MoneyForward の資産取得に失敗しました' });
      }
    })();
  """.trimIndent()

private fun formatYen(value: Long): String = "¥${NumberFormat.getNumberInstance(Locale.JAPAN).format(value)}"

@Composable
private fun <T> StateFlow<T>.collectAsStateWithLifecycleCompat(): androidx.compose.runtime.State<T> =
  androidx.compose.runtime.collectAsState()

private const val MONEY_FORWARD_PORTFOLIO_URL = "https://moneyforward.com/bs/portfolio"
