package dev.terashima.yomitorirss.feature.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun RssWebScrapingRulesUi(
  recommendationPolicy: RssRecommendationPolicy,
  recommendationPendingFeedbackCount: Int,
  recommendationPendingPriorityFeedbackCount: Int,
  onSaveRecommendationCondition: (String) -> Unit,
  onRecommendationProviderChange: (RssRecommendationExecutionProvider) -> Unit,
  onResetRecommendationLearning: () -> Unit,
  onResetPriorityRecommendationLearning: () -> Unit,
  rules: List<RssWebScrapingRule>,
  testState: WebScrapingRuleTestUiState,
  onSave: (String?, String, String, Int) -> Unit,
  onDelete: (RssWebScrapingRule) -> Unit,
  onTest: (String, String, Int, String) -> Unit,
  onClearTest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var editingRule by remember { mutableStateOf<RssWebScrapingRule?>(null) }
  var creatingRule by remember { mutableStateOf(false) }
  var manualRecommendationCondition by remember(recommendationPolicy.revision) {
    mutableStateOf(recommendationPolicy.manualCondition)
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("記事推薦", style = MaterialTheme.typography.titleLarge)
        Text(
          "除外・優先条件から未読記事の読みたい優先度を1〜10で評価します。1は低優先、10は高優先です。" +
            "タイトルだけでは判断できない場合や判定に失敗した場合は数値を付けず、理由を記事一覧に表示します。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("記事評価・優先と除外の同時学習の実行先", style = MaterialTheme.typography.titleMedium)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
              selected = recommendationPolicy.executionProvider == RssRecommendationExecutionProvider.LOCAL,
              onClick = { onRecommendationProviderChange(RssRecommendationExecutionProvider.LOCAL) },
            )
            Text("端末内AI")
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
              selected = recommendationPolicy.executionProvider == RssRecommendationExecutionProvider.CLOUD,
              onClick = { onRecommendationProviderChange(RssRecommendationExecutionProvider.CLOUD) },
            )
            Text("クラウドAI")
          }
        }
        Text(
          if (recommendationPolicy.executionProvider == RssRecommendationExecutionProvider.CLOUD) {
            "クラウド実行では手動条件、学習した優先・除外条件、評価対象の記事タイトル、両種類の参考記事のタイトルと直前評価をクラウドAIへ送信します。クラウドAI設定で事前に接続と利用モデルの選択が必要です。"
          } else {
            "端末内AIで記事評価と優先・除外条件の同時学習を実行します。記事タイトルや条件は端末外へ送信しません。"
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
          value = manualRecommendationCondition,
          onValueChange = { manualRecommendationCondition = it },
          modifier = Modifier.fillMaxWidth(),
          label = { Text("手動の除外条件") },
          placeholder = { Text("例: 単純なセール告知やキャンペーン記事は重要度を下げる") },
          minLines = 3,
          maxLines = 8,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = { onSaveRecommendationCondition(manualRecommendationCondition) },
            enabled = manualRecommendationCondition.trim() != recommendationPolicy.manualCondition,
          ) {
            Text("保存")
          }
        }
        Text("学習された除外条件", style = MaterialTheme.typography.titleMedium)
        Text(
          recommendationPolicy.learnedCondition.ifBlank { "まだ学習された条件はありません。" },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          recommendationFeedbackStatusText(recommendationPendingFeedbackCount, recommendationPendingPriorityFeedbackCount),
          style = MaterialTheme.typography.labelSmall,
          color = if (recommendationPendingFeedbackCount > 0) {
            MaterialTheme.colorScheme.secondary
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(onClick = onResetRecommendationLearning) {
              Text("除外学習条件をリセット")
            }
          }
        Text("学習された優先条件", style = MaterialTheme.typography.titleMedium)
        Text(
          recommendationPolicy.learnedPriorityCondition.ifBlank { "まだ学習された優先条件はありません。" },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
          ) {
            TextButton(onClick = onResetPriorityRecommendationLearning) {
              Text("優先学習条件をリセット")
            }
          }
        Text(
          "RSS記事を「あとで読む」に追加すると、自動的に優先参考にも登録されます。優先・除外の参考は両方の学習条件へ同時に反映します。学習条件のリセット時は、対応する種類の学習済み参考履歴も消去されます。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }

    item { HorizontalDivider() }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Web 取得ルール", style = MaterialTheme.typography.titleLarge)
        Text(
          "URL パターンに一致する Web ページを専用 WebView で開き、登録したスクリプトからフィードを生成します。" +
            "既存の漫画向け専用取得は残し、カスタムルールが一致する場合だけこちらを優先します。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.End,
        ) {
          TextButton(
            onClick = {
              onClearTest()
              creatingRule = true
            },
          ) {
            Text("ルールを追加")
          }
        }
      }
    }

    if (rules.isEmpty()) {
      item {
        Text(
          "登録済みの Web 取得ルールはありません。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    } else {
      items(rules, key = RssWebScrapingRule::id) { rule ->
        Card(modifier = Modifier.fillMaxWidth()) {
          Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              rule.urlPattern,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.bodyMedium,
            )
            Text(
              "WebView タイムアウト ${rule.timeoutSeconds} 秒",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.End,
            ) {
              TextButton(
                onClick = {
                  onClearTest()
                  editingRule = rule
                },
              ) {
                Text("編集")
              }
              TextButton(onClick = { onDelete(rule) }) {
                Text("削除")
              }
            }
          }
        }
      }
    }
  }

  if (creatingRule || editingRule != null) {
    RssWebScrapingRuleEditorDialog(
      rule = editingRule,
      testState = testState,
      onSave = { id, pattern, code, timeout ->
        onSave(id, pattern, code, timeout)
        creatingRule = false
        editingRule = null
      },
      onTest = onTest,
      onClearTest = onClearTest,
      onDismiss = {
        onClearTest()
        creatingRule = false
        editingRule = null
      },
    )
  }
}

@Composable
private fun RssWebScrapingRuleEditorDialog(
  rule: RssWebScrapingRule?,
  testState: WebScrapingRuleTestUiState,
  onSave: (String?, String, String, Int) -> Unit,
  onTest: (String, String, Int, String) -> Unit,
  onClearTest: () -> Unit,
  onDismiss: () -> Unit,
) {
  var urlPattern by remember(rule?.id) { mutableStateOf(rule?.urlPattern.orEmpty()) }
  var functionCode by remember(rule?.id) {
    mutableStateOf(rule?.functionCode ?: DEFAULT_RSS_WEB_SCRAPING_FUNCTION)
  }
  var timeoutText by remember(rule?.id) {
    mutableStateOf((rule?.timeoutSeconds ?: DEFAULT_RSS_WEB_SCRAPING_TIMEOUT_SECONDS).toString())
  }
  var testUrl by remember(rule?.id) { mutableStateOf("") }
  var testedSignature by remember(rule?.id) { mutableStateOf<String?>(null) }
  val timeoutSeconds = timeoutText.toIntOrNull()
  val timeoutValid = timeoutSeconds != null &&
    timeoutSeconds in MIN_RSS_WEB_SCRAPING_TIMEOUT_SECONDS..MAX_RSS_WEB_SCRAPING_TIMEOUT_SECONDS
  val currentSignature = remember(urlPattern, functionCode, timeoutText, testUrl) {
    listOf(urlPattern, functionCode, timeoutText, testUrl).joinToString("\u0000")
  }
  val showTestResult = testedSignature == currentSignature

  LaunchedEffect(rule?.id) { onClearTest() }

  Dialog(
    onDismissRequest = { if (!testState.running) onDismiss() },
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnClickOutside = false,
    ),
  ) {
    Surface(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing),
      color = MaterialTheme.colorScheme.surface,
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .imePadding()
          .padding(horizontal = 20.dp),
      ) {
        Text(
          if (rule == null) "Web 取得ルールを追加" else "Web 取得ルールを編集",
          modifier = Modifier.padding(top = 20.dp),
          style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(10.dp))
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            "URL パターンは HTTPS glob です。* は任意長、? は1文字に一致します。",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = urlPattern,
            onValueChange = {
              urlPattern = it
              testedSignature = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL パターン") },
            placeholder = { Text("https://example.com/series/*") },
            singleLine = true,
          )
          OutlinedTextField(
            value = timeoutText,
            onValueChange = {
              timeoutText = it.filter(Char::isDigit)
              testedSignature = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("WebView タイムアウト（秒）") },
            supportingText = {
              Text("$MIN_RSS_WEB_SCRAPING_TIMEOUT_SECONDS〜$MAX_RSS_WEB_SCRAPING_TIMEOUT_SECONDS 秒")
            },
            isError = timeoutText.isNotBlank() && !timeoutValid,
            singleLine = true,
          )
          Text(
            "関数はページコンテキストで実行され、Promise<{ title, siteUrl?, items }> を返します。" +
              "items は title と url が必須、externalId と publishedAt は任意です。",
            style = MaterialTheme.typography.bodySmall,
          )
          OutlinedTextField(
            value = functionCode,
            onValueChange = {
              functionCode = it
              testedSignature = null
            },
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 280.dp),
            label = { Text("取得スクリプト") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            minLines = 12,
          )

          HorizontalDivider()
          Text("実行テスト", style = MaterialTheme.typography.titleMedium)
          Text(
            "保存前の URL パターンとスクリプトをそのまま実行し、実際に生成されるデータを確認します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          OutlinedTextField(
            value = testUrl,
            onValueChange = {
              testUrl = it
              testedSignature = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("テスト URL") },
            placeholder = { Text("https://example.com/series/sample") },
            singleLine = true,
          )
          TextButton(
            onClick = {
              val timeout = timeoutSeconds ?: return@TextButton
              testedSignature = currentSignature
              onTest(urlPattern, functionCode, timeout, testUrl)
            },
            enabled = !testState.running &&
              urlPattern.isNotBlank() &&
              functionCode.isNotBlank() &&
              testUrl.isNotBlank() &&
              timeoutValid,
          ) {
            Text(if (testState.running) "実行中…" else "実行テスト")
          }
          if (testState.running && showTestResult) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
          }
          if (showTestResult) {
            testState.error?.let { error ->
              Text(
                "取得失敗: $error",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
            testState.result?.let { result ->
              RssWebScrapingPreviewCard(result)
            }
          }
          Spacer(Modifier.height(8.dp))
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
          horizontalArrangement = Arrangement.End,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(onClick = onDismiss, enabled = !testState.running) {
            Text("キャンセル")
          }
          TextButton(
            onClick = {
              val timeout = timeoutSeconds ?: return@TextButton
              onSave(rule?.id, urlPattern, functionCode, timeout)
            },
            enabled = !testState.running &&
              urlPattern.isNotBlank() &&
              functionCode.isNotBlank() &&
              timeoutValid,
          ) {
            Text("保存")
          }
        }
      }
    }
  }
}

@Composable
private fun RssWebScrapingPreviewCard(result: RssWebScrapingPreview) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text("取得成功", style = MaterialTheme.typography.titleSmall)
      Text("フィード名: ${result.title}")
      result.siteUrl?.let { Text("サイト URL: $it", style = MaterialTheme.typography.bodySmall) }
      Text("記事: ${result.items.size} 件")
      result.items.take(MAX_TEST_PREVIEW_ITEMS).forEachIndexed { index, item ->
        HorizontalDivider()
        Text("${index + 1}. ${item.title}", style = MaterialTheme.typography.bodySmall)
        Text(
          item.url,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        item.publishedAt?.let {
          Text(
            "公開日時: $it",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        item.externalId?.let {
          Text(
            "externalId: $it",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      if (result.items.size > MAX_TEST_PREVIEW_ITEMS) {
        Text(
          "ほか ${result.items.size - MAX_TEST_PREVIEW_ITEMS} 件",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

internal fun recommendationFeedbackStatusText(pendingCount: Int, pendingPriorityCount: Int = 0): String {
  require(pendingCount >= 0 && pendingPriorityCount in 0..pendingCount) { "pendingCount must not be negative" }
  return if (pendingCount > 0) {
    "未処理の参考: ${pendingCount}件（優先${pendingPriorityCount}件、除外${pendingCount - pendingPriorityCount}件）。最後の追加から約30秒後に両方の条件を同時学習し、失敗時は未処理のまま再試行します。"
  } else {
    "未処理の除外参考はありません。未処理の優先参考もありません。追加後に条件が変わらない場合は、既存の学習条件でカバー済みと判定されて処理済みです。"
  }
}

private const val MAX_TEST_PREVIEW_ITEMS = 20

private val DEFAULT_RSS_WEB_SCRAPING_FUNCTION = """
  async ({ url }) => ({
    title: document.querySelector('h1')?.textContent?.trim() || document.title,
    siteUrl: url,
    items: [...document.querySelectorAll('article a[href]')]
      .map((link) => ({
        title: link.textContent?.trim() || '',
        url: link.href,
        externalId: null,
        publishedAt: null,
      }))
      .filter((item) => item.title && item.url),
  })
""".trimIndent()
