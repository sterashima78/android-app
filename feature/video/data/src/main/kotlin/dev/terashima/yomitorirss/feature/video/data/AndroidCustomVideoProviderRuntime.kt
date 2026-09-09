package dev.terashima.yomitorirss.feature.video.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpMethod
import dev.terashima.yomitorirss.core.network.HttpRequest
import java.net.URI
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal interface CustomVideoProviderRuntime {
  suspend fun subscribe(functionCode: String, sourceUrl: String): VideoProviderFeed

  suspend fun refresh(functionCode: String, sourceId: String): VideoProviderFeed
}

internal class AndroidCustomVideoProviderRuntime(
  context: Context,
  private val httpClient: HttpClient,
) : CustomVideoProviderRuntime {
  private val appContext = context.applicationContext

  override suspend fun subscribe(functionCode: String, sourceUrl: String): VideoProviderFeed =
    execute(
      functionCode = functionCode,
      input = JSONObject()
        .put("mode", "subscribe")
        .put("sourceUrl", sourceUrl),
      expectedSourceId = null,
    )

  override suspend fun refresh(functionCode: String, sourceId: String): VideoProviderFeed =
    execute(
      functionCode = functionCode,
      input = JSONObject()
        .put("mode", "refresh")
        .put("sourceId", sourceId),
      expectedSourceId = sourceId,
    )

  private suspend fun execute(
    functionCode: String,
    input: JSONObject,
    expectedSourceId: String?,
  ): VideoProviderFeed = withTimeout(EXECUTION_TIMEOUT_MILLIS) {
    require(functionCode.isNotBlank()) { "カスタム動画プロバイダのfunction codeが空です" }
    require(functionCode.length <= MAX_FUNCTION_CHARS) { "カスタム動画プロバイダのfunction codeが長すぎます" }
    require(WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
      "安全なカスタム動画プロバイダ実行環境を利用できません"
    }

    val rendererLifecycle = CustomProviderRendererLifecycle()
    val webView = withContext(Dispatchers.Main.immediate) { createWebView(rendererLifecycle) }
    val stateKey = "__mosaicCustomVideoProvider_${SystemClock.uptimeMillis()}_${System.identityHashCode(webView)}"
    var nextScript = startScript(stateKey, functionCode, input)
    var requestCount = 0
    try {
      while (true) {
        when (val step = evaluateStep(webView, rendererLifecycle, nextScript)) {
          is ScriptStep.Request -> {
            requestCount += 1
            require(requestCount <= MAX_REQUESTS) { "カスタム動画プロバイダのHTTP request回数が上限を超えました" }
            val response = executeRequest(step.request)
            nextScript = resumeScript(stateKey, step.id, response)
          }
          is ScriptStep.Done -> {
            val feed = parseFeedValue(step.value)
            if (expectedSourceId != null) {
              require(feed.sourceId == expectedSourceId) {
                "カスタム動画プロバイダのsourceIdがrefresh前後で変化しました"
              }
            }
            return@withTimeout feed
          }
          ScriptStep.Pending -> error("カスタム動画プロバイダの実行状態が不正です")
        }
      }
      error("カスタム動画プロバイダの実行が完了しませんでした")
    } finally {
      withContext(NonCancellable + Dispatchers.Main.immediate) {
        rendererLifecycle.onFailure = null
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.clearHistory()
        webView.removeAllViews()
        webView.destroy()
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun createWebView(rendererLifecycle: CustomProviderRendererLifecycle): WebView =
    WebView(appContext).also { webView ->
      WebViewCompat.setProfile(webView, PROFILE_NAME)
      WebViewCompat.getProfile(webView).cookieManager.apply {
        setAcceptCookie(false)
        setAcceptThirdPartyCookies(webView, false)
      }
      webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        blockNetworkLoads = true
        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        javaScriptCanOpenWindowsAutomatically = false
        setSupportMultipleWindows(false)
        safeBrowsingEnabled = true
        setGeolocationEnabled(false)
      }
      webView.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
          rendererLifecycle.fail(
            IllegalStateException(
              if (detail.didCrash()) {
                "カスタム動画プロバイダのWebViewがクラッシュしました"
              } else {
                "カスタム動画プロバイダのWebViewが終了しました"
              },
            ),
          )
          return true
        }
      }
    }

  private suspend fun evaluateStep(
    webView: WebView,
    rendererLifecycle: CustomProviderRendererLifecycle,
    script: String,
  ): ScriptStep = withContext(Dispatchers.Main.immediate) {
    suspendCancellableCoroutine { continuation ->
      val handler = Handler(Looper.getMainLooper())
      var completed = false

      fun finish(result: Result<ScriptStep>) {
        if (completed) return
        completed = true
        handler.removeCallbacksAndMessages(null)
        rendererLifecycle.onFailure = null
        if (!continuation.isActive) return
        result.fold(
          onSuccess = { continuation.resume(it) },
          onFailure = continuation::resumeWithException,
        )
      }

      rendererLifecycle.failure?.let { failure ->
        finish(Result.failure(failure))
        return@suspendCancellableCoroutine
      }
      rendererLifecycle.onFailure = { failure -> finish(Result.failure(failure)) }

      fun poll() {
        if (completed || !continuation.isActive) return
        rendererLifecycle.failure?.let { failure ->
          finish(Result.failure(failure))
          return
        }
        webView.evaluateJavascript(pollScript(stateKey = null)) { raw ->
          if (completed || !continuation.isActive) return@evaluateJavascript
          rendererLifecycle.failure?.let { failure ->
            finish(Result.failure(failure))
            return@evaluateJavascript
          }
          runCatching { parseStep(raw) }.fold(
            onSuccess = { step ->
              if (step == ScriptStep.Pending) {
                handler.postDelayed(::poll, POLL_DELAY_MILLIS)
              } else {
                finish(Result.success(step))
              }
            },
            onFailure = { finish(Result.failure(it)) },
          )
        }
      }

      continuation.invokeOnCancellation {
        handler.post {
          completed = true
          handler.removeCallbacksAndMessages(null)
          rendererLifecycle.onFailure = null
        }
      }
      webView.evaluateJavascript(script) {
        if (!completed) poll()
      }
    }
  }

  private suspend fun executeRequest(request: JSONObject): JSONObject {
    val response = httpClient.execute(buildHttpRequest(request))
    requireHttpsUrl(response.finalUrl, "response URL")
    return JSONObject()
      .put("status", response.statusCode)
      .put("ok", response.isSuccessful)
      .put("url", response.finalUrl)
      .put(
        "headers",
        JSONObject().apply {
          response.headers.forEach { (name, values) -> put(name, JSONArray(values)) }
        },
      )
      .put("body", response.body.toString(Charsets.UTF_8))
  }

  private fun startScript(
    stateKey: String,
    functionCode: String,
    input: JSONObject,
  ): String {
    val key = JSONObject.quote(stateKey)
    val source = JSONObject.quote(functionCode)
    return """
      (() => {
        const key = $key;
        const source = $source;
        const input = ${input};
        const state = {
          state: 'pending',
          request: null,
          value: null,
          message: null,
          nextRequestId: 0,
          queue: [],
          resolvers: new Map()
        };
        window[key] = state;
        const exposeNextRequest = () => {
          if (state.state === 'done' || state.state === 'error') return;
          const next = state.queue.length > 0 ? state.queue[0] : null;
          state.request = next;
          state.state = next ? 'request' : 'pending';
        };
        state.deliver = (id, response) => {
          const next = state.queue.length > 0 ? state.queue[0] : null;
          if (!next || next.id !== id) throw new Error('host response does not match pending request');
          state.queue.shift();
          const resolver = state.resolvers.get(id);
          state.resolvers.delete(id);
          state.request = null;
          if (!resolver) throw new Error('host response resolver is missing');
          exposeNextRequest();
          resolver.resolve(response);
        };
        const api = Object.freeze({
          fetch(request) {
            if (!request || typeof request !== 'object') {
              return Promise.reject(new Error('api.fetch request must be an object'));
            }
            const id = state.nextRequestId++;
            return new Promise((resolve, reject) => {
              state.resolvers.set(id, { resolve, reject });
              state.queue.push({ id, request });
              exposeNextRequest();
            });
          }
        });
        Promise.resolve()
          .then(() => {
            const provider = eval('(' + source + ')');
            if (typeof provider !== 'function') throw new Error('provider code must be a function expression');
            return provider(input, api);
          })
          .then(value => {
            if (!value || typeof value !== 'object') throw new Error('provider result must be an object');
            state.state = 'done';
            state.request = null;
            state.value = value;
          })
          .catch(error => {
            state.state = 'error';
            state.request = null;
            state.message = String(error && error.message ? error.message : error);
          });
      })();
    """.trimIndent()
  }

  private fun resumeScript(stateKey: String, requestId: Int, response: JSONObject): String {
    val key = JSONObject.quote(stateKey)
    val responseJson = JSONObject.quote(response.toString())
    return """
      (() => {
        const state = window[$key];
        if (!state || typeof state.deliver !== 'function') {
          throw new Error('custom provider execution state is missing');
        }
        state.deliver($requestId, JSON.parse($responseJson));
        return true;
      })();
    """.trimIndent()
  }

  private fun pollScript(stateKey: String?): String {
    val key = stateKey?.let(JSONObject::quote)
    return if (key == null) {
      """
        (() => {
          const keys = Object.keys(window).filter(key => key.startsWith('__mosaicCustomVideoProvider_'));
          const state = keys.length > 0 ? window[keys[keys.length - 1]] : null;
          return JSON.stringify(state ? {
            state: state.state,
            request: state.request,
            value: state.value,
            message: state.message
          } : { state: 'pending' });
        })()
      """.trimIndent()
    } else {
      """
        (() => {
          const state = window[$key];
          return JSON.stringify(state ? {
            state: state.state,
            request: state.request,
            value: state.value,
            message: state.message
          } : { state: 'pending' });
        })()
      """.trimIndent()
    }
  }

  private fun parseStep(raw: String?): ScriptStep {
    val jsonString = JSONTokener(raw ?: "null").nextValue() as? String
      ?: throw IllegalStateException("カスタム動画プロバイダの実行状態を読み取れませんでした")
    val json = JSONObject(jsonString)
    return when (json.optString("state")) {
      "pending" -> ScriptStep.Pending
      "request" -> {
        val pending = json.optJSONObject("request")
          ?: throw IllegalStateException("カスタム動画プロバイダのrequestが不正です")
        val id = pending.optInt("id", -1)
        require(id >= 0) { "カスタム動画プロバイダのrequest IDが不正です" }
        ScriptStep.Request(
          id = id,
          request = pending.optJSONObject("request")
            ?: throw IllegalStateException("カスタム動画プロバイダのrequestが不正です"),
        )
      }
      "done" -> ScriptStep.Done(
        json.optJSONObject("value")
          ?: throw IllegalStateException("カスタム動画プロバイダの戻り値が不正です"),
      )
      "error" -> throw IllegalStateException(
        json.optString("message").takeIf(String::isNotBlank) ?: "カスタム動画プロバイダの実行に失敗しました",
      )
      else -> throw IllegalStateException("カスタム動画プロバイダの実行状態が不正です")
    }
  }

  private sealed interface ScriptStep {
    data object Pending : ScriptStep
    data class Request(val id: Int, val request: JSONObject) : ScriptStep
    data class Done(val value: JSONObject) : ScriptStep
  }

  companion object {
    internal fun buildHttpRequest(request: JSONObject): HttpRequest {
      val url = request.requiredString("url", MAX_URL_CHARS)
      requireHttpsUrl(url, "request URL")
      val method = request.optString("method", "GET")
        .uppercase(Locale.ROOT)
        .let { value ->
          runCatching { HttpMethod.valueOf(value) }
            .getOrElse { throw IllegalArgumentException("未対応のHTTP methodです") }
        }
      val headers = buildMap {
        val json = request.optJSONObject("headers") ?: JSONObject()
        val keys = json.keys()
        while (keys.hasNext()) {
          val name = keys.next()
          require(name.lowercase(Locale.ROOT) !in FORBIDDEN_CREDENTIAL_HEADERS) {
            "credential headerはカスタム動画プロバイダへ保存できません"
          }
          val value = json.optString(name)
          require(name.isNotBlank() && value.length <= MAX_HEADER_VALUE_CHARS) { "HTTP headerが不正です" }
          put(name, value)
        }
      }
      val body = request.optStringOrNull("body")?.toByteArray(Charsets.UTF_8)?.also {
        require(it.size <= MAX_REQUEST_BODY_BYTES) { "HTTP request bodyが大きすぎます" }
      }
      val contentType = request.optStringOrNull("contentType")?.also {
        require(it.length <= MAX_HEADER_VALUE_CHARS) { "Content-Typeが長すぎます" }
      }
      return HttpRequest(
        url = url,
        method = method,
        headers = headers,
        body = body,
        contentType = contentType,
        maxResponseBytes = MAX_RESPONSE_BYTES,
        maxErrorResponseBytes = MAX_RESPONSE_BYTES,
      )
    }

    internal fun parseFeedValue(value: JSONObject): VideoProviderFeed {
      val sourceId = value.requiredString("sourceId", MAX_ID_CHARS)
      val title = value.requiredString("title", MAX_TITLE_CHARS)
      val sourceUrl = value.requiredString("sourceUrl", MAX_URL_CHARS).also {
        requireSafeOutputUrl(it, "sourceUrl")
      }
      val videosJson = value.optJSONArray("videos")
        ?: throw IllegalArgumentException("カスタム動画プロバイダのvideosがありません")
      require(videosJson.length() <= MAX_VIDEOS) { "カスタム動画プロバイダの動画件数が上限を超えました" }
      val videos = buildList {
        for (index in 0 until videosJson.length()) {
          val item = videosJson.optJSONObject(index)
            ?: throw IllegalArgumentException("カスタム動画プロバイダの動画がobjectではありません")
          val url = item.requiredString("url", MAX_URL_CHARS).also {
            requireSafeOutputUrl(it, "video URL")
          }
          val thumbnailUrl = item.optStringOrNull("thumbnailUrl")?.also {
            requireSafeOutputUrl(it, "thumbnail URL")
          }
          val publishedAt = item.optLong("publishedAtEpochMillis", Long.MIN_VALUE)
          require(publishedAt >= 0L) { "publishedAtEpochMillisが不正です" }
          add(
            VideoProviderFeedItem(
              id = item.requiredString("id", MAX_ID_CHARS),
              title = item.requiredString("title", MAX_TITLE_CHARS),
              url = url,
              thumbnailUrl = thumbnailUrl,
              publishedAtEpochMillis = publishedAt,
            ),
          )
        }
      }
      return VideoProviderFeed(
        sourceId = sourceId,
        title = title,
        sourceUrl = sourceUrl,
        videos = videos,
      )
    }

    private const val PROFILE_NAME = "mosaic-video-custom-provider"
    private const val EXECUTION_TIMEOUT_MILLIS = 30_000L
    private const val POLL_DELAY_MILLIS = 50L
    private const val MAX_REQUESTS = 8
    private const val MAX_RESPONSE_BYTES = 4L * 1024 * 1024
    private const val MAX_REQUEST_BODY_BYTES = 512 * 1024
    private const val MAX_FUNCTION_CHARS = 128 * 1024
    private const val MAX_URL_CHARS = 8 * 1024
    private const val MAX_HEADER_VALUE_CHARS = 16 * 1024
    private const val MAX_ID_CHARS = 4 * 1024
    private const val MAX_TITLE_CHARS = 16 * 1024
    private const val MAX_VIDEOS = 500
    private val FORBIDDEN_CREDENTIAL_HEADERS = setOf(
      "authorization",
      "cookie",
      "proxy-authorization",
      "x-api-key",
      "api-key",
    )
  }
}

private class CustomProviderRendererLifecycle {
  @Volatile
  var failure: Throwable? = null
    private set

  var onFailure: ((Throwable) -> Unit)? = null

  fun fail(error: Throwable) {
    if (failure != null) return
    failure = error
    onFailure?.invoke(error)
  }
}

private fun JSONObject.requiredString(name: String, maxLength: Int): String =
  optString(name).trim().also {
    require(it.isNotEmpty()) { "$name がありません" }
    require(it.length <= maxLength) { "$name が長すぎます" }
  }

private fun JSONObject.optStringOrNull(name: String): String? =
  takeIf { has(name) && !isNull(name) }
    ?.optString(name)
    ?.takeIf(String::isNotBlank)

private fun requireHttpsUrl(value: String, label: String) {
  val uri = runCatching { URI(value) }
    .getOrElse { throw IllegalArgumentException("$label が不正です") }
  require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
    "$label はcredentialを含まないHTTPS URLである必要があります"
  }
}

private fun requireSafeOutputUrl(value: String, label: String) {
  val uri = runCatching { URI(value) }
    .getOrElse { throw IllegalArgumentException("$label が不正です") }
  require(
    (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true)) &&
      !uri.host.isNullOrBlank() &&
      uri.userInfo == null,
  ) {
    "$label はcredentialを含まないHTTP(S) URLである必要があります"
  }
}
