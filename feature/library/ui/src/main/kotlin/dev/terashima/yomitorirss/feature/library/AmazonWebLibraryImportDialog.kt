package dev.terashima.yomitorirss.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import dev.terashima.yomitorirss.core.webcollector.SecureWebCollectorDialog
import dev.terashima.yomitorirss.core.webcollector.WebCollectorConfig
import dev.terashima.yomitorirss.core.webcollector.WebCollectorContinuation
import org.json.JSONObject

internal val LocalWebLibraryImportHandler =
  staticCompositionLocalOf<(LibrarySource, String) -> Unit> {
    { _, _ -> error("Web Library import handler is not provided") }
  }

private data class WebLibrarySourceConfig(
  val source: LibrarySource,
  val expectedFormat: String,
  val collector: WebCollectorConfig,
)

private fun webLibrarySourceConfig(
  source: LibrarySource,
  kindlePersonalDocuments: Boolean,
): WebLibrarySourceConfig = when {
  source == LibrarySource.KINDLE && kindlePersonalDocuments -> WebLibrarySourceConfig(
    source = source,
    expectedFormat = "kindle-personal-library-export",
    collector = amazonCollectorConfig(
      title = "Kindle Personal Document",
      startUrl = KINDLE_PERSONAL_DOCUMENT_EXPORT_PAGE,
      bridgeOrigins = setOf("https://www.amazon.co.jp"),
      collectableUrlPrefixes = setOf(KINDLE_PERSONAL_DOCUMENT_URL_PREFIX),
      collectScript = KINDLE_PERSONAL_DOCUMENT_WEBVIEW_COLLECT_SCRIPT,
      collectButtonLabel = "Personal Document を取り込む",
    ),
  )

  source == LibrarySource.KINDLE -> WebLibrarySourceConfig(
    source = source,
    expectedFormat = "kindle-library-export",
    collector = amazonCollectorConfig(
      title = "Kindle Web Library",
      startUrl = KINDLE_WEB_LIBRARY_EXPORT_PAGE,
      bridgeOrigins = setOf("https://read.amazon.co.jp"),
      collectableUrlPrefixes = setOf(KINDLE_WEB_LIBRARY_URL_PREFIX),
      collectScript = KINDLE_WEBVIEW_COLLECT_SCRIPT,
      collectButtonLabel = "蔵書を取り込む",
    ),
  )

  source == LibrarySource.AUDIBLE -> WebLibrarySourceConfig(
    source = source,
    expectedFormat = "audible-library-export",
    collector = amazonCollectorConfig(
      title = "Audible Library",
      startUrl = AUDIBLE_WEB_LIBRARY_EXPORT_PAGE,
      bridgeOrigins = setOf(
        "https://www.audible.co.jp",
        "https://api.audible.co.jp",
      ),
      collectableUrlPrefixes = setOf(AUDIBLE_WEB_LIBRARY_URL_PREFIX),
      collectScript = AUDIBLE_WEBVIEW_COLLECT_SCRIPT,
      collectButtonLabel = "蔵書を取り込む",
      continuations = listOf(
        WebCollectorContinuation(
          urlPrefixes = setOf(AUDIBLE_CATALOG_URL_PREFIX),
          script = AUDIBLE_WEBVIEW_EXPORT_SCRIPT,
          statusMessage = "Audible のカタログ情報を取得しています",
        ),
      ),
    ),
  )

  else -> error("Web Library import does not support ${source.name}")
}

private fun amazonCollectorConfig(
  title: String,
  startUrl: String,
  bridgeOrigins: Set<String>,
  collectableUrlPrefixes: Set<String>,
  collectScript: String,
  collectButtonLabel: String,
  continuations: List<WebCollectorContinuation> = emptyList(),
): WebCollectorConfig = WebCollectorConfig(
  title = title,
  startUrl = startUrl,
  profileName = AMAZON_LIBRARY_WEBVIEW_PROFILE,
  allowedBridgeOrigins = bridgeOrigins,
  allowedNavigationHosts = AMAZON_IMPORT_NAVIGATION_HOSTS,
  collectableUrlPrefixes = collectableUrlPrefixes,
  collectScript = collectScript,
  bridgeName = WEB_LIBRARY_BRIDGE_NAME,
  collectButtonLabel = collectButtonLabel,
  acceptThirdPartyCookies = true,
  externalNavigationSchemes = setOf("http", "https"),
  userAgentTransformer = { it.toBrowserCompatibleImportUserAgent() },
  continuations = continuations,
  maxResultBytes = MAX_WEB_LIBRARY_EXPORT_BYTES,
  maxChunks = MAX_WEB_LIBRARY_CHUNKS,
)

@Composable
internal fun AmazonWebLibraryImportDialog(
  source: LibrarySource,
  onDismiss: () -> Unit,
  onImportJson: (LibrarySource, String) -> Unit,
  kindlePersonalDocuments: Boolean = false,
) {
  val config = remember(source, kindlePersonalDocuments) {
    webLibrarySourceConfig(source, kindlePersonalDocuments)
  }

  SecureWebCollectorDialog(
    config = config.collector,
    onDismiss = onDismiss,
    onResult = { json ->
      validateCollectedLibraryJson(config, json)
      onImportJson(config.source, json)
    },
  )
}

private fun validateCollectedLibraryJson(config: WebLibrarySourceConfig, json: String) {
  val root = JSONObject(json)
  require(root.optString("format") == config.expectedFormat) { "取得データの形式が一致しません" }
  require(root.optInt("version") == 1) { "未対応の Web Library データ形式です" }
}

private fun String.toBrowserCompatibleImportUserAgent(): String =
  replace(Regex(";\\s*wv(?=\\))"), "")
    .replace(Regex("\\bVersion/4\\.0\\s+"), "")

private val AMAZON_IMPORT_NAVIGATION_HOSTS = setOf("amazon.co.jp", "audible.co.jp")
private const val AMAZON_LIBRARY_WEBVIEW_PROFILE = "yomitori-amazon-library"
private const val WEB_LIBRARY_BRIDGE_NAME = "YomitoriLibraryBridge"
private const val KINDLE_WEB_LIBRARY_EXPORT_PAGE = "https://read.amazon.co.jp/kindle-library"
private const val KINDLE_WEB_LIBRARY_URL_PREFIX = "https://read.amazon.co.jp/kindle-library"
private const val KINDLE_PERSONAL_DOCUMENT_EXPORT_PAGE =
  "https://www.amazon.co.jp/hz/mycd/digital-console/contentlist/pdocs/dateDsc/"
private const val KINDLE_PERSONAL_DOCUMENT_URL_PREFIX =
  "https://www.amazon.co.jp/hz/mycd/digital-console/contentlist/pdocs"
private const val AUDIBLE_WEB_LIBRARY_EXPORT_PAGE = "https://www.audible.co.jp/library/titles"
private const val AUDIBLE_WEB_LIBRARY_URL_PREFIX = "https://www.audible.co.jp/library/titles"
private const val AUDIBLE_CATALOG_URL_PREFIX = "https://api.audible.co.jp/1.0/catalog/products"
private const val MAX_WEB_LIBRARY_EXPORT_BYTES = 25 * 1024 * 1024
private const val MAX_WEB_LIBRARY_CHUNKS = 2048
