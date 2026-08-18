package dev.terashima.yomitorirss.feature.rss.data.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.terashima.yomitorirss.feature.rss.FeedInspection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Treats a MangaONE chapter URL as a synthetic feed of currently free chapters. */
internal class MangaOneFeedClient(
  private val renderer: MangaOnePageRenderer,
  private val now: () -> Instant = Instant::now,
) {
  constructor(context: Context) : this(WebViewMangaOnePageRenderer(context.applicationContext))

  fun supports(url: String): Boolean = companionSupports(url)

  fun canonicalWorkUrl(url: String): String {
    require(supports(url)) { "マンガワンの作品第1話URLを入力してください" }
    return "https://$MANGA_ONE_HOST/manga/${mangaId(url)}/chapter/${chapterKey(url)}"
  }

  suspend fun inspect(url: String): FeedInspection {
    val workUrl = canonicalWorkUrl(url)
    validate(renderer.render(renderUrl(workUrl), mangaId(workUrl)))
    return FeedInspection(directFeedUrl = workUrl)
  }

  suspend fun fetchFeed(
    url: String,
    etag: String? = null,
    lastModified: String? = null,
  ): FetchResult {
    val workUrl = canonicalWorkUrl(url)
    val rendered = renderer.render(renderUrl(workUrl), mangaId(workUrl))
    validate(rendered)
    val discoveredAt = now().toString()
    val articles = rendered.chapters
      .asSequence()
      .filter { it.label == FREE_LABEL }
      .filter { it.title.isNotBlank() && it.url.isNotBlank() }
      .distinctBy(MangaOneRenderedChapter::url)
      .map { chapter ->
        ParsedArticle(
          externalId = chapter.url,
          identityKey = identityKey(chapter.url),
          url = chapter.url,
          title = chapter.title,
          publishedAt = parsePublishedAt(chapter.dateText, discoveredAt),
        )
      }
      .toList()

    return FetchResult(
      feed = ParsedFeed(
        title = rendered.title,
        feedUrl = workUrl,
        siteUrl = workUrl,
        articles = articles,
      ),
      etag = null,
      lastModified = null,
    )
  }

  private fun renderUrl(workUrl: String): String = "$workUrl?$CHAPTER_LIST_QUERY"

  private fun validate(page: MangaOneRenderedPage) {
    require(page.title.isNotBlank()) { "マンガワンの作品名を取得できませんでした" }
    require(page.chapters.isNotEmpty()) { "マンガワンの話一覧を取得できませんでした" }
  }

  private fun parsePublishedAt(value: String, fallback: String): String {
    val match = FULL_DATE_PATTERN.find(value) ?: return fallback
    return runCatching {
      LocalDate.of(
        match.groupValues[1].toInt(),
        match.groupValues[2].toInt(),
        match.groupValues[3].toInt(),
      ).atStartOfDay(TOKYO).toInstant().toString()
    }.getOrDefault(fallback)
  }

  private fun identityKey(url: String): String = MessageDigest.getInstance("SHA-256")
    .digest(url.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

  private fun pathSegments(url: String): List<String> = URI(url).path
    .trim('/')
    .split('/')

  private fun mangaId(url: String): String = pathSegments(url).getOrNull(1).orEmpty()

  private fun chapterKey(url: String): String = pathSegments(url).getOrNull(3).orEmpty()

  companion object {
    private const val MANGA_ONE_HOST = "manga-one.com"
    private const val WWW_MANGA_ONE_HOST = "www.manga-one.com"
    private const val FREE_LABEL = "無料"
    private const val CHAPTER_LIST_QUERY = "type=chapter&sort_type=desc&page=1&limit=10"
    private val FULL_DATE_PATTERN = Regex("(20\\d{2})(?:/|\\.|-|年)(\\d{1,2})(?:/|\\.|-|月)(\\d{1,2})日?")
    private val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")

    fun companionSupports(url: String): Boolean = runCatching {
      val uri = URI(url)
      val scheme = uri.scheme?.lowercase(Locale.ROOT)
      val host = uri.host?.lowercase(Locale.ROOT)
      if (scheme !in setOf("http", "https")) return@runCatching false
      if (host != MANGA_ONE_HOST && host != WWW_MANGA_ONE_HOST) return@runCatching false
      val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotBlank)
      val chapterKey = segments.getOrNull(3).orEmpty()
      segments.size == 4 &&
        segments[0] == "manga" &&
        segments[1].all(Char::isDigit) &&
        segments[1].isNotEmpty() &&
        segments[2] == "chapter" &&
        (chapterKey == "first" || (chapterKey.isNotEmpty() && chapterKey.all(Char::isDigit)))
    }.getOrDefault(false)
  }
}

internal data class MangaOneRenderedPage(
  val title: String,
  val chapters: List<MangaOneRenderedChapter>,
  val renderStatus: String = RENDER_STATUS_READY,
) {
  companion object {
    const val RENDER_STATUS_READY = "ready"
    const val RENDER_STATUS_DOCUMENT_LOADING = "document-loading"
    const val RENDER_STATUS_SERVICE_ERROR = "service-error"
    const val RENDER_STATUS_CHAPTER_LIST_MISSING = "chapter-list-missing"
    const val RENDER_STATUS_CHAPTER_LINKS_MISSING = "chapter-links-missing"
  }
}

internal data class MangaOneRenderedChapter(
  val title: String,
  val url: String,
  val label: String,
  val dateText: String,
)

internal fun interface MangaOnePageRenderer {
  suspend fun render(url: String, mangaId: String): MangaOneRenderedPage
}

internal fun chromeLikeUserAgent(defaultUserAgent: String): String = defaultUserAgent
  .replace("; wv", "")
  .replace("Version/4.0 ", "")
  .replace(Regex("\\s+"), " ")
  .trim()

private class WebViewMangaOnePageRenderer(
  private val context: Context,
) : MangaOnePageRenderer {
  @SuppressLint("SetJavaScriptEnabled")
  override suspend fun render(url: String, mangaId: String): MangaOneRenderedPage =
    withContext(Dispatchers.Main.immediate) {
      suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        val webView = WebView(context)
        var finished = false
        var lastSignature: String? = null
        var stableCount = 0
        var lastRenderStatus = MangaOneRenderedPage.RENDER_STATUS_DOCUMENT_LOADING
        val deadline = SystemClock.uptimeMillis() + RENDER_TIMEOUT_MS

        fun cleanup() {
          handler.removeCallbacksAndMessages(null)
          webView.stopLoading()
          webView.webViewClient = WebViewClient()
          webView.removeAllViews()
          webView.destroy()
        }

        fun fail(error: Throwable) {
          if (finished) return
          finished = true
          cleanup()
          if (continuation.isActive) continuation.resumeWithException(error)
        }

        fun succeed(page: MangaOneRenderedPage) {
          if (finished) return
          finished = true
          cleanup()
          if (continuation.isActive) continuation.resume(page)
        }

        lateinit var probe: Runnable
        probe = Runnable {
          if (finished) return@Runnable
          if (SystemClock.uptimeMillis() >= deadline) {
            fail(IllegalStateException(timeoutMessage(lastRenderStatus)))
            return@Runnable
          }
          webView.evaluateJavascript(extractionScript(mangaId)) { raw ->
            if (finished) return@evaluateJavascript
            val page = runCatching { decodeResult(raw) }.getOrNull()
            if (page == null) {
              lastRenderStatus = MangaOneRenderedPage.RENDER_STATUS_DOCUMENT_LOADING
              handler.postDelayed(probe, PROBE_INTERVAL_MS)
              return@evaluateJavascript
            }
            lastRenderStatus = page.renderStatus
            if (page.chapters.isEmpty()) {
              handler.postDelayed(probe, PROBE_INTERVAL_MS)
              return@evaluateJavascript
            }
            val signature = page.chapters.joinToString("\n") { "${it.url}|${it.label}|${it.title}|${it.dateText}" }
            stableCount = if (signature == lastSignature) stableCount + 1 else 0
            lastSignature = signature
            if (stableCount >= REQUIRED_STABLE_PROBES) {
              succeed(page)
            } else {
              handler.postDelayed(probe, PROBE_INTERVAL_MS)
            }
          }
        }

        webView.settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          allowFileAccess = false
          allowContentAccess = false
          mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
          userAgentString = chromeLikeUserAgent(WebSettings.getDefaultUserAgent(context))
          useWideViewPort = true
          loadWithOverviewMode = true
        }
        val widthSpec = View.MeasureSpec.makeMeasureSpec(VIRTUAL_VIEWPORT_WIDTH_PX, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(VIRTUAL_VIEWPORT_HEIGHT_PX, View.MeasureSpec.EXACTLY)
        webView.measure(widthSpec, heightSpec)
        webView.layout(0, 0, VIRTUAL_VIEWPORT_WIDTH_PX, VIRTUAL_VIEWPORT_HEIGHT_PX)

        webView.webViewClient = object : WebViewClient() {
          override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            request.isForMainFrame && !isAllowedHost(request.url)

          override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
              fail(IllegalStateException("マンガワンの読み込みに失敗しました: ${error.description}"))
            }
          }

          override fun onReceivedHttpError(
            view: WebView,
            request: WebResourceRequest,
            errorResponse: WebResourceResponse,
          ) {
            if (request.isForMainFrame && errorResponse.statusCode >= 400) {
              fail(IllegalStateException("マンガワンの読み込みに失敗しました: HTTP ${errorResponse.statusCode}"))
            }
          }
        }

        continuation.invokeOnCancellation {
          if (Looper.myLooper() == Looper.getMainLooper()) {
            if (!finished) {
              finished = true
              cleanup()
            }
          } else {
            handler.post {
              if (!finished) {
                finished = true
                cleanup()
              }
            }
          }
        }

        webView.loadUrl(url)
        handler.postDelayed(probe, PROBE_INTERVAL_MS)
      }
    }

  private fun isAllowedHost(uri: Uri): Boolean {
    val host = uri.host?.lowercase(Locale.ROOT)
    return host == "manga-one.com" || host == "www.manga-one.com"
  }

  private fun timeoutMessage(status: String): String = when (status) {
    MangaOneRenderedPage.RENDER_STATUS_SERVICE_ERROR ->
      "マンガワン側でページを表示できない状態が続いたため、話一覧を取得できませんでした"
    MangaOneRenderedPage.RENDER_STATUS_CHAPTER_LIST_MISSING ->
      "マンガワンのページは読み込めましたが、話一覧が表示されませんでした"
    MangaOneRenderedPage.RENDER_STATUS_CHAPTER_LINKS_MISSING ->
      "マンガワンの話一覧は表示されましたが、話リンクを取得できませんでした"
    else -> "マンガワンのページ読み込みが完了しませんでした"
  }

  private fun decodeResult(raw: String): MangaOneRenderedPage? {
    if (raw == "null" || raw == "undefined") return null
    val encoded = JSONArray("[$raw]").optString(0)
    if (encoded.isBlank()) return null
    val json = JSONObject(encoded)
    val chaptersJson = json.getJSONArray("chapters")
    val chapters = buildList {
      for (index in 0 until chaptersJson.length()) {
        val chapter = chaptersJson.getJSONObject(index)
        add(
          MangaOneRenderedChapter(
            title = chapter.optString("title").trim(),
            url = chapter.optString("url").trim(),
            label = chapter.optString("label").trim(),
            dateText = chapter.optString("dateText").trim(),
          ),
        )
      }
    }
    return MangaOneRenderedPage(
      title = json.optString("title").trim(),
      chapters = chapters,
      renderStatus = json.optString("renderStatus", MangaOneRenderedPage.RENDER_STATUS_READY),
    )
  }

  private fun extractionScript(mangaId: String): String = """
    (() => {
      const title = (document.title || '')
        .replace(/\s*\|\s*マンガワン\s*$/, '')
        .trim();
      const bodyText = (document.body && document.body.innerText || '').replace(/\s+/g, ' ');
      if (bodyText.includes('現在、マンガワンは一時的にご利用いただけません')) {
        return JSON.stringify({ title, chapters: [], renderStatus: 'service-error' });
      }
      window.scrollTo(0, document.body ? document.body.scrollHeight : 0);
      const root = document.querySelector('#chapterList');
      if (!root) {
        return JSON.stringify({ title, chapters: [], renderStatus: 'chapter-list-missing' });
      }
      root.scrollTop = root.scrollHeight;
      const prefix = '/manga/$mangaId/chapter/';
      const isChapterLink = (element) => {
        if (!(element instanceof HTMLAnchorElement)) return false;
        try {
          const target = new URL(element.href, location.href);
          return target.origin === location.origin && target.pathname.startsWith(prefix);
        } catch (_) {
          return false;
        }
      };
      const links = Array.from(root.querySelectorAll('a[href]')).filter(isChapterLink);
      if (links.length === 0) {
        return JSON.stringify({ title, chapters: [], renderStatus: 'chapter-links-missing' });
      }
      const chapters = [];
      for (const link of links) {
        let node = link;
        let row = link;
        while (node && node !== root) {
          let count = node.matches && node.matches('a[href]') && isChapterLink(node) ? 1 : 0;
          count += Array.from(node.querySelectorAll('a[href]')).filter(isChapterLink).length;
          if (count !== 1) break;
          row = node;
          node = node.parentElement;
        }
        const spanTexts = Array.from(row.querySelectorAll('span'))
          .map((span) => (span.textContent || '').trim())
          .filter(Boolean);
        const label = spanTexts.find((text) => text === '無料')
          || spanTexts.find((text) => text.startsWith('毎日無料'))
          || spanTexts.find((text) => text === '先読み')
          || '';
        const titleNode = row.querySelector('p[class*="line-clamp-1"]');
        const chapterTitle = ((titleNode && titleNode.textContent) || link.textContent || '')
          .replace(/\s+/g, ' ')
          .trim();
        const text = (row.textContent || '').replace(/\s+/g, ' ');
        const dateMatch = text.match(/20\d{2}(?:\/|\.|-|年)\d{1,2}(?:\/|\.|-|月)\d{1,2}日?/);
        chapters.push({
          title: chapterTitle,
          url: new URL(link.href, location.href).href,
          label,
          dateText: dateMatch ? dateMatch[0] : '',
        });
      }
      return JSON.stringify({ title, chapters, renderStatus: 'ready' });
    })();
  """.trimIndent()

  private companion object {
    const val RENDER_TIMEOUT_MS = 30_000L
    const val PROBE_INTERVAL_MS = 500L
    const val REQUIRED_STABLE_PROBES = 4
    const val VIRTUAL_VIEWPORT_WIDTH_PX = 1080
    const val VIRTUAL_VIEWPORT_HEIGHT_PX = 2400
  }
}
