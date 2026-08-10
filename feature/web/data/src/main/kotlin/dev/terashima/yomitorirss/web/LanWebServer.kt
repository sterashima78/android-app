package dev.terashima.yomitorirss.feature.web.data

import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.reddit.isRedditArticle
import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl
import dev.terashima.yomitorirss.feature.rss.Feed
import java.io.BufferedReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LanWebServer(
  private val database: YomitoriDatabase,
  private val accessToken: String,
) : AutoCloseable {
  private val running = AtomicBoolean(false)
  private val acceptExecutor: ExecutorService = Executors.newSingleThreadExecutor()
  private val requestExecutor: ExecutorService = Executors.newFixedThreadPool(MAX_CONNECTIONS)
  private var serverSocket: ServerSocket? = null

  fun start() {
    if (!running.compareAndSet(false, true)) return
    val socket = ServerSocket(PORT).also {
      it.reuseAddress = true
      serverSocket = it
    }
    acceptExecutor.execute {
      while (running.get()) {
        try {
          val client = socket.accept()
          requestExecutor.execute { handle(client) }
        } catch (error: SocketException) {
          if (running.get()) throw error
        }
      }
    }
  }

  override fun close() {
    if (!running.compareAndSet(true, false)) return
    runCatching { serverSocket?.close() }
    acceptExecutor.shutdownNow()
    requestExecutor.shutdownNow()
  }

  private fun handle(socket: Socket) {
    socket.use { client ->
      client.soTimeout = REQUEST_TIMEOUT_MS
      if (!client.inetAddress.isAllowedClient()) {
        writeResponse(client, 403, "Forbidden", "text/plain; charset=utf-8", "同一ネットワークからのみアクセスできます。")
        return
      }

      val reader = client.getInputStream().bufferedReader(StandardCharsets.US_ASCII)
      val requestLine = reader.readLine()?.trim().orEmpty()
      if (requestLine.isBlank()) return
      val parts = requestLine.split(' ')
      if (parts.size != 3 || parts[0] != "GET") {
        writeResponse(client, 405, "Method Not Allowed", "text/plain; charset=utf-8", "GETのみ対応しています。")
        return
      }

      val headers = readHeaders(reader)
      val target = runCatching { URI(parts[1]) }.getOrNull()
      if (target == null) {
        writeResponse(client, 400, "Bad Request", "text/plain; charset=utf-8", "不正なリクエストです。")
        return
      }

      val query = parseQuery(target.rawQuery)
      val tokenFromQuery = query["token"]
      val tokenFromCookie = parseCookie(headers["cookie"], COOKIE_NAME)
      val authenticated = tokenFromQuery == accessToken || tokenFromCookie == accessToken
      if (!authenticated) {
        writeResponse(
          client,
          403,
          "Forbidden",
          "text/html; charset=utf-8",
          errorPage("アクセスできません", "アプリに表示されたアクセスURLを使用してください。"),
        )
        return
      }

      val extraHeaders = if (tokenFromQuery == accessToken) {
        mapOf("Set-Cookie" to "$COOKIE_NAME=$accessToken; Path=/; HttpOnly; SameSite=Strict")
      } else {
        emptyMap()
      }

      when (target.path.ifBlank { "/" }) {
        "/", "/index.html" -> writeResponse(
          client,
          200,
          "OK",
          "text/html; charset=utf-8",
          renderHome(query["view"]),
          extraHeaders,
        )
        "/robots.txt" -> writeResponse(
          client,
          200,
          "OK",
          "text/plain; charset=utf-8",
          "User-agent: *\nDisallow: /\n",
          extraHeaders,
        )
        else -> writeResponse(
          client,
          404,
          "Not Found",
          "text/html; charset=utf-8",
          errorPage("ページがありません", "指定されたページは見つかりませんでした。"),
          extraHeaders,
        )
      }
    }
  }

  private fun renderHome(requestedView: String?): String {
    val view = requestedView?.takeIf { it in VIEWS } ?: VIEW_UNREAD
    val title: String
    val body: String
    when (view) {
      VIEW_REDDIT -> {
        title = "Reddit"
        body = renderArticles(
          database.listUnreadArticles().filter(Article::isRedditArticle),
          "Redditの未読はありません。",
        )
      }
      VIEW_SAVED -> {
        title = "ブックマーク"
        body = renderBookmarkedArticles(database.listSavedArticles(), "ブックマークはありません。")
      }
      VIEW_READ_LATER -> {
        title = "あとで読む"
        body = renderBookmarkedArticles(database.listReadLaterArticles(), "あとで読む記事はありません。")
      }
      VIEW_FEEDS -> {
        title = "RSSフィード"
        body = renderFeeds(database.listFeeds().filterNot { isRedditFeedUrl(it.feedUrl) })
      }
      else -> {
        title = "RSS未読"
        body = renderArticles(
          database.listUnreadArticles().filterNot(Article::isRedditArticle),
          "RSSの未読記事はありません。",
        )
      }
    }

    return """<!doctype html>
<html lang="ja">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="dark">
<title>${escapeHtml(title)} - Yomitori</title>
<style>
:root{font-family:system-ui,-apple-system,sans-serif;color-scheme:dark;background:#111318;color:#e3e2e9}body{margin:0}header{position:sticky;top:0;background:#1b1b21;border-bottom:1px solid #45464f;padding:16px;z-index:1}h1{font-size:1.25rem;margin:0 0 12px}.nav{display:flex;gap:8px;overflow-x:auto}.nav a{white-space:nowrap;color:#c6c5d0;text-decoration:none;padding:8px 12px;border-radius:999px;background:#292930}.nav a.current{background:#d0bcff;color:#381e72}main{max-width:900px;margin:0 auto;padding:16px}.notice{font-size:.85rem;color:#c6c5d0;margin:0 0 16px}.card{display:block;color:inherit;text-decoration:none;background:#1b1b21;border:1px solid #45464f;border-radius:14px;padding:16px;margin-bottom:12px}.card:hover{border-color:#d0bcff}.title{font-size:1rem;font-weight:650;line-height:1.5;margin-bottom:8px}.meta{font-size:.8rem;color:#c6c5d0;line-height:1.5}.tags{display:flex;gap:6px;flex-wrap:wrap;margin-top:10px}.tag{background:#34313d;border-radius:999px;padding:3px 8px;font-size:.75rem}.empty{padding:40px 16px;text-align:center;color:#c6c5d0}.feed-url{overflow-wrap:anywhere}footer{text-align:center;color:#8f9099;font-size:.75rem;padding:20px}</style>
</head>
<body>
<header><h1>Yomitori</h1>${renderNavigation(view)}</header>
<main><p class="notice">同じネットワーク内の端末から、読み取り専用で表示しています。更新や編集はAndroidアプリで行ってください。</p><h2>${escapeHtml(title)}</h2>$body</main>
<footer>Yomitori LAN Web Server</footer>
</body>
</html>"""
  }

  private fun renderNavigation(current: String): String = buildString {
    append("<nav class=\"nav\">")
    listOf(
      VIEW_UNREAD to "RSS未読",
      VIEW_REDDIT to "Reddit",
      VIEW_SAVED to "ブックマーク",
      VIEW_READ_LATER to "あとで読む",
      VIEW_FEEDS to "RSSフィード",
    ).forEach { (view, label) ->
      val css = if (view == current) " class=\"current\"" else ""
      append("<a$css href=\"/?view=${escapeHtml(view)}\">${escapeHtml(label)}</a>")
    }
    append("</nav>")
  }

  private fun renderArticles(articles: List<Article>, emptyText: String): String {
    if (articles.isEmpty()) return "<div class=\"empty\">${escapeHtml(emptyText)}</div>"
    return articles.joinToString(separator = "") { article -> renderArticle(article, emptyList()) }
  }

  private fun renderBookmarkedArticles(articles: List<BookmarkedArticle>, emptyText: String): String {
    if (articles.isEmpty()) return "<div class=\"empty\">${escapeHtml(emptyText)}</div>"
    return articles.joinToString(separator = "") { bookmark ->
      renderArticle(bookmark.article, bookmark.tags.map { it.name })
    }
  }

  private fun renderArticle(article: Article, tagNames: List<String>): String {
    val tags = if (tagNames.isEmpty()) "" else tagNames.joinToString(
      prefix = "<div class=\"tags\">",
      postfix = "</div>",
      separator = "",
    ) { tagName -> "<span class=\"tag\">${escapeHtml(tagName)}</span>" }
    return """<a class="card" href="${escapeAttribute(article.url)}" target="_blank" rel="noopener noreferrer">
<div class="title">${escapeHtml(article.title)}</div>
<div class="meta">${escapeHtml(article.sourceTitle)}<br>${escapeHtml(article.publishedAt)}</div>$tags
</a>"""
  }

  private fun renderFeeds(feeds: List<Feed>): String {
    if (feeds.isEmpty()) return "<div class=\"empty\">登録済みRSSフィードはありません。</div>"
    return feeds.joinToString(separator = "") { feed ->
      val href = feed.siteUrl ?: feed.feedUrl
      """<a class="card" href="${escapeAttribute(href)}" target="_blank" rel="noopener noreferrer">
<div class="title">${escapeHtml(feed.title)}</div>
<div class="meta feed-url">${escapeHtml(feed.feedUrl)}</div>
</a>"""
    }
  }

  private fun errorPage(title: String, message: String): String = """<!doctype html>
<html lang="ja"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="dark"><title>${escapeHtml(title)}</title></head>
<body style="font-family:system-ui;background:#111318;color:#e3e2e9;padding:32px"><h1>${escapeHtml(title)}</h1><p>${escapeHtml(message)}</p></body></html>"""

  private fun writeResponse(
    socket: Socket,
    status: Int,
    reason: String,
    contentType: String,
    body: String,
    extraHeaders: Map<String, String> = emptyMap(),
  ) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    socket.getOutputStream().buffered().use { output ->
      val headers = buildString {
        append("HTTP/1.1 $status $reason\r\n")
        append("Content-Type: $contentType\r\n")
        append("Content-Length: ${bytes.size}\r\n")
        append("Connection: close\r\n")
        append("Cache-Control: no-store\r\n")
        append("X-Content-Type-Options: nosniff\r\n")
        append("Referrer-Policy: no-referrer\r\n")
        append("Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'\r\n")
        extraHeaders.forEach { (name, value) -> append("$name: $value\r\n") }
        append("\r\n")
      }
      output.write(headers.toByteArray(StandardCharsets.US_ASCII))
      output.write(bytes)
      output.flush()
    }
  }

  private fun readHeaders(reader: BufferedReader): Map<String, String> = buildMap {
    while (true) {
      val line = reader.readLine() ?: break
      if (line.isEmpty()) break
      val separator = line.indexOf(':')
      if (separator <= 0) continue
      put(line.substring(0, separator).trim().lowercase(), line.substring(separator + 1).trim())
    }
  }

  companion object {
    const val PORT = 8765
    private const val MAX_CONNECTIONS = 8
    private const val REQUEST_TIMEOUT_MS = 10_000
    private const val COOKIE_NAME = "yomitori_lan_token"
    private const val VIEW_UNREAD = "unread"
    private const val VIEW_REDDIT = "reddit"
    private const val VIEW_SAVED = "saved"
    private const val VIEW_READ_LATER = "read-later"
    private const val VIEW_FEEDS = "feeds"
    private val VIEWS = setOf(VIEW_UNREAD, VIEW_REDDIT, VIEW_SAVED, VIEW_READ_LATER, VIEW_FEEDS)
  }
}

private fun InetAddress.isAllowedClient(): Boolean = isLoopbackAddress || isSiteLocalAddress || isLinkLocalAddress

private fun parseQuery(rawQuery: String?): Map<String, String> {
  if (rawQuery.isNullOrBlank()) return emptyMap()
  return rawQuery.split('&').mapNotNull { part ->
    val separator = part.indexOf('=')
    val rawName = if (separator >= 0) part.substring(0, separator) else part
    val rawValue = if (separator >= 0) part.substring(separator + 1) else ""
    runCatching {
      URLDecoder.decode(rawName, StandardCharsets.UTF_8.name()) to
        URLDecoder.decode(rawValue, StandardCharsets.UTF_8.name())
    }.getOrNull()
  }.toMap()
}

private fun parseCookie(header: String?, name: String): String? = header
  ?.split(';')
  ?.map(String::trim)
  ?.firstOrNull { it.startsWith("$name=") }
  ?.substringAfter('=')

internal fun escapeHtml(value: String): String = buildString(value.length) {
  value.forEach { character ->
    append(
      when (character) {
        '&' -> "&amp;"
        '<' -> "&lt;"
        '>' -> "&gt;"
        '"' -> "&quot;"
        '\'' -> "&#39;"
        else -> character
      },
    )
  }
}

private fun escapeAttribute(value: String): String = escapeHtml(value)
