@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.terashima.yomitorirss.feature.rss

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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

@Composable
internal fun RssWebScrapingRulesUi(
  rules: List<RssWebScrapingRule>,
  testState: WebScrapingRuleTestUiState,
  onSave: (String?, String, String, Int) -> Unit,
  onDelete: (RssWebScrapingRule) -> Unit,
  onTest: (String, String, Int, String) -> Unit,
  onClearTest: () -> Unit,
  onDismiss: () -> Unit,
) {
  var editingRule by remember { mutableStateOf<RssWebScrapingRule?>(null) }
  var creatingRule by remember { mutableStateOf(false) }

  if (creatingRule || editingRule != null) {
    RssWebScrapingRuleEditorSheet(
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
    return
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Web 取得ルール") },
    text = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Text(
          "URL パターンに一致する Web ページを専用 WebView で開き、登録したスクリプトからフィードを生成します。" +
            "既存の漫画向け専用取得は残し、カスタムルールが一致する場合だけこちらを優先します。",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
          onClick = {
            onClearTest()
            creatingRule = true
          },
          modifier = Modifier.align(Alignment.End),
        ) {
          Text("ルールを追加")
        }
        if (rules.isEmpty()) {
          Text(
            "登録済みの Web 取得ルールはありません。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          LazyColumn(
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
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
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("閉じる") }
    },
  )
}

@Composable
private fun RssWebScrapingRuleEditorSheet(
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
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  LaunchedEffect(rule?.id) { onClearTest() }

  ModalBottomSheet(
    onDismissRequest = { if (!testState.running) onDismiss() },
    sheetState = sheetState,
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .fillMaxHeight(0.94f)
        .imePadding()
        .padding(horizontal = 20.dp),
    ) {
      Text(
        if (rule == null) "Web 取得ルールを追加" else "Web 取得ルールを編集",
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
