package dev.terashima.yomitorirss.feature.web.data

import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.rss.FeedRepository
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
import kotlinx.coroutines.runBlocking

class LanWebServer(
  articleRepository: ArticleRepository,
  bookmarkRepository: BookmarkRepository,
  feedRepository: FeedRepository,
  private val accessToken: String,
) : AutoCloseable {
  private val readModel = LanWebReadModel(articleRepository, bookmarkRepository, feedRepository)
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
          LanWebRenderer.renderError("アクセスできません", "アプリに表示されたアクセスURLを使用してください。"),
        )
        return
      }

      val extraHeaders = if (tokenFromQuery == accessToken) {
        mapOf("Set-Cookie" to "$COOKIE_NAME=$accessToken; Path=/; HttpOnly; SameSite=Strict")
      } else {
        emptyMap()
      }

      when (target.path.ifBlank { "/" }) {
        "/", "/index.html" -> {
          val page = runBlocking { readModel.loadHome(query["view"]) }
          writeResponse(
            client,
            200,
            "OK",
            "text/html; charset=utf-8",
            LanWebRenderer.renderHome(page),
            extraHeaders,
          )
        }
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
          LanWebRenderer.renderError("ページがありません", "指定されたページは見つかりませんでした。"),
          extraHeaders,
        )
      }
    }
  }

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
