package dev.terashima.yomitorirss.feature.asset

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.terashima.yomitorirss.core.webcollector.SecureWebCollectorDialog
import dev.terashima.yomitorirss.core.webcollector.WebCollectorConfig
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
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

class AssetViewModel(
  private val repository: AssetRepository,
  private val onChanged: () -> Unit = {},
) : ViewModel() {
  private val mutableState = MutableStateFlow(AssetUiState())
  val state: StateFlow<AssetUiState> = mutableState.asStateFlow()

  init {
    reload()
  }

  fun importTsv(uri: String) = runOperation("資産履歴をインポートしました") {
    repository.importTsv(uri)
  }

  fun importMoneyForward(json: String) = runOperation("MoneyForward の資産を記録しました") {
    repository.importMoneyForwardJson(json)
  }

  fun setCategory(assetName: String, category: String) = runOperation("カテゴリを更新しました") {
    repository.setCategory(assetName, category)
  }

  fun dismissMessage() = mutableState.update { it.copy(message = null) }

  private fun reload() {
    viewModelScope.launch(Dispatchers.IO) {
      mutableState.update { it.copy(loading = true) }
      runCatching { repository.loadOverview() }
        .onSuccess { overview -> mutableState.value = AssetUiState(loading = false, overview = overview) }
        .onFailure { error ->
          mutableState.update {
            it.copy(loading = false, message = error.message ?: "資産データを読み込めませんでした")
          }
        }
    }
  }

  private fun runOperation(successMessage: String, operation: suspend () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
      mutableState.update { it.copy(loading = true, message = null) }
      runCatching { operation() }
        .onSuccess {
          onChanged()
          val overview = repository.loadOverview()
          mutableState.value = AssetUiState(loading = false, overview = overview, message = successMessage)
        }
        .onFailure { error ->
          mutableState.update { it.copy(loading = false, message = error.message ?: "処理に失敗しました") }
        }
    }
  }

  class Factory(
    private val repository: AssetRepository,
    private val onChanged: () -> Unit = {},
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AssetViewModel(repository, onChanged) as T
  }
}

private enum class AssetTab(val label: String) {
  OVERVIEW("概要"),
  IMPORT("インポート"),
  SETTINGS("設定"),
}

@Composable
fun AssetScreen(
  viewModel: AssetViewModel,
  modifier: Modifier = Modifier,
) {
  val state by viewModel.state.collectAsState()
  var selectedTabIndex by rememberSaveable { mutableIntStateOf(AssetTab.OVERVIEW.ordinal) }
  var showMoneyForward by remember { mutableStateOf(false) }
  var editing by remember { mutableStateOf<AssetCategorySetting?>(null) }
  var categoryText by remember { mutableStateOf("") }
  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.toString()?.let(viewModel::importTsv)
  }

  Column(modifier = modifier.fillMaxSize()) {
    TabRow(selectedTabIndex = selectedTabIndex) {
      AssetTab.entries.forEach { tab ->
        Tab(
          selected = selectedTabIndex == tab.ordinal,
          onClick = { selectedTabIndex = tab.ordinal },
          text = { Text(tab.label) },
        )
      }
    }
    if (state.loading) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    state.message?.let { message ->
      Text(
        message,
        modifier = Modifier
          .fillMaxWidth()
          .clickable { viewModel.dismissMessage() }
          .padding(horizontal = 16.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.primary,
      )
    }

    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
      if (state.loading && state.overview == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      } else {
        val overview = state.overview ?: AssetOverview(null, 0, emptyMap(), emptyList(), emptyList())
        when (AssetTab.entries[selectedTabIndex]) {
          AssetTab.OVERVIEW -> AssetOverviewTab(overview = overview, modifier = Modifier.fillMaxSize())
          AssetTab.IMPORT -> AssetImportTab(
            modifier = Modifier.fillMaxSize(),
            onImportTsv = {
              importLauncher.launch(
                arrayOf("text/tab-separated-values", "text/plain"),
              )
            },
            onImportMoneyForward = { showMoneyForward = true },
          )
          AssetTab.SETTINGS -> AssetSettingsTab(
            settings = overview.categorySettings,
            modifier = Modifier.fillMaxSize(),
            onEdit = { setting ->
              editing = setting
              categoryText = setting.category
            },
          )
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
private fun AssetOverviewTab(
  overview: AssetOverview,
  modifier: Modifier,
) {
  var normalizedChart by rememberSaveable { mutableStateOf(false) }
  val categoryColors = remember(overview.history, overview.latestByCategory) {
    buildAssetCategoryColorMap(
      overview.history.flatMap { it.byCategory.keys } + overview.latestByCategory.keys,
    )
  }

  LazyColumn(modifier = modifier) {
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
          Text("資産構成推移", style = MaterialTheme.typography.titleMedium)
          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            if (normalizedChart) {
              OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { normalizedChart = false },
              ) { Text("金額") }
              Button(
                modifier = Modifier.weight(1f),
                onClick = { normalizedChart = true },
              ) { Text("100%構成比") }
            } else {
              Button(
                modifier = Modifier.weight(1f),
                onClick = { normalizedChart = false },
              ) { Text("金額") }
              OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { normalizedChart = true },
              ) { Text("100%構成比") }
            }
          }
          Spacer(Modifier.height(8.dp))
          AssetStackedHistoryChart(
            points = overview.history,
            normalized = normalizedChart,
            categoryColors = categoryColors,
          )
          Spacer(Modifier.height(12.dp))
          Text("最新のカテゴリ別内訳", style = MaterialTheme.typography.titleMedium)
        }
      }
    } else {
      item {
        Text("最新のカテゴリ別内訳", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleMedium)
      }
    }
    if (overview.latestByCategory.isEmpty()) {
      item {
        Text(
          "資産データがありません。インポートタブからデータを追加してください。",
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      items(overview.latestByCategory.entries.sortedByDescending { it.value }) { entry ->
        ListItem(
          headlineContent = {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              val categoryColor = categoryColors[entry.key] ?: MaterialTheme.colorScheme.outline
              Canvas(Modifier.size(12.dp)) { drawCircle(categoryColor) }
              Text(entry.key)
            }
          },
          trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
              Text(formatYen(entry.value))
              Text(
                formatPercentage(entry.value, overview.total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          },
        )
      }
    }
    item { Spacer(Modifier.height(24.dp)) }
  }
}

@Composable
private fun AssetImportTab(
  modifier: Modifier,
  onImportTsv: () -> Unit,
  onImportMoneyForward: () -> Unit,
) {
  LazyColumn(modifier = modifier) {
    item {
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("TSV", style = MaterialTheme.typography.titleMedium)
        Text(
          "既存の資産履歴をTSVから取り込みます。1列目は日付、2列目は資産名、3列目は金額、4列目は金融機関・口座名（空欄可）です。",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(modifier = Modifier.fillMaxWidth(), onClick = onImportTsv) {
          Text("TSVを選択")
        }
      }
    }
    item { HorizontalDivider() }
    item {
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text("MoneyForward ME", style = MaterialTheme.typography.titleMedium)
        Text(
          "アプリ内WebViewでMoneyForward MEの資産ページを開き、表示中の資産スナップショットを取得します。",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onImportMoneyForward) {
          Text("MoneyForwardを開く")
        }
      }
    }
    item { Spacer(Modifier.height(24.dp)) }
  }
}

@Composable
private fun AssetSettingsTab(
  settings: List<AssetCategorySetting>,
  modifier: Modifier,
  onEdit: (AssetCategorySetting) -> Unit,
) {
  LazyColumn(modifier = modifier) {
    item {
      Column(modifier = Modifier.padding(16.dp)) {
        Text("資産項目のカテゴリ", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
          "カテゴリを変更すると、過去の履歴も現在のカテゴリ設定で再集計されます。",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    if (settings.isEmpty()) {
      item {
        Text(
          "設定できる資産項目がありません。先に資産データをインポートしてください。",
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      items(settings, key = { it.assetName }) { setting ->
        ListItem(
          modifier = Modifier.clickable { onEdit(setting) },
          headlineContent = { Text(setting.assetName) },
          supportingContent = { Text(setting.category) },
        )
      }
    }
    item { Spacer(Modifier.height(24.dp)) }
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
          const normalized = clean(value).replace(/[,，円\s]/g, '');
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
        if (!entries.length) {
          throw new Error('資産テーブルを解析できませんでした。MoneyForward の資産ページを表示してから再実行してください。');
        }
        const now = new Date();
        const date = [
          now.getFullYear(),
          String(now.getMonth() + 1).padStart(2, '0'),
          String(now.getDate()).padStart(2, '0'),
        ].join('-');
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

private fun formatPercentage(value: Long, total: Long): String {
  if (total == 0L) return "—"
  return NumberFormat.getPercentInstance(Locale.JAPAN).apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
  }.format(value.toDouble() / total.toDouble())
}

private const val MONEY_FORWARD_PORTFOLIO_URL = "https://moneyforward.com/bs/portfolio"
