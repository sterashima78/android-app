package dev.terashima.yomitorirss.feature.rss.data.network

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.rss.FeedInspection
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Treats a Yanmaga Web comic work page as a synthetic RSS source.
 *
 * The selectors intentionally mirror the public shishi/rss_generator implementation so the
 * Android app can subscribe to arbitrary work pages without depending on an external feed host.
 */
internal class YanmagaFeedClient(
  private val client: HttpClient = HttpClient.create(),
) {
  fun supports(url: String): Boolean = runCatching {
    val uri = URI(url)
    val host = uri.host?.lowercase(Locale.ROOT)
    if (host != YANMAGA_HOST && host != WWW_YANMAGA_HOST) return@runCatching false
    val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotBlank)
    segments.size == 2 && segments[0] == "comics" && segments[1] != "series"
  }.getOrDefault(false)

  fun canonicalWorkUrl(url: String): String {
    require(supports(url)) { "ヤンマガWebの作品URLを入力してください" }
    val uri = URI(url)
    val rawPath = uri.rawPath.orEmpty().trimEnd('/')
    return "https://$YANMAGA_HOST$rawPath"
  }

  suspend fun inspect(url: String): FeedInspection {
    val workUrl = canonicalWorkUrl(url)
    val response = client.execute(request(workUrl))
    response.requireSuccess()
    val finalUrl = canonicalWorkUrl(response.finalUrl)
    parsePage(decode(response.body, response.header("Content-Type")), finalUrl)
    return FeedInspection(directFeedUrl = finalUrl)
  }

  suspend fun fetchFeed(
    url: String,
    etag: String? = null,
    lastModified: String? = null,
  ): FetchResult {
    val workUrl = canonicalWorkUrl(url)
    val response = client.execute(
      request(
        workUrl,
        buildMap {
          etag?.let { put("If-None-Match", it) }
          lastModified?.let { put("If-Modified-Since", it) }
        },
      ),
    )
    if (response.statusCode == 304) return FetchResult(null, etag, lastModified, notModified = true)
    response.requireSuccess()
    val finalUrl = canonicalWorkUrl(response.finalUrl)
    return FetchResult(
      feed = parsePage(decode(response.body, response.header("Content-Type")), finalUrl),
      etag = response.header("ETag"),
      lastModified = response.header("Last-Modified"),
    )
  }

  internal fun parsePage(html: String, pageUrl: String): ParsedFeed {
    val document = Jsoup.parse(html, pageUrl)
    val title = document.selectFirst("main h1, h1")?.text()?.trim().orEmpty()
      .ifBlank { document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty() }
      .ifBlank { document.title().substringBefore('|').trim() }
    require(title.isNotBlank()) { "ヤンマガWebの作品名を取得できませんでした" }

    val episodeElements = document.select(EPISODE_LIST_SELECTOR)
    require(episodeElements.isNotEmpty()) { "ヤンマガWebのエピソード一覧を取得できませんでした" }

    val articles = episodeElements
      .asSequence()
      .filter(::isReadableForFree)
      .mapNotNull { episode ->
        val episodeTitle = episode.selectFirst(EPISODE_TITLE_SELECTOR)?.text()?.trim().orEmpty()
        val link = episode.selectFirst(EPISODE_URL_SELECTOR)
        val href = link?.attr("href")?.trim().orEmpty()
        val episodeUrl = link?.absUrl("href")?.ifBlank { resolveUrl(pageUrl, href) }.orEmpty()
        if (episodeTitle.isBlank() || episodeUrl.isBlank()) return@mapNotNull null
        val publishedAt = parsePublishedAt(
          episode.selectFirst(EPISODE_DATE_SELECTOR)?.text().orEmpty(),
        )
        ParsedArticle(
          externalId = episodeUrl,
          identityKey = identityKey(episodeUrl),
          url = episodeUrl,
          title = episodeTitle,
          publishedAt = publishedAt,
        )
      }
      .distinctBy(ParsedArticle::url)
      .toList()

    return ParsedFeed(
      title = title,
      feedUrl = pageUrl,
      siteUrl = pageUrl,
      articles = articles,
    )
  }

  private fun isReadableForFree(episode: Element): Boolean {
    val explicitlyUnavailable = episode.attr("data-is-free").equals("false", ignoreCase = true)
    val hasFreeBadge = episode.selectFirst(FREE_BADGE_SELECTOR) != null
    return !explicitlyUnavailable || hasFreeBadge
  }

  private fun request(url: String, additionalHeaders: Map<String, String> = emptyMap()): HttpRequest = HttpRequest(
    url = url,
    headers = mapOf(
      "Accept" to "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5",
    ) + additionalHeaders,
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
    val charset = sequenceOf(headerCharset, "UTF-8")
      .mapNotNull { name -> runCatching { Charset.forName(name) }.getOrNull() }
      .first()
    return bytes.toString(charset).removePrefix("\uFEFF")
  }

  private fun parsePublishedAt(value: String): String {
    val dateText = DATE_PATTERN.find(value)?.value ?: return Instant.now().toString()
    return runCatching {
      LocalDate.parse(dateText, DATE_FORMATTER)
        .atStartOfDay(TOKYO)
        .toInstant()
        .toString()
    }.getOrElse { Instant.now().toString() }
  }

  private fun resolveUrl(base: String, value: String): String = runCatching {
    URI(base).resolve(value).normalize().toString()
  }.getOrDefault(value)

  private fun identityKey(url: String): String = MessageDigest.getInstance("SHA-256")
    .digest(url.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }

  private companion object {
    const val YANMAGA_HOST = "yanmaga.jp"
    const val WWW_YANMAGA_HOST = "www.yanmaga.jp"
    const val EPISODE_LIST_SELECTOR = ".mod-episode-item"
    const val EPISODE_TITLE_SELECTOR = ".mod-episode-title"
    const val EPISODE_URL_SELECTOR = ".mod-episode-link"
    const val EPISODE_DATE_SELECTOR = ".mod-episode-date"
    const val FREE_BADGE_SELECTOR = ".mod-episode-point--free"
    val DATE_PATTERN = Regex("\\d{4}/\\d{1,2}/\\d{1,2}")
    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu/M/d")
    val TOKYO: ZoneId = ZoneId.of("Asia/Tokyo")
  }
}
