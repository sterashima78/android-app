package dev.terashima.yomitorirss.feature.library.data

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
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
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractor
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorRepository
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

  fun hasCustomExtractor(url: String): Boolean = false
}

class AndroidWebViewLibraryMetadataClient(
  private val activityProvider: () -> Activity?,
  private val extractorRepository: WebLibraryMetadataExtractorRepository? = null,
  private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : WebLibraryRenderedMetadataClient {
  override fun hasCustomExtractor(url: String): Boolean =
    findMatchingWebLibraryMetadataExtractor(extractorRepository?.list().orEmpty(), url) != null

  override suspend fun fetch(url: String, titleHint: String?): LibraryBook {
    val requestedUrl = normalizeWebUrl(url)
    require(isSafeRenderedUrl(requestedUrl)) {
      "WebView での metadata 取得は HTTPS ページのみ対応しています"
    }
    val extractors = extractorRepository?.list().orEmpty()

    return withTimeout(timeoutMillis) {
      withContext(Dispatchers.Main.immediate) {
        require(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
          "安全な WebView metadata 取得を利用できません。Android System WebView を更新してください"
        }
        val activity = requireNotNull(activityProvider()) {
          "WebView metadata を取得できる画面がありません"
        }
        require(!activity.isFinishing && !activity.isDestroyed) {
          "WebView metadata を取得できる画面がありません"
        }
        fetchOnMainThread(activity, requestedUrl, titleHint, extractors)
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private suspend fun fetchOnMainThread(
    activity: Activity,
    requestedUrl: String,
    titleHint: String?,
    extractors: List<WebLibraryMetadataExtractor>,
  ): LibraryBook = suspendCancellableCoroutine { continuation ->
    val mainHandler = Handler(Looper.getMainLooper())
    val webView = WebView(activity)
    WebViewCompat.setProfile(webView, PROFILE_NAME)

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
      setGeolocationEnabled(false)
      mediaPlaybackRequiresUserGesture = true
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

    var completed = false
    var pageGeneration = 0
    var standardExtractionAttempts = 0
    lateinit var extractMetadata: (String, Int) -> Unit
    lateinit var pollCustomMetadata: (String, String, Int, Long) -> Unit

    fun dispose() {
      webView.stopLoading()
      webView.webViewClient = WebViewClient()
      webView.clearHistory()
      webView.removeAllViews()
      webView.destroy()
    }

    fun finish(result: Result<LibraryBook>) {
      if (completed) return
      completed = true
      dispose()
      if (!continuation.isActive) return
      result.fold(
        onSuccess = { continuation.resume(it) },
        onFailure = continuation::resumeWithException,
      )
    }

    fun failAfterRendererExit(detail: RenderProcessGoneDetail) {
      if (completed) return
      completed = true
      webView.destroy()
      if (continuation.isActive) {
        continuation.resumeWithException(
          IllegalStateException(renderProcessGoneMessage(detail.didCrash())),
        )
      }
    }

    fun evaluateStandardMetadata(
      finalUrl: String,
      generation: Int,
      customMetadata: WebLibraryCustomMetadata?,
      allowRetry: Boolean = true,
    ) {
      if (completed || generation != pageGeneration) return
      standardExtractionAttempts += 1
      webView.evaluateJavascript(METADATA_SCRIPT) { rawResult ->
        if (completed || generation != pageGeneration) return@evaluateJavascript
        runCatching {
          parseRenderedWebLibraryBook(
            requestedUrl = finalUrl,
            rawResult = rawResult,
            titleHint = titleHint,
          ).applyCustomMetadata(customMetadata)
        }.fold(
          onSuccess = { book ->
            if (
              !allowRetry ||
              !book.needsRenderedWebMetadata() ||
              standardExtractionAttempts >= MAX_EXTRACTION_ATTEMPTS
            ) {
              finish(Result.success(book))
            } else {
              webView.postDelayed(
                {
                  evaluateStandardMetadata(
                    finalUrl = finalUrl,
                    generation = generation,
                    customMetadata = customMetadata,
                    allowRetry = allowRetry,
                  )
                },
                EXTRACTION_RETRY_DELAY_MILLIS,
              )
            }
          },
          onFailure = { error -> finish(Result.failure(error)) },
        )
      }
    }

    pollCustomMetadata = { finalUrl, stateKey, generation, deadlineMillis ->
      if (!completed && generation == pageGeneration) {
        if (SystemClock.uptimeMillis() >= deadlineMillis) {
          webView.evaluateJavascript(customMetadataCleanupScript(stateKey), null)
          evaluateStandardMetadata(finalUrl, generation, null)
        } else {
          webView.evaluateJavascript(customMetadataPollScript(stateKey)) { rawResult ->
            if (!completed && generation == pageGeneration) {
              val poll = parseCustomMetadataPromisePoll(finalUrl, rawResult)
              when {
                poll == null -> evaluateStandardMetadata(finalUrl, generation, null)
                poll.pending -> webView.postDelayed(
                  {
                    pollCustomMetadata(finalUrl, stateKey, generation, deadlineMillis)
                  },
                  CUSTOM_METADATA_POLL_DELAY_MILLIS,
                )
                else -> evaluateStandardMetadata(finalUrl, generation, poll.metadata)
              }
            }
          }
        }
      }
    }

    extractMetadata = { finalUrl, generation ->
      if (!completed && generation == pageGeneration) {
        val extractor = findMatchingWebLibraryMetadataExtractor(extractors, finalUrl)
          ?: findMatchingWebLibraryMetadataExtractor(extractors, requestedUrl)
        if (extractor == null) {
          evaluateStandardMetadata(finalUrl, generation, null)
        } else {
          val stateKey = "$CUSTOM_METADATA_STATE_PREFIX-$generation-${SystemClock.uptimeMillis()}"
          webView.evaluateJavascript(
            customMetadataStartScript(extractor.functionCode, stateKey),
          ) {
            if (!completed && generation == pageGeneration) {
              pollCustomMetadata(
                finalUrl,
                stateKey,
                generation,
                SystemClock.uptimeMillis() + CUSTOM_METADATA_PROMISE_TIMEOUT_MILLIS,
              )
            }
          }
        }
      }
    }

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return !isSafeRenderedUrl(request.url.toString())
      }

      override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        pageGeneration += 1
        standardExtractionAttempts = 0
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

      override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        failAfterRendererExit(detail)
        return true
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

internal data class WebLibraryCustomMetadata(
  val title: String?,
  val thumbnailUrl: String?,
)

internal data class WebLibraryCustomMetadataPromisePoll(
  val pending: Boolean,
  val metadata: WebLibraryCustomMetadata?,
)

internal fun customMetadataStartScript(functionCode: String, stateKey: String): String {
  val expression = functionCode.trim().removeSuffix(";")
  val quotedStateKey = JSONObject.quote(stateKey)
  return """
    (() => {
      const stateKey = $quotedStateKey;
      window[stateKey] = { pending: true, value: null };
      try {
        const extractor = ($expression);
        if (typeof extractor !== 'function') {
          window[stateKey] = { pending: false, value: null };
          return null;
        }
        const promise = extractor({ url: location.href });
        if (!promise || typeof promise.then !== 'function') {
          window[stateKey] = { pending: false, value: null };
          return null;
        }
        Promise.resolve(promise)
          .then((value) => {
            if (!value || typeof value !== 'object') {
              window[stateKey] = { pending: false, value: null };
              return;
            }
            const title = typeof value.title === 'string' ? value.title.trim() : null;
            const thumbnailUrl = typeof value.thumbnailUrl === 'string' ? value.thumbnailUrl.trim() : null;
            window[stateKey] = {
              pending: false,
              value: JSON.stringify({
                title: title || null,
                thumbnailUrl: thumbnailUrl || null
              })
            };
          })
          .catch(() => {
            window[stateKey] = { pending: false, value: null };
          });
      } catch (_) {
        window[stateKey] = { pending: false, value: null };
      }
      return null;
    })()
  """.trimIndent()
}

internal fun customMetadataPollScript(stateKey: String): String {
  val quotedStateKey = JSONObject.quote(stateKey)
  return """
    (() => {
      const stateKey = $quotedStateKey;
      const state = window[stateKey];
      if (!state) return JSON.stringify({ pending: false, value: null });
      if (state.pending) return JSON.stringify({ pending: true, value: null });
      const value = typeof state.value === 'string' ? state.value : null;
      delete window[stateKey];
      return JSON.stringify({ pending: false, value });
    })()
  """.trimIndent()
}

internal fun customMetadataCleanupScript(stateKey: String): String {
  val quotedStateKey = JSONObject.quote(stateKey)
  return "delete window[$quotedStateKey]; null;"
}

internal fun parseCustomMetadataPromisePoll(
  finalUrl: String,
  rawResult: String?,
): WebLibraryCustomMetadataPromisePoll? = runCatching {
  val evaluationResult = rawResult ?: return null
  val decoded = JSONTokener(evaluationResult).nextValue()
  val poll = when (decoded) {
    is JSONObject -> decoded
    is String -> JSONObject(decoded)
    else -> return null
  }
  val pending = poll.optBoolean("pending", false)
  if (pending) {
    WebLibraryCustomMetadataPromisePoll(pending = true, metadata = null)
  } else {
    val metadata = poll.optionalString("value")?.let { payload ->
      parseCustomRenderedWebLibraryMetadata(finalUrl, JSONObject.quote(payload))
    }
    WebLibraryCustomMetadataPromisePoll(pending = false, metadata = metadata)
  }
}.getOrNull()

internal fun parseCustomRenderedWebLibraryMetadata(
  finalUrl: String,
  rawResult: String?,
): WebLibraryCustomMetadata? = runCatching {
  val evaluationResult = rawResult ?: return null
  val decoded = JSONTokener(evaluationResult).nextValue()
  if (decoded == JSONObject.NULL) return null
  val metadata = when (decoded) {
    is JSONObject -> decoded
    is String -> JSONObject(decoded)
    else -> return null
  }
  val title = metadata.optionalString("title")
  val thumbnailUrl = metadata.optionalString("thumbnailUrl")
    ?.let { resolveRenderedImageUrl(finalUrl, it) }
  if (title == null && thumbnailUrl == null) return null
  WebLibraryCustomMetadata(title = title, thumbnailUrl = thumbnailUrl)
}.getOrNull()

internal fun LibraryBook.applyCustomMetadata(customMetadata: WebLibraryCustomMetadata?): LibraryBook {
  if (customMetadata == null) return this
  return copy(
    title = customMetadata.title ?: title,
    thumbnailUrl = customMetadata.thumbnailUrl ?: thumbnailUrl,
  )
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
  val image = sequenceOf("image", "firstImage")
    .mapNotNull { key ->
      metadata.optionalString(key)
        ?.let { resolveRenderedImageUrl(finalUrl, it) }
    }
    .firstOrNull()
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

internal fun renderProcessGoneMessage(didCrash: Boolean): String = if (didCrash) {
  "WebView の表示プロセスが異常終了しました。再試行してください"
} else {
  "WebView の表示プロセスがメモリ不足で終了しました。再試行してください"
}

private const val METADATA_SCRIPT = """
(() => {
  const meta = (...selectors) => {
    for (const selector of selectors) {
      const value = document.querySelector(selector)?.getAttribute('content')?.trim();
      if (value) return value;
    }
    return null;
  };
  const firstImage = () => {
    for (const image of document.images) {
      const value = (image.currentSrc || image.getAttribute('src') || '').trim();
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
    firstImage: firstImage(),
    author: meta('meta[name="author"]')
  });
})()
"""

private const val PROFILE_NAME = "mosaic-web-library-metadata"
private const val CUSTOM_METADATA_STATE_PREFIX = "__mosaic_web_library_metadata"
private const val DEFAULT_TIMEOUT_MILLIS = 15_000L
private const val INITIAL_DOM_SETTLE_MILLIS = 500L
private const val EXTRACTION_RETRY_DELAY_MILLIS = 500L
private const val CUSTOM_METADATA_POLL_DELAY_MILLIS = 100L
private const val CUSTOM_METADATA_PROMISE_TIMEOUT_MILLIS = 10_000L
private const val MAX_EXTRACTION_ATTEMPTS = 4
