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
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractor
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorExecution
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorRepository
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class WebLibraryRenderedMetadataFetchResult(
  val book: LibraryBook,
  val extractorExecution: WebLibraryMetadataExtractorExecution? = null,
)

internal class WebLibraryRenderedMetadataException(
  message: String,
  val extractorExecution: WebLibraryMetadataExtractorExecution? = null,
  cause: Throwable? = null,
) : IllegalStateException(message, cause)

interface WebLibraryRenderedMetadataClient {
  suspend fun fetch(url: String, titleHint: String? = null): LibraryBook

  suspend fun fetchWithReport(
    url: String,
    titleHint: String? = null,
  ): WebLibraryRenderedMetadataFetchResult = WebLibraryRenderedMetadataFetchResult(fetch(url, titleHint))

  fun hasCustomExtractor(url: String): Boolean = false
}

class AndroidWebViewLibraryMetadataClient(
  private val activityProvider: () -> Activity?,
  private val extractorRepository: WebLibraryMetadataExtractorRepository? = null,
  private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) : WebLibraryRenderedMetadataClient {
  override fun hasCustomExtractor(url: String): Boolean =
    findMatchingWebLibraryMetadataExtractor(extractorRepository?.list().orEmpty(), url) != null

  override suspend fun fetch(url: String, titleHint: String?): LibraryBook =
    fetchWithReport(url, titleHint).book

  override suspend fun fetchWithReport(
    url: String,
    titleHint: String?,
  ): WebLibraryRenderedMetadataFetchResult {
    val requestedUrl = normalizeWebUrl(url)
    require(isSafeRenderedUrl(requestedUrl)) {
      "WebView での metadata 取得は HTTPS ページのみ対応しています"
    }
    val extractors = extractorRepository?.list().orEmpty()
    val effectiveTimeoutMillis = webLibraryMetadataTimeoutMillis(
      extractors = extractors,
      requestedUrl = requestedUrl,
      fallbackTimeoutMillis = timeoutMillis,
    )
    var latestExtractorExecution: WebLibraryMetadataExtractorExecution? = null

    return try {
      withTimeout(effectiveTimeoutMillis) {
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
          fetchOnMainThread(
            activity = activity,
            requestedUrl = requestedUrl,
            titleHint = titleHint,
            extractors = extractors,
            onExtractorExecution = { latestExtractorExecution = it },
          )
        }
      }
    } catch (error: TimeoutCancellationException) {
      throw WebLibraryRenderedMetadataException(
        message = "WebView metadata 取得が ${effectiveTimeoutMillis / 1_000} 秒以内に完了しませんでした",
        extractorExecution = latestExtractorExecution,
        cause = error,
      )
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private suspend fun fetchOnMainThread(
    activity: Activity,
    requestedUrl: String,
    titleHint: String?,
    extractors: List<WebLibraryMetadataExtractor>,
    onExtractorExecution: (WebLibraryMetadataExtractorExecution?) -> Unit,
  ): WebLibraryRenderedMetadataFetchResult = suspendCancellableCoroutine { continuation ->
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
    var extractionStartedGeneration = -1
    var standardExtractionAttempts = 0
    var extractorExecution: WebLibraryMetadataExtractorExecution? = null
    var activeCustomStateKey: String? = null
    var activeCustomWatchdog: Runnable? = null
    lateinit var extractMetadata: (String, Int) -> Unit
    lateinit var pollCustomMetadata: (String, WebLibraryMetadataExtractor, String, Int, Long) -> Unit
    lateinit var startCustomMetadataWhenDomReady: (String, Int) -> Unit

    fun clearCustomExecution() {
      activeCustomStateKey = null
      activeCustomWatchdog?.let(mainHandler::removeCallbacks)
      activeCustomWatchdog = null
    }

    fun dispose() {
      clearCustomExecution()
      webView.stopLoading()
      webView.webViewClient = WebViewClient()
      webView.clearHistory()
      webView.removeAllViews()
      webView.destroy()
    }

    fun finish(result: Result<WebLibraryRenderedMetadataFetchResult>) {
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
      clearCustomExecution()
      webView.destroy()
      if (continuation.isActive) {
        continuation.resumeWithException(
          IllegalStateException(renderProcessGoneMessage(detail.didCrash())),
        )
      }
    }

    fun matchingExtractor(url: String): WebLibraryMetadataExtractor? =
      findMatchingWebLibraryMetadataExtractor(extractors, url)
        ?: findMatchingWebLibraryMetadataExtractor(extractors, requestedUrl)

    fun recordExtractorExecution(
      extractor: WebLibraryMetadataExtractor,
      status: WebLibraryMetadataExtractorStatus,
      message: String? = null,
      metadata: WebLibraryCustomMetadata? = null,
    ) {
      extractorExecution = createWebLibraryMetadataExtractorExecution(
        extractor = extractor,
        status = status,
        message = message,
        metadata = metadata,
      )
      onExtractorExecution(extractorExecution)
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
              finish(
                Result.success(
                  WebLibraryRenderedMetadataFetchResult(
                    book = book,
                    extractorExecution = extractorExecution,
                  ),
                ),
              )
            } else {
              mainHandler.postDelayed(
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

    pollCustomMetadata = { finalUrl, extractor, stateKey, generation, deadlineMillis ->
      if (
        !completed &&
        generation == pageGeneration &&
        activeCustomStateKey == stateKey
      ) {
        if (SystemClock.uptimeMillis() >= deadlineMillis) {
          clearCustomExecution()
          webView.evaluateJavascript(customMetadataCleanupScript(stateKey), null)
          recordExtractorExecution(
            extractor,
            WebLibraryMetadataExtractorStatus.TIMED_OUT,
            "Promise が ${CUSTOM_METADATA_PROMISE_TIMEOUT_MILLIS / 1_000} 秒以内に完了しませんでした",
          )
          evaluateStandardMetadata(finalUrl, generation, null)
        } else {
          webView.evaluateJavascript(customMetadataPollScript(stateKey)) { rawResult ->
            if (
              !completed &&
              generation == pageGeneration &&
              activeCustomStateKey == stateKey
            ) {
              val poll = parseCustomMetadataPromisePoll(finalUrl, rawResult)
              when {
                poll == null -> {
                  clearCustomExecution()
                  recordExtractorExecution(
                    extractor,
                    WebLibraryMetadataExtractorStatus.INVALID_STATE,
                    "取得ルールの実行状態を読み取れませんでした",
                  )
                  evaluateStandardMetadata(finalUrl, generation, null)
                }
                poll.pending -> mainHandler.postDelayed(
                  {
                    pollCustomMetadata(finalUrl, extractor, stateKey, generation, deadlineMillis)
                  },
                  CUSTOM_METADATA_POLL_DELAY_MILLIS,
                )
                else -> {
                  clearCustomExecution()
                  val appliedMetadata = poll.metadata.takeIf {
                    poll.status == WebLibraryMetadataExtractorStatus.APPLIED
                  }
                  recordExtractorExecution(
                    extractor,
                    poll.status ?: WebLibraryMetadataExtractorStatus.INVALID_STATE,
                    poll.message,
                    appliedMetadata,
                  )
                  evaluateStandardMetadata(
                    finalUrl,
                    generation,
                    appliedMetadata,
                  )
                }
              }
            }
          }
        }
      }
    }

    extractMetadata = { finalUrl, generation ->
      if (!completed && generation == pageGeneration && extractionStartedGeneration != generation) {
        extractionStartedGeneration = generation
        val extractor = matchingExtractor(finalUrl)
        if (extractor == null) {
          evaluateStandardMetadata(finalUrl, generation, null)
        } else {
          recordExtractorExecution(
            extractor,
            WebLibraryMetadataExtractorStatus.RUNNING,
            "カスタムスクリプトを WebView へ送信し、開始応答を待機中",
          )
          val stateKey = "$CUSTOM_METADATA_STATE_PREFIX-$generation-${SystemClock.uptimeMillis()}"
          val deadlineMillis = SystemClock.uptimeMillis() + CUSTOM_METADATA_PROMISE_TIMEOUT_MILLIS
          activeCustomStateKey = stateKey
          val watchdog = Runnable {
            if (
              !completed &&
              generation == pageGeneration &&
              activeCustomStateKey == stateKey
            ) {
              activeCustomWatchdog = null
              activeCustomStateKey = null
              webView.evaluateJavascript(customMetadataCleanupScript(stateKey), null)
              recordExtractorExecution(
                extractor,
                WebLibraryMetadataExtractorStatus.TIMED_OUT,
                "Promise 監視中に WebView JavaScript の応答が停止しました",
              )
              finish(
                Result.failure(
                  WebLibraryRenderedMetadataException(
                    message = "カスタム metadata 取得の JavaScript 応答が ${customMetadataNativeWatchdogDelayMillis() / 1_000} 秒以内に戻りませんでした",
                    extractorExecution = extractorExecution,
                  ),
                ),
              )
            }
          }
          activeCustomWatchdog = watchdog
          mainHandler.postDelayed(watchdog, customMetadataNativeWatchdogDelayMillis())
          webView.evaluateJavascript(
            customMetadataStartScript(extractor.functionCode, stateKey),
          ) {
            if (
              !completed &&
              generation == pageGeneration &&
              activeCustomStateKey == stateKey
            ) {
              recordExtractorExecution(
                extractor,
                WebLibraryMetadataExtractorStatus.RUNNING,
                "カスタムスクリプト開始応答を確認。Promise の完了を監視中",
              )
              pollCustomMetadata(
                finalUrl,
                extractor,
                stateKey,
                generation,
                deadlineMillis,
              )
            }
          }
        }
      }
    }

    startCustomMetadataWhenDomReady = { finalUrl, generation ->
      if (!completed && generation == pageGeneration && extractionStartedGeneration != generation) {
        webView.evaluateJavascript("document.readyState") { rawState ->
          if (!completed && generation == pageGeneration && extractionStartedGeneration != generation) {
            if (rawState == "\"loading\"") {
              mainHandler.postDelayed(
                {
                  startCustomMetadataWhenDomReady(finalUrl, generation)
                },
                DOM_READY_POLL_DELAY_MILLIS,
              )
            } else {
              mainHandler.postDelayed(
                {
                  extractMetadata(finalUrl, generation)
                },
                CUSTOM_DOM_SETTLE_MILLIS,
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
        extractionStartedGeneration = -1
        clearCustomExecution()
        standardExtractionAttempts = 0
        val extractor = matchingExtractor(url)
        if (extractor == null) {
          extractorExecution = null
          onExtractorExecution(null)
        } else {
          recordExtractorExecution(
            extractor,
            WebLibraryMetadataExtractorStatus.MATCHED,
            "URL パターンに一致。DOM が利用可能になるのを待機中",
          )
        }
      }

      override fun onPageCommitVisible(view: WebView, url: String) {
        if (completed) return
        val finalUrl = view.url?.takeIf(String::isNotBlank) ?: url
        if (!isSafeRenderedUrl(finalUrl)) return
        val generation = pageGeneration
        if (matchingExtractor(finalUrl) != null) {
          startCustomMetadataWhenDomReady(finalUrl, generation)
        }
      }

      override fun onPageFinished(view: WebView, url: String) {
        if (completed) return
        val finalUrl = view.url?.takeIf(String::isNotBlank) ?: url
        if (!isSafeRenderedUrl(finalUrl)) {
          finish(Result.failure(IllegalArgumentException("HTTPS 以外へ遷移したため metadata 取得を中止しました")))
          return
        }
        val generation = pageGeneration
        if (matchingExtractor(finalUrl) != null) {
          extractMetadata(finalUrl, generation)
        } else {
          mainHandler.postDelayed(
            {
              if (!completed && generation == pageGeneration) {
                extractMetadata(finalUrl, generation)
              }
            },
            INITIAL_DOM_SETTLE_MILLIS,
          )
        }
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
private const val CUSTOM_DOM_SETTLE_MILLIS = 200L
private const val DOM_READY_POLL_DELAY_MILLIS = 100L
private const val EXTRACTION_RETRY_DELAY_MILLIS = 500L
private const val CUSTOM_METADATA_POLL_DELAY_MILLIS = 100L
private const val MAX_EXTRACTION_ATTEMPTS = 4
