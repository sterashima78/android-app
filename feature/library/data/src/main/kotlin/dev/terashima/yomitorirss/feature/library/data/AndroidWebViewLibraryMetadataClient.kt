package dev.terashima.yomitorirss.feature.library.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface WebLibraryRenderedMetadataClient {
  suspend fun fetch(url: String, titleHint: String? = null): LibraryBook
}

class AndroidWebViewLibraryMetadataClient(
  context: Context,
  private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : WebLibraryRenderedMetadataClient {
  private val applicationContext = context.applicationContext

  @SuppressLint("SetJavaScriptEnabled")
  override suspend fun fetch(url: String, titleHint: String?): LibraryBook {
    val requestedUrl = normalizeWebUrl(url)
    require(isSafeRenderedUrl(requestedUrl)) {
      "WebView での metadata 取得は HTTPS ページのみ対応しています"
    }

    return withTimeout(timeoutMillis) {
      withContext(Dispatchers.Main.immediate) {
        fetchOnMainThread(requestedUrl, titleHint)
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private suspend fun fetchOnMainThread(
    requestedUrl: String,
    titleHint: String?,
  ): LibraryBook = suspendCancellableCoroutine { continuation ->
    val mainHandler = Handler(Looper.getMainLooper())
    val webView = WebView(applicationContext)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
      WebViewCompat.setProfile(webView, PROFILE_NAME)
    }

    webView.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      allowFileAccess = false
      allowContentAccess = false
      mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
      javaScriptCanOpenWindowsAutomatically = false
      setSupportMultipleWindows(false)
      safeBrowsingEnabled = true
      cacheMode = WebSettings.LOAD_NO_CACHE
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

    var completed = false
    var pageGeneration = 0
    var extractionAttempts = 0

    fun dispose() {
      webView.stopLoading()
      webView.webViewClient = WebViewClient()
      webView.clearHistory()
      webView.destroy()
    }

    fun finish(result: Result<LibraryBook>) {
      if (completed) return
      completed = true
      dispose()
      if (!continuation.isActive) return
      result.fold(
        onSuccess = continuation::resume,
        onFailure = continuation::resumeWithException,
      )
    }

    fun extractMetadata(finalUrl: String, generation: Int) {
      if (completed || generation != pageGeneration) return
      extractionAttempts += 1
      webView.evaluateJavascript(METADATA_SCRIPT) { rawResult ->
        if (completed || generation != pageGeneration) return@evaluateJavascript
        runCatching {
          parseRenderedWebLibraryBook(
            requestedUrl = finalUrl,
            rawResult = rawResult,
            titleHint = titleHint,
          )
        }.fold(
          onSuccess = { book ->
            if (!book.needsRenderedWebMetadata() || extractionAttempts >= MAX_EXTRACTION_ATTEMPTS) {
              finish(Result.success(book))
            } else {
              webView.postDelayed(
                { extractMetadata(finalUrl, generation) },
                EXTRACTION_RETRY_DELAY_MILLIS,
              )
            }
          },
          onFailure = { error -> finish(Result.failure(error)) },
        )
      }
    }

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return !isSafeRenderedUrl(request.url.toString())
      }

      override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        pageGeneration += 1
        extractionAttempts = 0
      }

      override fun onPageFinished(view: WebView, url: String) {
        if (completed) return
        val finalUrl = view.url?.takeIf(String::isNotBlank) ?: url
        if (!isSafeRenderedUrl(finalUrl)) {
          finish(Result.failure(IllegalArgumentException("HTTPS 以外へ遷移したため metadata 取得を中止しました")))
          return
        }
        val generation = pageGeneration
        view.postDelayed(
          {
            if (!completed && generation == pageGeneration) {
              extractMetadata(finalUrl, generation)
            }
          },
          INITIAL_DOM_SETTLE_MILLIS,
        )
      }

      override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
      ) {
        if (request.isForMainFrame) {
          finish(Result.failure(IllegalArgumentException("Web ページを表示できませんでした: ${error.description}")))
        }
      }

      override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
      ) {
        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
          finish(
            Result.failure(
              IllegalArgumentException("Web ページを表示できませんでした: HTTP ${errorResponse.statusCode}"),
            ),
          )
        }
      }
    }

    continuation.invokeOnCancellation {
      mainHandler.post {
        if (!completed) {
          completed = true
          dispose()
        }
      }
    }

    webView.loadUrl(requestedUrl)
  }
}

internal fun parseRenderedWebLibraryBook(
  requestedUrl: String,
  rawResult: String?,
  titleHint: String? = null,
): LibraryBook {
  val evaluationResult = requireNotNull(rawResult) { "WebView metadata の応答がありません" }
  val decoded = JSONTokener(evaluationResult).nextValue()
  val metadata = when (decoded) {
    is JSONObject -> decoded
    is String -> JSONObject(decoded)
    else -> throw IllegalArgumentException("WebView metadata の応答形式が不正です")
  }
  val finalUrl = metadata.optionalString("url")
    ?.let(::normalizeWebUrl)
    ?.takeIf(::isSafeRenderedUrl)
    ?: normalizeWebUrl(requestedUrl)
  require(isSafeRenderedUrl(finalUrl)) { "WebView metadata の URL が安全ではありません" }

  val title = metadata.optionalString("title")
    ?: titleHint?.trim()?.takeIf(String::isNotEmpty)
    ?: URI(finalUrl).host.removePrefix("www.")
  val image = metadata.optionalString("image")
    ?.let { resolveRenderedImageUrl(finalUrl, it) }
  val authors = metadata.optionalString("author")
    ?.split(',', '、')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    .orEmpty()

  return LibraryBook(
    source = LibrarySource.WEB,
    sourceId = finalUrl,
    title = title.trim(),
    authors = authors,
    publisher = null,
    publishedDate = null,
    description = metadata.optionalString("description"),
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = image,
    infoUrl = finalUrl,
  )
}

private fun JSONObject.optionalString(key: String): String? =
  optString(key, "").trim().takeIf(String::isNotEmpty)

private fun resolveRenderedImageUrl(baseUrl: String, value: String): String? = runCatching {
  normalizeWebUrl(URI(baseUrl).resolve(value).toString())
}.getOrNull()?.takeIf(::isSafeRenderedUrl)

private fun isSafeRenderedUrl(url: String): Boolean = runCatching {
  val uri = URI(url)
  uri.scheme.equals("https", ignoreCase = true) &&
    !uri.host.isNullOrBlank() &&
    (uri.port == -1 || uri.port == 443)
}.getOrDefault(false)

private const val METADATA_SCRIPT = """
(() => {
  const meta = (...selectors) => {
    for (const selector of selectors) {
      const value = document.querySelector(selector)?.getAttribute('content')?.trim();
      if (value) return value;
    }
    return null;
  };
  return JSON.stringify({
    url: location.href,
    title: meta('meta[property="og:title"]', 'meta[name="twitter:title"]') || document.title?.trim() || null,
    description: meta(
      'meta[property="og:description"]',
      'meta[name="description"]',
      'meta[name="twitter:description"]'
    ),
    image: meta(
      'meta[property="og:image:secure_url"]',
      'meta[property="og:image"]',
      'meta[name="twitter:image"]'
    ),
    author: meta('meta[name="author"]')
  });
})()
"""

private const val PROFILE_NAME = "mosaic-web-library-metadata"
private const val DEFAULT_TIMEOUT_MILLIS = 15_000L
private const val INITIAL_DOM_SETTLE_MILLIS = 500L
private const val EXTRACTION_RETRY_DELAY_MILLIS = 500L
private const val MAX_EXTRACTION_ATTEMPTS = 4
