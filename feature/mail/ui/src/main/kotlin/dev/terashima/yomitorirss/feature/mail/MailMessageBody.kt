package dev.terashima.yomitorirss.feature.mail

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.webkit.RenderProcessGoneDetail
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private val MIN_HTML_BODY_HEIGHT = 80.dp
private val MAX_HTML_BODY_HEIGHT = 1_200.dp
private const val MAIL_HTML_BASE_URL = "https://mail.invalid/"
private const val MAIL_HTML_BASE_HOST = "mail.invalid"
private val HEAD_OPEN_TAG = Regex("<head\\b[^>]*>", RegexOption.IGNORE_CASE)
private val HEAD_CLOSE_TAG = Regex("</head\\s*>", RegexOption.IGNORE_CASE)
private val HTML_OPEN_TAG = Regex("<html\\b[^>]*>", RegexOption.IGNORE_CASE)
private val VIEWPORT_META_TAG = Regex(
  "<meta\\b[^>]*name\\s*=\\s*['\"]?viewport['\"]?[^>]*>",
  RegexOption.IGNORE_CASE,
)

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
  var rendererGeneration by remember(document) { mutableIntStateOf(0) }
  var rendererCrashed by remember(document) { mutableStateOf(false) }

  if (rendererCrashed) {
    PlainMailBody(
      text = "HTML メールの表示処理が異常終了しました。画面を開き直すと再試行できます。",
      modifier = modifier,
    )
    return
  }

  val contentHeight = with(density) {
    if (contentHeightPx <= 0) {
      MIN_HTML_BODY_HEIGHT
    } else {
      contentHeightPx.toDp().coerceIn(MIN_HTML_BODY_HEIGHT, MAX_HTML_BODY_HEIGHT)
    }
  }
  val rendererLifecycle = remember(context, document, rendererGeneration) {
    MailWebViewRendererLifecycle()
  }
  val webView = remember(context, document, rendererGeneration, rendererLifecycle) {
    createMailWebView(
      context = context,
      rendererLifecycle = rendererLifecycle,
      onContentHeightChanged = { height ->
        if (height != contentHeightPx) contentHeightPx = height
      },
      onRenderProcessGone = { didCrash ->
        contentHeightPx = 0
        if (didCrash) {
          rendererCrashed = true
        } else {
          rendererGeneration += 1
        }
      },
    ).also { view ->
      view.setMailDocument(document)
    }
  }

  DisposableEffect(webView) {
    onDispose {
      if (!rendererLifecycle.gone) {
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
      }
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

private class MailWebViewRendererLifecycle {
  var gone: Boolean = false
}

private class MailWebView(
  context: Context,
  private val onContentHeightChanged: (Int) -> Unit,
) : WebView(context) {
  private var pendingDocument: String? = null

  fun setMailDocument(document: String) {
    pendingDocument = document
    loadPendingDocumentIfAttached()
  }

  fun reportContentHeight() {
    post {
      if (!isAttachedToWindow || width <= 0) return@post
      val heightPx = computeVerticalScrollRange()
      if (heightPx > 0) onContentHeightChanged(heightPx)
    }
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    loadPendingDocumentIfAttached()
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    if (w > 0 && w != oldw) reportContentHeight()
  }

  private fun loadPendingDocumentIfAttached() {
    if (!isAttachedToWindow) return
    val document = pendingDocument ?: return
    pendingDocument = null
    loadDataWithBaseURL(
      MAIL_HTML_BASE_URL,
      document,
      "text/html",
      null,
      MAIL_HTML_BASE_URL,
    )
  }
}

private fun createMailWebView(
  context: Context,
  rendererLifecycle: MailWebViewRendererLifecycle,
  onContentHeightChanged: (Int) -> Unit,
  onRenderProcessGone: (Boolean) -> Unit,
): MailWebView = MailWebView(context, onContentHeightChanged).apply {
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

    override fun onPageCommitVisible(view: WebView, url: String) {
      super.onPageCommitVisible(view, url)
      (view as? MailWebView)?.reportContentHeight()
    }

    override fun onPageFinished(view: WebView, url: String) {
      super.onPageFinished(view, url)
      (view as? MailWebView)?.reportContentHeight()
    }

    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
      super.onScaleChanged(view, oldScale, newScale)
      (view as? MailWebView)?.reportContentHeight()
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
      rendererLifecycle.gone = true
      onRenderProcessGone(detail.didCrash())
      return true
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

internal fun htmlDocument(
  html: String,
  textColor: Color,
  linkColor: Color,
): String {
  val headContent = mailHtmlHeadContent(
    includeViewport = !VIEWPORT_META_TAG.containsMatchIn(html),
    textColor = textColor,
    linkColor = linkColor,
  )

  HEAD_CLOSE_TAG.find(html)?.let { closingHead ->
    return html.replaceRange(
      closingHead.range.first,
      closingHead.range.first,
      headContent,
    )
  }

  HEAD_OPEN_TAG.find(html)?.let { openingHead ->
    val insertionPoint = openingHead.range.last + 1
    return html.replaceRange(insertionPoint, insertionPoint, headContent)
  }

  HTML_OPEN_TAG.find(html)?.let { openingHtml ->
    val insertionPoint = openingHtml.range.last + 1
    return html.replaceRange(
      insertionPoint,
      insertionPoint,
      "<head>$headContent</head>",
    )
  }

  return """
    <!doctype html>
    <html>
      <head>$headContent</head>
      <body>$html</body>
    </html>
  """.trimIndent()
}

private fun mailHtmlHeadContent(
  includeViewport: Boolean,
  textColor: Color,
  linkColor: Color,
): String = buildString {
  if (includeViewport) {
    append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
  }
  append(
    """
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
        img, video, svg { max-width: 100% !important; height: auto !important; }
        table { max-width: 100% !important; }
        pre { white-space: pre-wrap !important; overflow-wrap: anywhere !important; }
        a { color: ${linkColor.toCssHex()}; }
      </style>
    """.trimIndent(),
  )
}

private fun Color.toCssHex(): String = String.format("#%06X", toArgb() and 0x00FFFFFF)
