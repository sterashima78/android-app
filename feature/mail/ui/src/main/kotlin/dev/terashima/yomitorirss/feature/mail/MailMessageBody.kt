package dev.terashima.yomitorirss.feature.mail

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

private val MIN_HTML_BODY_HEIGHT = 80.dp
private val MAX_HTML_BODY_HEIGHT = 1_200.dp
private const val MAIL_HTML_BASE_URL = "https://mail.invalid/"
private const val MAIL_HTML_BASE_HOST = "mail.invalid"

@Composable
internal fun MailMessageBody(
  message: MailMessage,
  modifier: Modifier = Modifier,
) {
  val html = message.htmlBody?.takeIf(String::isNotBlank)
  if (html != null) {
    HtmlMailBody(
      html = html,
      modifier = modifier,
    )
  } else {
    PlainMailBody(
      text = message.body.ifBlank { message.snippet },
      modifier = modifier,
    )
  }
}

@Composable
private fun PlainMailBody(
  text: String,
  modifier: Modifier,
) {
  val textColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.toArgb()
  val linkColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.toArgb()

  AndroidView(
    factory = { context ->
      TextView(context).apply {
        setTextIsSelectable(true)
        linksClickable = true
        movementMethod = LinkMovementMethod.getInstance()
        textSize = 16f
      }
    },
    modifier = modifier.fillMaxWidth(),
    update = { view ->
      view.text = text
      view.setTextColor(textColor)
      view.setLinkTextColor(linkColor)
      Linkify.addLinks(view, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
    },
  )
}

@Composable
private fun HtmlMailBody(
  html: String,
  modifier: Modifier,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val textColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
  val linkColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
  val document = remember(html, textColor, linkColor) {
    htmlDocument(html, textColor, linkColor)
  }
  var contentHeightPx by remember(document) { mutableIntStateOf(0) }
  val contentHeight = with(density) {
    if (contentHeightPx <= 0) {
      MIN_HTML_BODY_HEIGHT
    } else {
      contentHeightPx.toDp().coerceIn(MIN_HTML_BODY_HEIGHT, MAX_HTML_BODY_HEIGHT)
    }
  }
  val webView = remember(context, document) {
    createMailWebView(
      context = context,
      onContentHeightChanged = { height -> contentHeightPx = height },
    ).also { view ->
      view.loadDataWithBaseURL(
        MAIL_HTML_BASE_URL,
        document,
        "text/html",
        Charsets.UTF_8.name(),
        null,
      )
    }
  }

  DisposableEffect(webView) {
    onDispose {
      webView.stopLoading()
      webView.webViewClient = WebViewClient()
      webView.destroy()
    }
  }

  AndroidView(
    factory = { webView },
    modifier = modifier
      .fillMaxWidth()
      .height(contentHeight),
  )
}

private fun createMailWebView(
  context: Context,
  onContentHeightChanged: (Int) -> Unit,
): WebView = WebView(context).apply {
  settings.javaScriptEnabled = false
  settings.domStorageEnabled = false
  settings.allowFileAccess = false
  settings.allowContentAccess = false
  settings.blockNetworkLoads = true
  settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
  settings.cacheMode = WebSettings.LOAD_NO_CACHE
  settings.setSupportMultipleWindows(false)
  settings.builtInZoomControls = true
  settings.displayZoomControls = false
  setBackgroundColor(AndroidColor.TRANSPARENT)

  webViewClient = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
      val target = request.url
      val usesSyntheticOrigin = target.host.equals(MAIL_HTML_BASE_HOST, ignoreCase = true)
      if (usesSyntheticOrigin) {
        return target.fragment == null
      }
      if (request.isForMainFrame && request.hasGesture()) {
        context.openExternalUri(target)
      }
      return true
    }

    override fun onPageFinished(view: WebView, url: String) {
      super.onPageFinished(view, url)
      view.post {
        val height = (view.contentHeight * view.resources.displayMetrics.density).roundToInt()
        if (height > 0) onContentHeightChanged(height)
      }
    }
  }
}

private fun Context.openExternalUri(uri: Uri) {
  val scheme = uri.scheme?.lowercase() ?: return
  if (scheme !in setOf("http", "https", "mailto", "tel")) return
  runCatching {
    startActivity(
      Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
      },
    )
  }
}

private fun htmlDocument(
  html: String,
  textColor: Color,
  linkColor: Color,
): String = """
  <!doctype html>
  <html>
    <head>
      <meta name="viewport" content="width=device-width, initial-scale=1.0">
      <style>
        html, body {
          margin: 0;
          padding: 0;
          background: transparent;
          color: ${textColor.toCssHex()};
          font-family: sans-serif;
          font-size: 16px;
          line-height: 1.45;
          overflow-wrap: anywhere;
        }
        img, video, svg { max-width: 100%; height: auto; }
        table { max-width: 100%; }
        pre { white-space: pre-wrap; overflow-wrap: anywhere; }
        a { color: ${linkColor.toCssHex()}; }
      </style>
    </head>
    <body>$html</body>
  </html>
""".trimIndent()

private fun Color.toCssHex(): String = String.format("#%06X", toArgb() and 0x00FFFFFF)
