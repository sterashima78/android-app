package dev.terashima.yomitorirss.feature.rss.data.network

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.rss.FeedCandidate
import dev.terashima.yomitorirss.feature.rss.FeedInspection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI
import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

internal class FeedClient(
  private val client: HttpClient = HttpClient.create(),
) {
  suspend fun inspect(input: String): FeedInspection {
    val url = normalizeInputUrl(input)
    val response = client.execute(request(url))
    response.requireSuccess()
    val finalUrl = response.finalUrl
    val text = decode(response.body, response.header("Content-Type"))
    return if (looksLikeFeed(text, response.header("Content-Type"))) {
      FeedInspection(directFeedUrl = parseFeed(text, finalUrl).feedUrl)
    } else {
      val document = Jsoup.parse(text, finalUrl)
      val candidates = document.select("link[rel~=alternate][href]")
        .mapNotNull { link ->
          val type = link.attr("type").lowercase(Locale.ROOT)
          if (type.isNotBlank() && type !in FEED_CONTENT_TYPES) return@mapNotNull null
          val absolute = link.absUrl("href").ifBlank { resolveUrl(finalUrl, link.attr("href")) }
          absolute.takeIf(String::isNotBlank)?.let {
            FeedCandidate(link.attr("title").ifBlank { document.title().ifBlank { it } }, it)
          }
        }
        .distinctBy(FeedCandidate::url)
      if (candidates.isEmpty()) error("RSSまたはAtomフィードを検出できませんでした")
      FeedInspection(candidates = candidates)
    }
  }

  suspend fun fetchFeed(url: String, etag: String? = null, lastModified: String? = null): FetchResult {
    val response = client.execute(
      request(
        normalizeInputUrl(url),
        buildMap {
          etag?.let { put("If-None-Match", it) }
          lastModified?.let { put("If-Modified-Since", it) }
        },
      ),
    )
    if (response.statusCode == 304) return FetchResult(null, etag, lastModified, notModified = true)
    response.requireSuccess()
    return FetchResult(
      feed = parseFeed(decode(response.body, response.header("Content-Type")), response.finalUrl),
      etag = response.header("ETag"),
      lastModified = response.header("Last-Modified"),
    )
  }

  internal fun parseFeed(xml: String, feedUrl: String): ParsedFeed {
    val document = Jsoup.parse(xml, feedUrl, Parser.xmlParser())
    return if (document.allElements.any { it.localNameEquals("feed") }) {
      parseAtom(document, feedUrl)
    } else {
      parseRss(document, feedUrl)
    }
  }

  private fun parseAtom(document: Document, feedUrl: String): ParsedFeed {
    val root = document.allElements.firstOrNull { it.localNameEquals("feed") }
      ?: error("Atomフィードを解析できませんでした")
    val title = root.directChildText("title").repairText().ifBlank { feedUrl }
    val siteUrl = root.directChildren("link")
      .firstOrNull { it.attr("rel").isBlank() || it.attr("rel") == "alternate" }
      ?.attr("href")
      ?.let { resolveUrl(feedUrl, it) }
    val articles = root.directChildren("entry").mapNotNull { entry ->
      val articleTitle = entry.directChildText("title").repairText().trim()
      val linkElement = entry.directChildren("link")
        .firstOrNull { it.attr("rel").isBlank() || it.attr("rel") == "alternate" }
        ?: entry.directChildren("link").firstOrNull()
      val url = linkElement?.attr("href")?.let { resolveUrl(feedUrl, it) }.orEmpty()
      if (articleTitle.isBlank() || url.isBlank()) return@mapNotNull null
      val externalId = entry.directChildText("id").trim().ifBlank { null }
      val published = parseDate(entry.directChildText("published").ifBlank { entry.directChildText("updated") })
      ParsedArticle(externalId, identityKey(externalId, url, articleTitle, published), url, articleTitle, published)
    }
    return ParsedFeed(title, feedUrl, siteUrl, articles)
  }

  private fun parseRss(document: Document, feedUrl: String): ParsedFeed {
    val channel = document.allElements.firstOrNull { it.localNameEquals("channel") }
    val root = channel ?: document
    val title = root.directChildText("title").repairText().ifBlank { feedUrl }
    val siteUrl = root.directChildText("link").takeIf(String::isNotBlank)?.let { resolveUrl(feedUrl, it) }
    val articles = document.allElements.filter { it.localNameEquals("item") }.mapNotNull { item ->
      val articleTitle = item.directChildText("title").repairText().trim()
      val link = item.directChildText("link").trim()
      val guid = item.directChildText("guid").trim().ifBlank { null }
      val url = when {
        link.isNotBlank() -> resolveUrl(feedUrl, link)
        guid?.startsWith("http://") == true || guid?.startsWith("https://") == true -> guid
        else -> ""
      }
      if (articleTitle.isBlank() || url.isBlank()) return@mapNotNull null
      val published = parseDate(
        item.directChildText("pubDate")
          .ifBlank { item.directChildText("date") }
          .ifBlank { item.directChildText("updated") },
      )
      ParsedArticle(guid, identityKey(guid, url, articleTitle, published), url, articleTitle, published)
    }
    if (articles.isEmpty() && title == feedUrl) error("RSSフィードを解析できませんでした")
    return ParsedFeed(title, feedUrl, siteUrl, articles)
  }

  private fun request(url: String, additionalHeaders: Map<String, String> = emptyMap()): HttpRequest = HttpRequest(
    url = url,
    headers = mapOf(
      "Accept" to "application/atom+xml, application/rss+xml, application/rdf+xml, application/xml, text/xml, text/html;q=0.8, */*;q=0.5",
    ) + additionalHeaders,
    maxResponseBytes = 4L * 1024 * 1024,
  )

  private fun HttpResponse.requireSuccess() {
    if (!isSuccessful) error("HTTP $statusCode: $reasonPhrase")
  }

  private fun decode(bytes: ByteArray, contentType: String?): String {
    val headerCharset = contentType
      ?.substringAfter("charset=", "")
      ?.substringBefore(';')
      ?.trim(' ', '"', '\'')
      ?.takeIf(String::isNotBlank)
    val declaration = bytes.take(256).toByteArray().toString(Charsets.US_ASCII)
      .let { XML_ENCODING.find(it)?.groupValues?.getOrNull(1) }
    val charset = sequenceOf(headerCharset, declaration, "UTF-8")
      .mapNotNull { name -> runCatching { Charset.forName(name) }.getOrNull() }
      .first()
    return bytes.toString(charset).removePrefix("\uFEFF")
  }

  private fun looksLikeFeed(text: String, contentType: String?): Boolean {
    val type = contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
    if (type in FEED_CONTENT_TYPES) return true
    val beginning = text.take(1_024).lowercase(Locale.ROOT)
    return "<rss" in beginning || "<feed" in beginning || "<rdf:rdf" in beginning
  }

  internal fun normalizeInputUrl(input: String): String {
    val trimmed = input.trim()
    require(trimmed.isNotBlank()) { "URLを入力してください" }
    val candidate = when {
      trimmed.startsWith("https://", ignoreCase = true) -> trimmed
      trimmed.startsWith("http://", ignoreCase = true) -> "https://${trimmed.substring(7)}"
      else -> "https://$trimmed"
    }
    val uri = URI(candidate)
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) { "URLが正しくありません" }
    return uri.normalize().toString()
  }

  private fun resolveUrl(base: String, value: String): String = runCatching {
    URI(base).resolve(value.trim()).normalize().toString()
  }.getOrDefault(value.trim())

  private fun identityKey(externalId: String?, url: String, title: String, published: String): String {
    val source = externalId?.takeIf(String::isNotBlank) ?: url.ifBlank { "$title|$published" }
    return MessageDigest.getInstance("SHA-256")
      .digest(source.toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
  }

  private fun parseDate(value: String): String {
    val text = value.trim()
    if (text.isBlank()) return nowIso()
    val parsers = listOf<(String) -> Instant>(
      { Instant.parse(it) },
      { OffsetDateTime.parse(it).toInstant() },
      { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() },
      { ZonedDateTime.parse(it).toInstant() },
      { LocalDateTime.parse(it).atZone(ZoneId.systemDefault()).toInstant() },
    )
    parsers.forEach { parser ->
      try {
        return parser(text).toString()
      } catch (_: DateTimeParseException) {
        // Try the next common RSS/Atom date format.
      }
    }
    return nowIso()
  }

  private fun String.repairText(): String {
    val suspicious = count { it == 'Ã' || it == 'Â' || it == 'ã' }
    if (suspicious < 2) return this
    return runCatching { toByteArray(Charsets.ISO_8859_1).toString(Charsets.UTF_8) }.getOrDefault(this)
  }

  private companion object {
    val XML_ENCODING = Regex("encoding\\s*=\\s*[\"']([^\"']+)", RegexOption.IGNORE_CASE)
    val FEED_CONTENT_TYPES = setOf(
      "application/atom+xml",
      "application/rss+xml",
      "application/rdf+xml",
      "application/xml",
      "text/xml",
    )
  }
}

private fun Element.localNameEquals(name: String): Boolean =
  tagName().substringAfterLast(':').equals(name, ignoreCase = true)

private fun Element.directChildren(name: String): List<Element> =
  children().filter { it.localNameEquals(name) }

private fun Element.directChildText(name: String): String =
  directChildren(name).firstOrNull()?.text().orEmpty()

private fun nowIso(): String = Instant.now().toString()
