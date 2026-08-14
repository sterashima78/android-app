package dev.terashima.yomitorirss.feature.library

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URI
import org.json.JSONObject

internal val LocalWebLibraryImportHandler =
  staticCompositionLocalOf<(LibrarySource, String) -> Unit> {
    { _, _ -> error("Web Library import handler is not provided") }
  }

private data class WebLibrarySourceConfig(
  val source: LibrarySource,
  val title: String,
  val startUrl: String,
  val expectedFormat: String,
  val allowedBridgeOrigins: Set<String>,
)

private fun webLibrarySourceConfig(source: LibrarySource): WebLibrarySourceConfig = when (source) {
  LibrarySource.KINDLE -> WebLibrarySourceConfig(
    source = source,
    title = "Kindle Web Library",
    startUrl = KINDLE_WEB_LIBRARY_EXPORT_PAGE,
    expectedFormat = "kindle-library-export",
    allowedBridgeOrigins = setOf("https://read.amazon.co.jp"),
  )

  LibrarySource.AUDIBLE -> WebLibrarySourceConfig(
    source = source,
    title = "Audible Library",
    startUrl = AUDIBLE_WEB_LIBRARY_EXPORT_PAGE,
    expectedFormat = "audible-library-export",
    allowedBridgeOrigins = setOf(
      "https://www.audible.co.jp",
      "https://api.audible.co.jp",
    ),
  )

  else -> error("Web Library import does not support ${source.name}")
}

@Composable
internal fun AmazonWebLibraryImportDialog(
  source: LibrarySource,
  onDismiss: () -> Unit,
  onImportJson: (LibrarySource, String) -> Unit,
) {
  val config = remember(source) { webLibrarySourceConfig(source) }
  val latestOnDismiss by rememberUpdatedState(onDismiss)
  val latestOnImportJson by rememberUpdatedState(onImportJson)
  val supportsRequiredWebViewFeatures = remember {
    WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
      WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
  }

  Dialog(
    onDismissRequest = latestOnDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = false,
      dismissOnClickOutside = false,
    ),
  ) {
    Surface(
      modifier = Modifier.fillMaxSize(),
      color = MaterialTheme.colorScheme.background,
    ) {
      if (!supportsRequiredWebViewFeatures) {
        UnsupportedWebLibraryImport(
          sourceLabel = source.label,
          onDismiss = latestOnDismiss,
        )
      } else {
        WebLibraryImportContent(
          config = config,
          onDismiss = latestOnDismiss,
          onImportJson = latestOnImportJson,
        )
      }
    }
  }
}

@Composable
private fun UnsupportedWebLibraryImport(
  sourceLabel: String,
  onDismiss: () -> Unit,
) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(24.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Text("$sourceLabel のアプリ内インポートを利用できません", style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(12.dp))
    Text(
      "端末の Android System WebView が、専用プロファイルまたは安全な Web メッセージ機能に対応していません。設定画面の外部ブラウザ用ブックマークレットと JSON インポートを利用してください。",
      style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(20.dp))
    Button(onClick = onDismiss) { Text("閉じる") }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebLibraryImportContent(
  config: WebLibrarySourceConfig,
  onDismiss: () -> Unit,
  onImportJson: (LibrarySource, String) -> Unit,
) {
  val context = LocalContext.current
  val chunkAccumulator = remember { ImportChunkAccumulator() }
  var currentUrl by remember(config) { mutableStateOf(config.startUrl) }
  var loading by remember { mutableStateOf(true) }
  var importing by remember { mutableStateOf(false) }
  var progressText by remember(config) { mutableStateOf("${config.title} を開いています") }
  var canGoBack by remember { mutableStateOf(false) }
  var audibleApiCollectionStarted by remember { mutableStateOf(false) }

  val webView = remember(config) {
    WebView(context).also { view ->
      WebViewCompat.setProfile(view, AMAZON_LIBRARY_WEBVIEW_PROFILE)
    }.apply {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      settings.allowFileAccess = false
      settings.allowContentAccess = false
      settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      settings.javaScriptCanOpenWindowsAutomatically = false
      settings.setSupportMultipleWindows(false)
      settings.safeBrowsingEnabled = true
      settings.userAgentString = settings.userAgentString.toBrowserCompatibleImportUserAgent()

      CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

      WebViewCompat.addWebMessageListener(
        this,
        WEB_LIBRARY_BRIDGE_NAME,
        config.allowedBridgeOrigins,
      ) { _, message, sourceOrigin, isMainFrame, _ ->
        if (!isMainFrame || !importing) return@addWebMessageListener
        if (!isAllowedBridgeOrigin(config.source, sourceOrigin.toString())) return@addWebMessageListener

        runCatching {
          val payload = JSONObject(message.data ?: return@addWebMessageListener)
          when (payload.optString("type")) {
            "progress" -> {
              payload.optString("message").takeIf(String::isNotBlank)?.let { progressText = it }
            }

            "error" -> {
              importing = false
              audibleApiCollectionStarted = false
              chunkAccumulator.reset()
              progressText = payload.optString("message").ifBlank { "Web Library の取得に失敗しました" }
            }

            "result-start" -> {
              chunkAccumulator.start(
                sessionId = payload.getString("session"),
                totalChunks = payload.getInt("total"),
                declaredByteLength = payload.getInt("byteLength"),
              )
            }

            "result-chunk" -> {
              chunkAccumulator.add(
                sessionId = payload.getString("session"),
                index = payload.getInt("index"),
                totalChunks = payload.getInt("total"),
                data = payload.getString("data"),
              )
            }

            "result-end" -> {
              val json = chunkAccumulator.finish(payload.getString("session"))
              validateCollectedLibraryJson(config, json)
              importing = false
              progressText = "${config.source.label} のデータを取得しました。インポートしています"
              onImportJson(config.source, json)
              onDismiss()
            }
          }
        }.onFailure { error ->
          importing = false
          audibleApiCollectionStarted = false
          chunkAccumulator.reset()
          progressText = error.message ?: "Web Library の受信データを処理できませんでした"
        }
      }

      webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
          if (!request.isForMainFrame) return false
          val target = request.url.toString()
          if (isTrustedAmazonImportNavigation(target)) return false

          runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
          }
          return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
          super.onPageStarted(view, url, favicon)
          currentUrl = url
          loading = true
        }

        override fun onPageFinished(view: WebView, url: String) {
          super.onPageFinished(view, url)
          currentUrl = url
          loading = false
          canGoBack = view.canGoBack()

          if (
            config.source == LibrarySource.AUDIBLE &&
            importing &&
            isAudibleCatalogApiPage(url) &&
            !audibleApiCollectionStarted
          ) {
            audibleApiCollectionStarted = true
            progressText = "Audible のカタログ情報を取得しています"
            view.evaluateJavascript(AUDIBLE_WEBVIEW_EXPORT_SCRIPT, null)
          } else if (!importing) {
            progressText = when {
              isStartPageForSource(config.source, url) -> "ログイン済みなら「蔵書を取り込む」を押してください"
              else -> "ログイン後に蔵書ページへ移動してください"
            }
          }
        }
      }

      loadUrl(config.startUrl)
    }
  }

  DisposableEffect(webView) {
    onDispose {
      importing = false
      chunkAccumulator.reset()
      webView.stopLoading()
      webView.webViewClient = WebViewClient()
      webView.destroy()
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      TextButton(
        enabled = canGoBack && !importing,
        onClick = { webView.goBack() },
      ) {
        Text("戻る")
      }
      Text(
        config.title,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.titleMedium,
      )
      TextButton(onClick = onDismiss) { Text(if (importing) "中止" else "閉じる") }
    }

    if (loading) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }

    AndroidView(
      factory = { webView },
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f),
    )

    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (importing) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Text(
          progressText,
          modifier = Modifier.weight(1f),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (!importing) {
        if (isStartPageForSource(config.source, currentUrl)) {
          Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
              chunkAccumulator.reset()
              audibleApiCollectionStarted = false
              importing = true
              progressText = "蔵書情報を取得しています"
              val script = when (config.source) {
                LibrarySource.KINDLE -> KINDLE_WEBVIEW_COLLECT_SCRIPT
                LibrarySource.AUDIBLE -> AUDIBLE_WEBVIEW_COLLECT_SCRIPT
                else -> error("Unsupported source")
              }
              webView.evaluateJavascript(script, null)
            },
          ) {
            Text("蔵書を取り込む")
          }
        } else {
          Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { webView.loadUrl(config.startUrl) },
          ) {
            Text("蔵書ページへ")
          }
        }
      }

      Text(
        "認証情報と Cookie は専用 WebView プロファイル内だけに保持され、アプリのインポート処理には JSON の蔵書データだけを渡します。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun validateCollectedLibraryJson(config: WebLibrarySourceConfig, json: String) {
  val byteLength = json.toByteArray(Charsets.UTF_8).size
  require(byteLength in 1..MAX_WEB_LIBRARY_EXPORT_BYTES) { "取得データが大きすぎます" }
  val root = JSONObject(json)
  require(root.optString("format") == config.expectedFormat) { "取得データの形式が一致しません" }
  require(root.optInt("version") == 1) { "未対応の Web Library データ形式です" }
}

private fun isStartPageForSource(source: LibrarySource, url: String): Boolean = when (source) {
  LibrarySource.KINDLE -> isKindleWebLibraryPage(url)
  LibrarySource.AUDIBLE -> isAudibleLibraryPage(url)
  else -> false
}

private fun isAllowedBridgeOrigin(source: LibrarySource, origin: String): Boolean {
  val uri = runCatching { URI(origin) }.getOrNull() ?: return false
  if (!uri.scheme.equals("https", ignoreCase = true)) return false
  if (uri.port != -1 && uri.port != 443) return false
  val host = uri.host?.lowercase() ?: return false
  return when (source) {
    LibrarySource.KINDLE -> host == "read.amazon.co.jp"
    LibrarySource.AUDIBLE -> host == "www.audible.co.jp" || host == "api.audible.co.jp"
    else -> false
  }
}

private fun String.toBrowserCompatibleImportUserAgent(): String =
  replace(Regex(";\\s*wv(?=\\))"), "")
    .replace(Regex("\\bVersion/4\\.0\\s+"), "")

private const val AMAZON_LIBRARY_WEBVIEW_PROFILE = "yomitori-amazon-library"
private const val WEB_LIBRARY_BRIDGE_NAME = "YomitoriLibraryBridge"
