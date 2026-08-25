package dev.terashima.yomitorirss.core.webcollector

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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

data class WebCollectorContinuation(
  val urlPrefixes: Set<String>,
  val script: String,
  val statusMessage: String? = null,
)

data class WebCollectorConfig(
  val title: String,
  val startUrl: String,
  val profileName: String,
  val allowedBridgeOrigins: Set<String>,
  val allowedNavigationHosts: Set<String>,
  val collectableUrlPrefixes: Set<String>,
  val collectScript: String,
  val bridgeName: String = DEFAULT_BRIDGE_NAME,
  val collectButtonLabel: String = "取得",
  val acceptThirdPartyCookies: Boolean = false,
  val externalNavigationSchemes: Set<String> = setOf("https"),
  val userAgentTransformer: (String) -> String = { it },
  val continuations: List<WebCollectorContinuation> = emptyList(),
  val maxResultBytes: Int = DEFAULT_MAX_RESULT_BYTES,
  val maxChunks: Int = DEFAULT_MAX_CHUNKS,
)

@Composable
fun SecureWebCollectorDialog(
  config: WebCollectorConfig,
  onDismiss: () -> Unit,
  onResult: (String) -> Unit,
) {
  val latestDismiss by rememberUpdatedState(onDismiss)
  val latestResult by rememberUpdatedState(onResult)
  val supported = remember {
    WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
      WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
  }

  Dialog(
    onDismissRequest = latestDismiss,
    properties = DialogProperties(
      usePlatformDefaultWidth = false,
      dismissOnBackPress = false,
      dismissOnClickOutside = false,
    ),
  ) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      if (supported) {
        CollectorContent(config, latestDismiss, latestResult)
      } else {
        Column(
          modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
          verticalArrangement = Arrangement.Center,
        ) {
          Text("安全な Web データ取得を利用できません", style = MaterialTheme.typography.titleLarge)
          Spacer(Modifier.height(12.dp))
          Text("Android System WebView を更新してから再度お試しください。")
          Spacer(Modifier.height(20.dp))
          Button(onClick = latestDismiss) { Text("閉じる") }
        }
      }
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CollectorContent(
  config: WebCollectorConfig,
  onDismiss: () -> Unit,
  onResult: (String) -> Unit,
) {
  val context = LocalContext.current
  val chunks = remember(config.maxResultBytes, config.maxChunks) {
    WebCollectorChunkAccumulator(
      maxBytes = config.maxResultBytes,
      maxChunks = config.maxChunks,
    )
  }
  var currentUrl by remember(config) { mutableStateOf(config.startUrl) }
  var loading by remember { mutableStateOf(true) }
  var collecting by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf("ページを開いています") }
  var canGoBack by remember { mutableStateOf(false) }
  var continuationKey by remember(config) { mutableStateOf<String?>(null) }
  var rendererGeneration by remember(config) { mutableIntStateOf(0) }
  var rendererCrashed by remember(config) { mutableStateOf(false) }

  if (rendererCrashed) {
    Column(
      modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text("Web 表示処理が異常終了しました", style = MaterialTheme.typography.titleLarge)
      Spacer(Modifier.height(12.dp))
      Text("同じページを自動で開き直さず、開始ページから再試行します。")
      Spacer(Modifier.height(20.dp))
      Button(
        onClick = {
          currentUrl = config.startUrl
          loading = true
          collecting = false
          canGoBack = false
          continuationKey = null
          chunks.reset()
          status = "ページを開いています"
          rendererCrashed = false
        },
      ) {
        Text("再読み込み")
      }
      TextButton(onClick = onDismiss) { Text("閉じる") }
    }
    return
  }

  val rendererLifecycle = remember(config, rendererGeneration) {
    WebCollectorRendererLifecycle()
  }
  val initialUrl = currentUrl.takeIf {
    isAllowedNavigation(it, config.allowedNavigationHosts)
  } ?: config.startUrl
  val webView = remember(config, rendererGeneration, rendererLifecycle) {
    WebView(context).also { WebViewCompat.setProfile(it, config.profileName) }.apply {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      settings.allowFileAccess = false
      settings.allowContentAccess = false
      settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      settings.javaScriptCanOpenWindowsAutomatically = false
      settings.setSupportMultipleWindows(false)
      settings.safeBrowsingEnabled = true
      settings.userAgentString = config.userAgentTransformer(settings.userAgentString)
      CookieManager.getInstance().setAcceptThirdPartyCookies(this, config.acceptThirdPartyCookies)

      WebViewCompat.addWebMessageListener(
        this,
        config.bridgeName,
        config.allowedBridgeOrigins,
      ) { _, message, sourceOrigin, isMainFrame, _ ->
        if (!isMainFrame || !collecting) return@addWebMessageListener
        if (!isAllowedBridgeOrigin(sourceOrigin.toString(), config.allowedBridgeOrigins)) {
          return@addWebMessageListener
        }
        runCatching {
          val envelope = JSONObject(message.data ?: return@addWebMessageListener)
          when (envelope.optString("type")) {
            "progress" -> status = envelope.optString("message").ifBlank { status }
            "error" -> {
              collecting = false
              continuationKey = null
              chunks.reset()
              status = envelope.optString("message").ifBlank { "データ取得に失敗しました" }
            }
            "result" -> {
              val payload = envelope.getString("payload")
              requirePayloadSize(payload, config.maxResultBytes)
              collecting = false
              onResult(payload)
              onDismiss()
            }
            "result-start" -> chunks.start(
              session = envelope.getString("session"),
              total = envelope.getInt("total"),
              byteLength = envelope.getInt("byteLength"),
            )
            "result-chunk" -> chunks.add(
              session = envelope.getString("session"),
              index = envelope.getInt("index"),
              total = envelope.getInt("total"),
              data = envelope.getString("data"),
            )
            "result-end" -> {
              val payload = chunks.finish(envelope.getString("session"))
              collecting = false
              onResult(payload)
              onDismiss()
            }
          }
        }.onFailure { error ->
          collecting = false
          continuationKey = null
          chunks.reset()
          status = error.message ?: "受信データを処理できませんでした"
        }
      }

      webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
          if (!request.isForMainFrame) return false
          val target = request.url.toString()
          if (isAllowedNavigation(target, config.allowedNavigationHosts)) return false
          if (config.externalNavigationSchemes.any { it.equals(request.url.scheme, ignoreCase = true) }) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
          }
          return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
          currentUrl = url
          loading = true
        }

        override fun onPageFinished(view: WebView, url: String) {
          currentUrl = url
          loading = false
          canGoBack = view.canGoBack()

          if (collecting) {
            val continuationIndex = config.continuations.indexOfFirst { continuation ->
              isCollectableUrl(url, continuation.urlPrefixes)
            }
            if (continuationIndex >= 0) {
              val key = "$continuationIndex:$url"
              if (continuationKey != key) {
                continuationKey = key
                val continuation = config.continuations[continuationIndex]
                continuation.statusMessage?.let { status = it }
                view.evaluateJavascript(continuation.script, null)
              }
            }
          } else {
            status = if (isCollectableUrl(url, config.collectableUrlPrefixes)) {
              "対象ページを確認しました。「${config.collectButtonLabel}」を押してください"
            } else {
              "ログイン後に対象ページへ移動してください"
            }
          }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
          rendererLifecycle.gone = true
          collecting = false
          continuationKey = null
          chunks.reset()
          canGoBack = false
          if (detail.didCrash()) {
            loading = false
            status = "Web 表示処理が異常終了しました"
            rendererCrashed = true
          } else {
            loading = true
            status = "Web 表示プロセスがメモリ不足で終了したため再読み込みしています"
            rendererGeneration += 1
          }
          return true
        }
      }

      loadUrl(initialUrl)
    }
  }

  DisposableEffect(webView) {
    onDispose {
      collecting = false
      continuationKey = null
      chunks.reset()
      if (!rendererLifecycle.gone) {
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
      }
      webView.destroy()
    }
  }

  Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      TextButton(enabled = canGoBack && !collecting, onClick = webView::goBack) { Text("戻る") }
      Text(config.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
      TextButton(onClick = onDismiss) { Text(if (collecting) "中止" else "閉じる") }
    }
    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().weight(1f))
    Column(
      modifier = Modifier.fillMaxWidth().padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (collecting) CircularProgressIndicator(strokeWidth = 2.dp)
        Text(status, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
      }
      if (!collecting) {
        if (isCollectableUrl(currentUrl, config.collectableUrlPrefixes)) {
          Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
              chunks.reset()
              continuationKey = null
              collecting = true
              status = "ページからデータを取得しています"
              webView.evaluateJavascript(config.collectScript, null)
            },
          ) { Text(config.collectButtonLabel) }
        } else {
          Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { webView.loadUrl(config.startUrl) },
          ) { Text("対象ページへ") }
        }
      }
      Text(
        "認証情報と Cookie は専用 WebView プロファイル内に保持し、ネイティブ側には collector が明示的に返したデータだけを渡します。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private class WebCollectorRendererLifecycle {
  var gone: Boolean = false
}

internal fun isCollectableUrl(url: String, prefixes: Set<String>): Boolean = prefixes.any(url::startsWith)

internal fun isAllowedNavigation(url: String, hosts: Set<String>): Boolean {
  val uri = runCatching { URI(url) }.getOrNull() ?: return false
  if (!uri.scheme.equals("https", ignoreCase = true)) return false
  if (uri.port != -1 && uri.port != 443) return false
  val host = uri.host?.lowercase() ?: return false
  return hosts.any { allowed ->
    val normalized = allowed.lowercase()
    host == normalized || host.endsWith(".$normalized")
  }
}

internal fun isAllowedBridgeOrigin(origin: String, allowedOrigins: Set<String>): Boolean {
  val uri = runCatching { URI(origin) }.getOrNull() ?: return false
  if (!uri.scheme.equals("https", ignoreCase = true)) return false
  if (uri.port != -1 && uri.port != 443) return false
  val host = uri.host?.lowercase() ?: return false
  val normalized = "https://$host"
  return normalized in allowedOrigins.map { it.removeSuffix("/").lowercase() }.toSet()
}

private fun requirePayloadSize(payload: String, maxBytes: Int) {
  require(payload.toByteArray(Charsets.UTF_8).size in 1..maxBytes) { "取得データが大きすぎます" }
}

internal class WebCollectorChunkAccumulator(
  private val maxBytes: Int,
  private val maxChunks: Int,
) {
  private var session: String? = null
  private var total = 0
  private var byteLength = 0
  private val parts = mutableMapOf<Int, String>()
  private var receivedBytes = 0

  fun start(session: String, total: Int, byteLength: Int) {
    require(session.isNotBlank()) { "Web collector セッションが不正です" }
    require(total in 1..maxChunks) { "Web collector の分割数が不正です" }
    require(byteLength in 1..maxBytes) { "取得データが大きすぎます" }
    reset()
    this.session = session
    this.total = total
    this.byteLength = byteLength
  }

  fun add(session: String, index: Int, total: Int, data: String) {
    require(session == this.session) { "Web collector セッションが一致しません" }
    require(total == this.total) { "Web collector の分割数が一致しません" }
    require(index in 0 until total) { "Web collector の分割位置が不正です" }
    val existing = parts[index]
    if (existing != null) {
      require(existing == data) { "Web collector の分割データが競合しました" }
      return
    }
    val chunkBytes = data.toByteArray(Charsets.UTF_8).size
    require(receivedBytes + chunkBytes <= maxBytes) { "取得データが大きすぎます" }
    parts[index] = data
    receivedBytes += chunkBytes
  }

  fun finish(session: String): String {
    require(session == this.session) { "Web collector セッションが一致しません" }
    require(parts.size == total) { "Web collector の取得データが不足しています" }
    val payload = buildString { repeat(total) { append(parts.getValue(it)) } }
    require(payload.toByteArray(Charsets.UTF_8).size == byteLength) { "Web collector の取得データが破損しています" }
    reset()
    return payload
  }

  fun reset() {
    session = null
    total = 0
    byteLength = 0
    parts.clear()
    receivedBytes = 0
  }
}

private const val DEFAULT_BRIDGE_NAME = "MosaicCollectorBridge"
private const val DEFAULT_MAX_RESULT_BYTES = 5 * 1024 * 1024
private const val DEFAULT_MAX_CHUNKS = 1024
