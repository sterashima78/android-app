package dev.terashima.yomitorirss.feature.video.data

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.terashima.yomitorirss.feature.video.VideoPlaybackCookieProvider
import dev.terashima.yomitorirss.feature.video.WebVideoExtractionResult
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidWebVideoExtractorClient(
  private val activityProvider: () -> Activity?,
) {
  suspend fun extract(url: String, rule: WebVideoExtractorRule): WebVideoExtractionResult =
    extract(url, rule, includePlaybackCookies = false)

  suspend fun extractForPlayback(url: String, rule: WebVideoExtractorRule): WebVideoExtractionResult =
    extract(url, rule, includePlaybackCookies = rule.shareCookiesForPlayback)

  private suspend fun extract(
    url: String,
    rule: WebVideoExtractorRule,
    includePlaybackCookies: Boolean,
  ): WebVideoExtractionResult {
    validateWebVideoExtractorRule(rule)
    require(isSafeExtractorPageUrl(url)) { "動画抽出はHTTPSページのみ対応しています" }
    return withTimeout(rule.timeoutSeconds * 1_000L) {
      withContext(Dispatchers.Main.immediate) {
        require(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
          "安全なWebView動画抽出を利用できません。Android System WebViewを更新してください"
        }
        val activity = requireNotNull(activityProvider()) { "動画抽出を実行できる画面がありません" }
        require(!activity.isFinishing && !activity.isDestroyed) { "動画抽出を実行できる画面がありません" }
        extractOnMainThread(activity, url, rule, includePlaybackCookies)
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private suspend fun extractOnMainThread(
    activity: Activity,
    requestedUrl: String,
    rule: WebVideoExtractorRule,
    includePlaybackCookies: Boolean,
  ): WebVideoExtractionResult = suspendCancellableCoroutine { continuation ->
    val handler = Handler(Looper.getMainLooper())
    val webView = WebView(activity)
    WebViewCompat.setProfile(webView, PROFILE_NAME)
    val profileCookieManager = WebViewCompat.getProfile(webView).cookieManager
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
    val cookieInterceptSupported = WebViewFeature.isFeatureSupported(WebViewFeature.COOKIE_INTERCEPT)
    val captureRequestCookies = includePlaybackCookies && cookieInterceptSupported
    if (captureRequestCookies) {
      WebSettingsCompat.setCookiesIncludedInShouldInterceptRequest(webView.settings, true)
    }
    profileCookieManager.setAcceptThirdPartyCookies(webView, false)
    val requestReferrers = WebVideoRequestReferrerCapture()
    val requestCookies = WebVideoRequestCookieCapture(enabled = captureRequestCookies)
    val requestDiagnostics = WebVideoRequestDiagnosticsCapture()

    var completed = false
    val stateKey = "__mosaicVideoExtractor_${SystemClock.uptimeMillis()}"

    fun dispose() {
      handler.removeCallbacksAndMessages(null)
      webView.stopLoading()
      webView.webViewClient = WebViewClient()
      webView.clearHistory()
      webView.removeAllViews()
      webView.destroy()
    }

    fun finish(result: Result<WebVideoExtractionResult>) {
      if (completed) return
      completed = true
      dispose()
      if (!continuation.isActive) return
      result.fold(
        onSuccess = { continuation.resume(it) },
        onFailure = continuation::resumeWithException,
      )
    }

    fun poll(finalUrl: String) {
      if (completed) return
      webView.evaluateJavascript(pollScript(stateKey)) { raw ->
        if (completed) return@evaluateJavascript
        runCatching { parsePoll(finalUrl, raw) }.fold(
          onSuccess = { poll ->
            when (poll.state) {
              "pending" -> handler.postDelayed({ poll(finalUrl) }, POLL_DELAY_MILLIS)
              "done" -> {
                val extraction = poll.result ?: WebVideoExtractionResult()
                val streamUrl = extraction.streamUrl
                val playbackCookieProvider = streamUrl?.let { resolvedStreamUrl ->
                  requestCookies.retainOnly(resolvedStreamUrl)
                  createPlaybackCookieProvider(
                    enabled = includePlaybackCookies,
                    capturedCookieLookup = { requestUrl ->
                      if (samePlaybackRequestUrl(requestUrl, resolvedStreamUrl)) {
                        requestCookies.cookieFor(requestUrl)
                      } else {
                        null
                      }
                    },
                    cookieLookup = profileCookieManager::getCookie,
                  )
                }
                val playbackDiagnostics = streamUrl?.let { resolvedStreamUrl ->
                  val observed = requestDiagnostics.diagnosticsFor(resolvedStreamUrl)
                  val capturedCookieObserved = requestCookies.cookieFor(resolvedStreamUrl) != null
                  val profileCookieAvailable = if (includePlaybackCookies) {
                    runCatching {
                      !profileCookieManager.getCookie(resolvedStreamUrl).isNullOrBlank()
                    }.getOrDefault(false)
                  } else {
                    false
                  }
                  webVideoPlaybackDiagnostics(
                    cookieSharingEnabled = includePlaybackCookies,
                    cookieInterceptSupported = cookieInterceptSupported,
                    observed = observed,
                    streamRequestCookieObserved = capturedCookieObserved,
                    profileCookieAvailable = profileCookieAvailable,
                  )
                }
                if (streamUrl == null) requestCookies.clear()
                requestDiagnostics.clear()
                finish(
                  Result.success(
                    extraction.copy(
                      referrerUrl = streamUrl?.let(requestReferrers::referrerFor),
                      cookieProvider = playbackCookieProvider,
                      playbackDiagnostics = playbackDiagnostics,
                    ),
                  ),
                )
              }
              "error" -> finish(Result.failure(IllegalStateException(poll.message ?: "動画抽出に失敗しました")))
              else -> finish(Result.failure(IllegalStateException("動画抽出の実行状態が不正です")))
            }
          },
          onFailure = { finish(Result.failure(it)) },
        )
      }
    }

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        !isSafeExtractorPageUrl(request.url.toString())

      override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
      ): WebResourceResponse? {
        requestDiagnostics.record(request.url.toString(), request.requestHeaders)
        requestReferrers.record(request.url.toString(), request.requestHeaders)
        requestCookies.record(request.url.toString(), request.requestHeaders)
        return null
      }

      override fun onPageFinished(view: WebView, url: String) {
        if (completed || !isSafeExtractorPageUrl(url)) return
        view.evaluateJavascript(startScript(stateKey, rule)) { _ ->
          if (!completed) poll(url)
        }
      }

      override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        finish(
          Result.failure(
            IllegalStateException(
              if (detail.didCrash()) "動画抽出WebViewがクラッシュしました" else "動画抽出WebViewが終了しました",
            ),
          ),
        )
        return true
      }
    }

    continuation.invokeOnCancellation {
      handler.post {
        if (!completed) {
          completed = true
          requestCookies.clear()
          requestDiagnostics.clear()
          dispose()
        }
      }
    }
    webView.loadUrl(requestedUrl)
  }

  private data class PollResult(
    val state: String,
    val result: WebVideoExtractionResult? = null,
    val message: String? = null,
  )

  private fun parsePoll(finalUrl: String, raw: String?): PollResult {
    val jsonString = JSONTokener(raw ?: "null").nextValue() as? String
      ?: error("動画抽出の状態を読み取れませんでした")
    val json = JSONObject(jsonString)
    val state = json.optString("state")
    if (state != "done") {
      return PollResult(
        state = state,
        message = json.optString("message").takeIf(String::isNotBlank),
      )
    }
    val value = json.optJSONObject("value") ?: JSONObject()
    return PollResult(
      state = state,
      result = WebVideoExtractionResult(
        title = value.optString("title").takeIf(String::isNotBlank),
        thumbnailUrl = value.optString("thumbnailUrl")
          .takeIf(String::isNotBlank)
          ?.let { resolveWebVideoExtractorUrl(finalUrl, it, httpsOnly = true) },
        streamUrl = value.optString("streamUrl")
          .takeIf(String::isNotBlank)
          ?.let { resolveWebVideoExtractorUrl(finalUrl, it, httpsOnly = false) },
        mimeType = value.optString("mimeType").takeIf(String::isNotBlank),
      ),
    )
  }

  private fun startScript(stateKey: String, rule: WebVideoExtractorRule): String {
    val key = JSONObject.quote(stateKey)
    val title = rule.titleExtractorCode?.takeIf(String::isNotBlank)?.let(::functionRunner) ?: "null"
    val thumbnail = rule.thumbnailExtractorCode?.takeIf(String::isNotBlank)?.let(::functionRunner) ?: "null"
    val playback = rule.playbackExtractorCode?.takeIf(String::isNotBlank)?.let(::functionRunner) ?: "null"
    return """
      (() => {
        const key = $key;
        window[key] = { state: 'pending' };
        const run = async (fn) => {
          if (!fn) return null;
          const promise = fn({ url: location.href });
          if (!promise || typeof promise.then !== 'function') throw new Error('extractor must return Promise');
          const result = await promise;
          if (result == null) return null;
          if (typeof result !== 'object') throw new Error('extractor result must be object');
          return result;
        };
        Promise.resolve().then(async () => {
          const titleResult = await run($title);
          const thumbnailResult = await run($thumbnail);
          const playbackResult = await run($playback);
          window[key] = {
            state: 'done',
            value: {
              title: titleResult && typeof titleResult.title === 'string' ? titleResult.title : null,
              thumbnailUrl: thumbnailResult && typeof thumbnailResult.thumbnailUrl === 'string' ? thumbnailResult.thumbnailUrl : null,
              streamUrl: playbackResult && typeof playbackResult.streamUrl === 'string' ? playbackResult.streamUrl : null,
              mimeType: playbackResult && typeof playbackResult.mimeType === 'string' ? playbackResult.mimeType : null,
            }
          };
        }).catch((error) => {
          window[key] = {
            state: 'error',
            message: String(error && error.message ? error.message : error).slice(0, 240)
          };
        });
        return true;
      })();
    """.trimIndent()
  }

  private fun functionRunner(code: String): String = "($code)"

  private fun pollScript(stateKey: String): String {
    val key = JSONObject.quote(stateKey)
    return "JSON.stringify(window[$key] || { state: 'pending' })"
  }

  private companion object {
    const val PROFILE_NAME = "mosaic-video-extractor"
    const val POLL_DELAY_MILLIS = 100L
  }
}

internal class WebVideoRequestReferrerCapture(
  private val maxEntries: Int = 64,
) {
  private val entries = mutableListOf<Pair<String, String>>()

  init {
    require(maxEntries > 0)
  }

  @Synchronized
  fun record(requestUrl: String, requestHeaders: Map<String, String>) {
    val safeRequestUrl = validPlaybackRequestUrl(requestUrl) ?: return
    val referrer = requestHeaders.entries
      .firstOrNull { (name, _) -> name.equals("Referer", ignoreCase = true) }
      ?.value
      ?.let(::webVideoReferrerOrigin)
      ?: return
    entries.removeAll { (url, _) -> samePlaybackRequestUrl(url, safeRequestUrl) }
    entries += safeRequestUrl to referrer
    while (entries.size > maxEntries) entries.removeAt(0)
  }

  @Synchronized
  fun referrerFor(requestUrl: String): String? {
    val safeRequestUrl = validPlaybackRequestUrl(requestUrl) ?: return null
    return entries.lastOrNull { (url, _) -> samePlaybackRequestUrl(url, safeRequestUrl) }?.second
  }
}

internal class WebVideoRequestCookieCapture(
  private val enabled: Boolean,
  private val maxEntries: Int = 64,
) {
  private val entries = mutableListOf<Pair<String, String>>()

  init {
    require(maxEntries > 0)
  }

  @Synchronized
  fun record(requestUrl: String, requestHeaders: Map<String, String>) {
    if (!enabled) return
    val safeRequestUrl = validPlaybackRequestUrl(requestUrl) ?: return
    val cookie = requestHeaders.entries
      .firstOrNull { (name, _) -> name.equals("Cookie", ignoreCase = true) }
      ?.value
      ?.takeIf(String::isNotBlank)
      ?: return
    entries.removeAll { (url, _) -> samePlaybackRequestUrl(url, safeRequestUrl) }
    entries += safeRequestUrl to cookie
    while (entries.size > maxEntries) entries.removeAt(0)
  }

  @Synchronized
  fun cookieFor(requestUrl: String): String? {
    if (!enabled) return null
    val safeRequestUrl = validPlaybackRequestUrl(requestUrl) ?: return null
    return entries.lastOrNull { (url, _) -> samePlaybackRequestUrl(url, safeRequestUrl) }?.second
  }

  @Synchronized
  fun retainOnly(requestUrl: String) {
    if (!enabled) {
      entries.clear()
      return
    }
    val safeRequestUrl = validPlaybackRequestUrl(requestUrl)
    if (safeRequestUrl == null) {
      entries.clear()
      return
    }
    entries.removeAll { (url, _) -> !samePlaybackRequestUrl(url, safeRequestUrl) }
  }

  @Synchronized
  fun clear() {
    entries.clear()
  }
}

internal fun createPlaybackCookieProvider(
  enabled: Boolean,
  capturedCookieLookup: (String) -> String? = { null },
  cookieLookup: (String) -> String?,
): VideoPlaybackCookieProvider? = if (!enabled) {
  null
} else {
  VideoPlaybackCookieProvider { url ->
    val safeUrl = validPlaybackCookieUrl(url) ?: return@VideoPlaybackCookieProvider null
    runCatching { capturedCookieLookup(safeUrl) }
      .getOrNull()
      ?.takeIf(String::isNotBlank)
      ?: runCatching { cookieLookup(safeUrl) }
        .getOrNull()
        ?.takeIf(String::isNotBlank)
  }
}

internal fun validPlaybackCookieUrl(url: String): String? = runCatching {
  val uri = URI(url)
  val scheme = uri.scheme?.lowercase()
  uri.takeIf {
    (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
  }?.toString()
}.getOrNull()

internal fun validPlaybackRequestUrl(url: String): String? = runCatching {
  val withoutFragment = url.substringBefore('#')
  val uri = URI(withoutFragment)
  val scheme = uri.scheme?.lowercase()
  withoutFragment.takeIf {
    (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
  }
}.getOrNull()

internal fun samePlaybackRequestUrl(left: String, right: String): Boolean = runCatching {
  val leftUri = URI(left)
  val rightUri = URI(right)
  leftUri.scheme.equals(rightUri.scheme, ignoreCase = true) &&
    leftUri.host.equals(rightUri.host, ignoreCase = true) &&
    effectivePort(leftUri) == effectivePort(rightUri) &&
    leftUri.rawPath == rightUri.rawPath &&
    leftUri.rawQuery == rightUri.rawQuery
}.getOrDefault(false)

private fun effectivePort(uri: URI): Int = when {
  uri.port >= 0 -> uri.port
  uri.scheme.equals("https", ignoreCase = true) -> 443
  else -> 80
}

internal fun webVideoReferrerOrigin(referrerUrl: String): String? = runCatching {
  val uri = URI(referrerUrl)
  val scheme = uri.scheme?.lowercase()
  require((scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank())
  URI(scheme, null, uri.host, uri.port, "/", null, null).toString()
}.getOrNull()

internal fun resolveWebVideoExtractorUrl(
  baseUrl: String,
  candidate: String,
  httpsOnly: Boolean,
): String? = runCatching {
  val resolved = URI(baseUrl).resolve(candidate)
  val scheme = resolved.scheme?.lowercase()
  val allowed = if (httpsOnly) scheme == "https" else scheme == "https" || scheme == "http"
  resolved.takeIf { allowed && !it.host.isNullOrBlank() }?.toString()
}.getOrNull()

internal fun isSafeExtractorPageUrl(url: String): Boolean = runCatching {
  val uri = URI(url)
  uri.scheme.equals("https", ignoreCase = true) &&
    !uri.host.isNullOrBlank() &&
    (uri.port == -1 || uri.port == 443)
}.getOrDefault(false)
