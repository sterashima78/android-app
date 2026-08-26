package dev.terashima.yomitorirss.feature.rss.data.network

import android.annotation.SuppressLint
import android.content.Context
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
import dev.terashima.yomitorirss.feature.rss.FeedInspection
import dev.terashima.yomitorirss.feature.rss.RssWebScrapingItemPreview
import dev.terashima.yomitorirss.feature.rss.RssWebScrapingPreview
import dev.terashima.yomitorirss.feature.rss.RssWebScrapingRule
import dev.terashima.yomitorirss.feature.rss.data.normalizeRssWebScrapingUrl
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal class WebScrapingFeedClient(
  context: Context,
  private val now: () -> Instant = Instant::now,
) {
  private val applicationContext = context.applicationContext

  suspend fun inspect(url: String, rule: RssWebScrapingRule): FeedInspection {
    val requestedUrl = normalizeRssWebScrapingUrl(url)
    renderWithTimeout(requestedUrl, rule)
    return FeedInspection(directFeedUrl = requestedUrl)
  }

  suspend fun fetchFeed(
    url: String,
    rule: RssWebScrapingRule,
  ): FetchResult {
    val requestedUrl = normalizeRssWebScrapingUrl(url)
    val preview = renderWithTimeout(requestedUrl, rule)
    return FetchResult(
      feed = preview.toParsedFeed(requestedUrl),
      etag = null,
      lastModified = null,
    )
  }

  suspend fun test(
    url: String,
    rule: RssWebScrapingRule,
  ): RssWebScrapingPreview = renderWithTimeout(normalizeRssWebScrapingUrl(url), rule)

  private suspend fun renderWithTimeout(
    requestedUrl: String,
    rule: RssWebScrapingRule,
  ): RssWebScrapingPreview = try {
    withTimeout(rule.timeoutSeconds * 1_000L) {
      withContext(Dispatchers.Main.immediate) {
        renderOnMainThread(requestedUrl, rule.functionCode)
      }
    }
  } catch (error: TimeoutCancellationException) {
    throw IllegalStateException(
      "Web スクレイピングが ${rule.timeoutSeconds} 秒以内に完了しませんでした",
      error,
    )
  }

  @SuppressLint("SetJavaScriptEnabled")
  private suspend fun renderOnMainThread(
    requestedUrl: String,
    functionCode: String,
  ): RssWebScrapingPreview = suspendCancellableCoroutine { continuation ->
    val handler = Handler(Looper.getMainLooper())
    val webView = WebView(applicationContext)
    var completed = false
    var pageGeneration = 0
    var extractionStartedGeneration = -1
    var activeStateKey: String? = null
    var watchdog: Runnable? = null
    lateinit var startWhenDomReady: (String, Int) -> Unit
    lateinit var startExtraction: (String, Int) -> Unit
    lateinit var pollResult: (String, String, Int, Long) -> Unit

    fun clearExecution() {
      activeStateKey = null
      watchdog?.let(handler::removeCallbacks)
      watchdog = null
    }

    fun dispose() {
      clearExecution()
      handler.removeCallbacksAndMessages(null)
      webView.stopLoading()
      webView.webViewClient = WebViewClient()
      webView.clearHistory()
      webView.removeAllViews()
      webView.destroy()
    }

    fun finish(result: Result<RssWebScrapingPreview>) {
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
      val message = if (detail.didCrash()) {
        "Web スクレイピングの表示処理が異常終了しました。再試行してください"
      } else {
        "Web スクレイピングの表示処理がメモリ不足で終了しました。再試行してください"
      }
      finish(Result.failure(IllegalStateException(message)))
    }

    pollResult = { finalUrl, stateKey, generation, deadlineMillis ->
      if (!completed && generation == pageGeneration && activeStateKey == stateKey) {
        if (SystemClock.uptimeMillis() >= deadlineMillis) {
          clearExecution()
          webView.evaluateJavascript(webScrapingCleanupScript(stateKey), null)
          finish(Result.failure(IllegalStateException("取得スクリプトの Promise が 10 秒以内に完了しませんでした")))
        } else {
          webView.evaluateJavascript(webScrapingPollScript(stateKey)) { rawResult ->
            if (!completed && generation == pageGeneration && activeStateKey == stateKey) {
              val poll = parseWebScrapingPoll(finalUrl, rawResult)
              when {
                poll == null -> finish(
                  Result.failure(IllegalStateException("取得スクリプトの実行状態を読み取れませんでした")),
                )
                poll.pending -> handler.postDelayed(
                  { pollResult(finalUrl, stateKey, generation, deadlineMillis) },
                  POLL_DELAY_MILLIS,
                )
                poll.preview != null -> {
                  clearExecution()
                  finish(Result.success(poll.preview))
                }
                else -> {
                  clearExecution()
                  finish(
                    Result.failure(
                      IllegalStateException(poll.message ?: "取得スクリプトの実行に失敗しました"),
                    ),
                  )
                }
              }
            }
          }
        }
      }
    }

    startExtraction = { finalUrl, generation ->
      if (!completed && generation == pageGeneration && extractionStartedGeneration != generation) {
        extractionStartedGeneration = generation
        val stateKey = "$STATE_PREFIX-$generation-${SystemClock.uptimeMillis()}"
        activeStateKey = stateKey
        val deadlineMillis = SystemClock.uptimeMillis() + PROMISE_TIMEOUT_MILLIS
        val timeoutWatchdog = Runnable {
          if (!completed && generation == pageGeneration && activeStateKey == stateKey) {
            activeStateKey = null
            watchdog = null
            finish(
              Result.failure(
                IllegalStateException("取得スクリプト実行中に WebView JavaScript の応答が停止しました"),
              ),
            )
          }
        }
        watchdog = timeoutWatchdog
        handler.postDelayed(timeoutWatchdog, PROMISE_TIMEOUT_MILLIS + WATCHDOG_GRACE_MILLIS)
        webView.evaluateJavascript(webScrapingStartScript(functionCode, stateKey)) {
          if (!completed && generation == pageGeneration && activeStateKey == stateKey) {
            pollResult(finalUrl, stateKey, generation, deadlineMillis)
          }
        }
      }
    }

    startWhenDomReady = { finalUrl, generation ->
      if (!completed && generation == pageGeneration && extractionStartedGeneration != generation) {
        webView.evaluateJavascript("document.readyState") { rawState ->
          if (!completed && generation == pageGeneration && extractionStartedGeneration != generation) {
            if (rawState == "\"loading\"") {
              handler.postDelayed(
                { startWhenDomReady(finalUrl, generation) },
                DOM_READY_POLL_DELAY_MILLIS,
              )
            } else {
              handler.postDelayed(
                { startExtraction(finalUrl, generation) },
                DOM_SETTLE_DELAY_MILLIS,
              )
            }
          }
        }
      }
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
      setGeolocationEnabled(false)
      mediaPlaybackRequiresUserGesture = true
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)

    webView.webViewClient = object : WebViewClient() {
      override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return !isSafeLoadedUrl(request.url.toString())
      }

      override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        pageGeneration += 1
        extractionStartedGeneration = -1
        clearExecution()
      }

      override fun onPageCommitVisible(view: WebView, url: String) {
        if (completed) return
        val finalUrl = view.url?.takeIf(String::isNotBlank) ?: url
        if (!isSafeLoadedUrl(finalUrl)) return
        startWhenDomReady(finalUrl, pageGeneration)
      }

      override fun onPageFinished(view: WebView, url: String) {
        if (completed) return
        val finalUrl = view.url?.takeIf(String::isNotBlank) ?: url
        if (!isSafeLoadedUrl(finalUrl)) {
          finish(Result.failure(IllegalArgumentException("HTTPS 以外へ遷移したため取得を中止しました")))
          return
        }
        startExtraction(finalUrl, pageGeneration)
      }

      override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
      ) {
        if (request.isForMainFrame) {
          finish(Result.failure(IllegalStateException("Web ページを表示できませんでした: ${error.description}")))
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
              IllegalStateException("Web ページを表示できませんでした: HTTP ${errorResponse.statusCode}"),
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
      handler.post {
        if (!completed) {
          completed = true
          dispose()
        }
      }
    }

    webView.loadUrl(requestedUrl)
  }

  private fun RssWebScrapingPreview.toParsedFeed(requestedUrl: String): ParsedFeed = ParsedFeed(
    title = title,
    feedUrl = requestedUrl,
    siteUrl = siteUrl ?: requestedUrl,
    articles = items.map { item ->
      val published = normalizePublishedAt(item.publishedAt)
      ParsedArticle(
        externalId = item.externalId,
        identityKey = identityKey(item.externalId, item.url),
        url = item.url,
        title = item.title,
        publishedAt = published,
      )
    },
  )

  private fun normalizePublishedAt(value: String?): String {
    val text = value?.trim().orEmpty()
    if (text.isBlank()) return now().toString()
    return runCatching { Instant.parse(text).toString() }
      .recoverCatching {
        LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
      }
      .getOrElse { now().toString() }
  }

  private fun identityKey(externalId: String?, url: String): String {
    val source = externalId?.takeIf(String::isNotBlank) ?: url
    return MessageDigest.getInstance("SHA-256")
      .digest(source.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
  }
}

internal data class WebScrapingPromisePoll(
  val pending: Boolean,
  val preview: RssWebScrapingPreview? = null,
  val message: String? = null,
)

internal fun webScrapingStartScript(functionCode: String, stateKey: String): String {
  val expression = functionCode.trim().removeSuffix(";")
  val quotedExpression = JSONObject.quote(expression)
  val quotedStateKey = JSONObject.quote(stateKey)
  return """
    (() => {
      const stateKey = $quotedStateKey;
      const source = $quotedExpression;
      const finish = (status, value = null, message = null) => {
        window[stateKey] = { pending: false, status, value, message };
      };
      const errorMessage = (error) => {
        const value = error && typeof error.message === 'string'
          ? error.message
          : String(error ?? '');
        return value.trim().slice(0, 500) || null;
      };
      window[stateKey] = { pending: true, status: null, value: null, message: null };
      setTimeout(() => {
        let extractor;
        try {
          extractor = eval('(' + source + ')');
        } catch (error) {
          finish('invalid_function', null, errorMessage(error));
          return;
        }
        if (typeof extractor !== 'function') {
          finish('invalid_function', null, '関数式として評価できませんでした');
          return;
        }
        try {
          const promise = extractor({ url: location.href });
          if (!promise || typeof promise.then !== 'function') {
            finish('non_promise_result', null, 'Promise を返していません');
            return;
          }
          Promise.resolve(promise)
            .then((value) => {
              if (!value || typeof value !== 'object') {
                finish('invalid_result', null, '戻り値が object ではありません');
                return;
              }
              const title = typeof value.title === 'string' ? value.title.trim() : '';
              if (!title) {
                finish('invalid_result', null, 'title が空です');
                return;
              }
              if (!Array.isArray(value.items)) {
                finish('invalid_result', null, 'items が配列ではありません');
                return;
              }
              const items = [];
              for (let index = 0; index < value.items.length; index += 1) {
                const item = value.items[index];
                if (!item || typeof item !== 'object') {
                  finish('invalid_result', null, 'items[' + index + '] が object ではありません');
                  return;
                }
                const itemTitle = typeof item.title === 'string' ? item.title.trim() : '';
                const itemUrl = typeof item.url === 'string' ? item.url.trim() : '';
                if (!itemTitle || !itemUrl) {
                  finish('invalid_result', null, 'items[' + index + '] の title または url が空です');
                  return;
                }
                items.push({
                  title: itemTitle,
                  url: itemUrl,
                  externalId: typeof item.externalId === 'string' && item.externalId.trim()
                    ? item.externalId.trim()
                    : null,
                  publishedAt: typeof item.publishedAt === 'string' && item.publishedAt.trim()
                    ? item.publishedAt.trim()
                    : null
                });
              }
              const siteUrl = typeof value.siteUrl === 'string' && value.siteUrl.trim()
                ? value.siteUrl.trim()
                : null;
              try {
                finish('applied', JSON.stringify({ title, siteUrl, items }));
              } catch (error) {
                finish('invalid_result', null, errorMessage(error));
              }
            })
            .catch((error) => finish('rejected', null, errorMessage(error)));
        } catch (error) {
          finish('threw', null, errorMessage(error));
        }
      }, 0);
      return null;
    })()
  """.trimIndent()
}

internal fun webScrapingPollScript(stateKey: String): String {
  val quotedStateKey = JSONObject.quote(stateKey)
  return """
    (() => {
      const stateKey = $quotedStateKey;
      const state = window[stateKey];
      if (!state) {
        return JSON.stringify({ pending: false, status: 'invalid_state', value: null, message: '実行状態が見つかりません' });
      }
      if (state.pending) {
        return JSON.stringify({ pending: true, status: null, value: null, message: null });
      }
      const result = JSON.stringify({
        pending: false,
        status: typeof state.status === 'string' ? state.status : 'invalid_state',
        value: typeof state.value === 'string' ? state.value : null,
        message: typeof state.message === 'string' ? state.message : null
      });
      delete window[stateKey];
      return result;
    })()
  """.trimIndent()
}

internal fun webScrapingCleanupScript(stateKey: String): String =
  "delete window[${JSONObject.quote(stateKey)}]; null;"

internal fun parseWebScrapingPoll(
  finalUrl: String,
  rawResult: String?,
): WebScrapingPromisePoll? {
  val poll = runCatching {
    val decoded = rawResult?.let { JSONTokener(it).nextValue() } ?: return null
    when (decoded) {
      is JSONObject -> decoded
      is String -> JSONObject(decoded)
      else -> return null
    }
  }.getOrNull() ?: return null

  if (poll.optBoolean("pending", false)) return WebScrapingPromisePoll(pending = true)
  val status = poll.optString("status").trim()
  if (status != "applied") {
    return WebScrapingPromisePoll(
      pending = false,
      message = poll.optString("message").trim().takeIf(String::isNotEmpty)
        ?: webScrapingStatusMessage(status),
    )
  }
  val value = poll.optString("value").trim()
  if (value.isBlank()) return WebScrapingPromisePoll(false, message = "取得結果が空です")
  return runCatching { parseWebScrapingPreview(finalUrl, value) }
    .fold(
      onSuccess = { preview ->
        WebScrapingPromisePoll(
          pending = false,
          preview = preview,
        )
      },
      onFailure = { error ->
        WebScrapingPromisePoll(
          pending = false,
          message = error.message?.takeIf(String::isNotBlank) ?: "取得結果が不正です",
        )
      },
    )
}

internal fun parseWebScrapingPreview(
  finalUrl: String,
  payload: String,
): RssWebScrapingPreview {
  val root = JSONObject(payload)
  val title = root.optString("title").trim()
  require(title.isNotBlank()) { "title が空です" }
  val siteUrl = root.optNullableString("siteUrl")?.let { value -> resolveSafeUrl(finalUrl, value) }
  val itemsJson = root.optJSONArray("items") ?: JSONArray()
  val items = buildList {
    for (index in 0 until itemsJson.length()) {
      val item = itemsJson.getJSONObject(index)
      val itemTitle = item.optString("title").trim()
      val itemUrl = resolveSafeUrl(finalUrl, item.optString("url").trim())
      require(itemTitle.isNotBlank()) { "items[$index] の title が空です" }
      add(
        RssWebScrapingItemPreview(
          title = itemTitle,
          url = itemUrl,
          externalId = item.optNullableString("externalId"),
          publishedAt = item.optNullableString("publishedAt"),
        ),
      )
    }
  }
  return RssWebScrapingPreview(
    title = title,
    siteUrl = siteUrl ?: finalUrl,
    items = items.distinctBy { it.url },
  )
}

private fun JSONObject.optNullableString(name: String): String? =
  if (isNull(name)) null else optString(name).trim().takeIf(String::isNotEmpty)

private fun resolveSafeUrl(baseUrl: String, value: String): String {
  require(value.isNotBlank()) { "URL が空です" }
  val resolved = URI(baseUrl).resolve(value).normalize()
  require(
    resolved.scheme.equals("https", ignoreCase = true) &&
      !resolved.host.isNullOrBlank() &&
      (resolved.port == -1 || resolved.port == 443)
  ) {
    "取得結果の URL は HTTPS の標準ポートである必要があります"
  }
  return resolved.toString()
}

private fun isSafeLoadedUrl(url: String): Boolean = runCatching {
  val uri = URI(url)
  uri.scheme.equals("https", ignoreCase = true) &&
    !uri.host.isNullOrBlank() &&
    (uri.port == -1 || uri.port == 443)
}.getOrDefault(false)

private fun webScrapingStatusMessage(status: String): String = when (status) {
  "invalid_function" -> "取得スクリプトを関数として評価できませんでした"
  "non_promise_result" -> "取得スクリプトが Promise を返していません"
  "invalid_result" -> "取得スクリプトの戻り値が不正です"
  "rejected" -> "取得スクリプトの Promise が reject されました"
  "threw" -> "取得スクリプトの実行中にエラーが発生しました"
  else -> "取得スクリプトの実行状態が不正です"
}

private const val STATE_PREFIX = "__yomitoriRssWebScraping"
private const val PROMISE_TIMEOUT_MILLIS = 10_000L
private const val WATCHDOG_GRACE_MILLIS = 1_000L
private const val POLL_DELAY_MILLIS = 100L
private const val DOM_READY_POLL_DELAY_MILLIS = 100L
private const val DOM_SETTLE_DELAY_MILLIS = 150L
