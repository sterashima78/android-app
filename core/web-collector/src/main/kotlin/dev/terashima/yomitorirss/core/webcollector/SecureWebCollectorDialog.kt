package dev.terashima.yomitorirss.core.webcollector

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
import androidx.compose.foundation.layout.weight
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

data class WebCollectorConfig(
  val title: String,
  val startUrl: String,
  val profileName: String,
  val allowedBridgeOrigins: Set<String>,
  val allowedNavigationHosts: Set<String>,
  val collectableUrlPrefixes: Set<String>,
  val collectScript: String,
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
  val chunks = remember { ChunkAccumulator() }
  var currentUrl by remember(config) { mutableStateOf(config.startUrl) }
  var loading by remember { mutableStateOf(true) }
  var collecting by remember { mutableStateOf(false) }
  var status by remember { mutableStateOf("ページを開いています") }
  var canGoBack by remember { mutableStateOf(false) }

  val webView = remember(config) {
    WebView(context).also { WebViewCompat.setProfile(it, config.profileName) }.apply {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      settings.allowFileAccess = false
      settings.allowContentAccess = false
      settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      settings.javaScriptCanOpenWindowsAutomatically = false
      settings.setSupportMultipleWindows(false)
      settings.safeBrowsingEnabled = true
      CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

      WebViewCompat.addWebMessageListener(
        this,
        BRIDGE_NAME,
        config.allowedBridgeOrigins,
      ) { _, message, sourceOrigin, isMainFrame, _ ->
        if (!isMainFrame || !collecting) return@addWebMessageListener
        if (sourceOrigin.toString() !in config.allowedBridgeOrigins) return@addWebMessageListener
        runCatching {
          val envelope = JSONObject(message.data ?: return@addWebMessageListener)
          when (envelope.optString("type")) {
            "progress" -> status = envelope.optString("message").ifBlank { status }
            "error" -> {
              collecting = false
              chunks.reset()
              status = envelope.optString("message").ifBlank { "データ取得に失敗しました" }
            }
            "result" -> {
              val payload = envelope.getString("payload")
              requirePayloadSize(payload)
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
          chunks.reset()
          status = error.message ?: "受信データを処理できませんでした"
        }
      }

      webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
          if (!request.isForMainFrame) return false
          val target = request.url.toString()
          if (isAllowedNavigation(target, config.allowedNavigationHosts)) return false
          if (request.url.scheme == "https") {
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
          if (!collecting) {
            status = if (isCollectableUrl(url, config.collectableUrlPrefixes)) {
              "対象ページを確認しました。「取得」を押してください"
            } else {
              "ログイン後に対象ページへ移動してください"
            }
          }
        }
      }

      loadUrl(config.startUrl)
    }
  }

  DisposableEffect(webView) {
    onDispose {
      collecting = false
      chunks.reset()
      webView.stopLoading()
      webView.webViewClient = WebViewClient()
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
        Button(
          modifier = Modifier.fillMaxWidth(),
          enabled = isCollectableUrl(currentUrl, config.collectableUrlPrefixes),
          onClick = {
            chunks.reset()
            collecting = true
            status = "ページからデータを取得しています"
            webView.evaluateJavascript(config.collectScript, null)
          },
        ) { Text("取得") }
      }
      Text(
        "認証情報と Cookie は専用 WebView プロファイル内に保持し、ネイティブ側には collector が明示的に返したデータだけを渡します。",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

private fun isCollectableUrl(url: String, prefixes: Set<String>): Boolean = prefixes.any(url::startsWith)

private fun isAllowedNavigation(url: String, hosts: Set<String>): Boolean {
  val uri = runCatching { URI(url) }.getOrNull() ?: return false
  if (!uri.scheme.equals("https", ignoreCase = true)) return false
  val host = uri.host?.lowercase() ?: return false
  return hosts.any { allowed -> host == allowed || host.endsWith(".$allowed") }
}

private fun requirePayloadSize(payload: String) {
  require(payload.toByteArray(Charsets.UTF_8).size in 1..MAX_RESULT_BYTES) { "取得データが大きすぎます" }
}

private class ChunkAccumulator {
  private var session: String? = null
  private var total = 0
  private var byteLength = 0
  private val parts = mutableMapOf<Int, String>()

  fun start(session: String, total: Int, byteLength: Int) {
    require(total in 1..MAX_CHUNKS)
    require(byteLength in 1..MAX_RESULT_BYTES)
    reset()
    this.session = session
    this.total = total
    this.byteLength = byteLength
  }

  fun add(session: String, index: Int, total: Int, data: String) {
    require(session == this.session)
    require(total == this.total)
    require(index in 0 until total)
    parts[index] = data
  }

  fun finish(session: String): String {
    require(session == this.session)
    require(parts.size == total)
    val payload = buildString { repeat(total) { append(parts.getValue(it)) } }
    require(payload.toByteArray(Charsets.UTF_8).size == byteLength)
    requirePayloadSize(payload)
    reset()
    return payload
  }

  fun reset() {
    session = null
    total = 0
    byteLength = 0
    parts.clear()
  }
}

private const val BRIDGE_NAME = "MosaicCollectorBridge"
private const val MAX_RESULT_BYTES = 5 * 1024 * 1024
private const val MAX_CHUNKS = 1024
