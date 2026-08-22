package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.WebLibraryMutator
import java.net.URI
import java.nio.charset.Charset
import java.util.Locale
import org.json.JSONArray

class DefaultWebLibraryMutator(
  private val database: DatabaseConnection,
  private val metadataClient: WebLibraryMetadataClient = WebLibraryMetadataClient(),
) : WebLibraryMutator {
  override suspend fun addWebBook(url: String, titleHint: String?): LibraryBook {
    ensureLibraryCatalogSchema(database.writable)
    val book = metadataClient.fetch(url, titleHint)
    val syncedAt = System.currentTimeMillis()
    database.transaction {
      insertWithOnConflict(
        "library_items",
        null,
        book.toValues(syncedAt),
        SQLiteDatabase.CONFLICT_REPLACE,
      )
      delete(
        "hidden_library_items",
        "source = ? AND source_id = ?",
        arrayOf(LibrarySource.WEB.name, book.sourceId),
      )
    }
    return book
  }

  override suspend fun removeWebBook(book: LibraryBook) {
    require(book.source == LibrarySource.WEB) { "Web 蔵書以外は削除できません" }
    ensureLibraryCatalogSchema(database.writable)
    database.transaction {
      val args = arrayOf(LibrarySource.WEB.name, book.sourceId)
      delete("hidden_library_items", "source = ? AND source_id = ?", args)
      delete("library_item_series", "source = ? AND source_id = ?", args)
      delete("library_item_series_exclusions", "source = ? AND source_id = ?", args)
      delete("library_items", "source = ? AND source_id = ?", args)
    }
  }

  private fun LibraryBook.toValues(syncedAt: Long): ContentValues = ContentValues().apply {
    put("source", source.name)
    put("source_id", sourceId)
    put("title", title)
    put("authors", JSONArray(authors).toString())
    put("publisher", publisher)
    put("published_date", publishedDate)
    put("description", description)
    put("isbn10", isbn10)
    put("isbn13", isbn13)
    put("thumbnail_url", thumbnailUrl)
    put("info_url", infoUrl)
    put("narrators", JSONArray(narrators).toString())
    put("duration", duration)
    put("synced_at", syncedAt)
  }
}

class WebLibraryMetadataClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  suspend fun fetch(url: String, titleHint: String? = null): LibraryBook {
    val requestedUrl = normalizeWebUrl(url)
    val response = httpClient.execute(
      HttpRequest(
        url = requestedUrl,
        headers = mapOf(
          "Accept" to "text/html,application/xhtml+xml;q=0.9,*/*;q=0.1",
          "User-Agent" to USER_AGENT,
        ),
      ),
    )
    require(response.isSuccessful) {
      "Web ページを取得できませんでした: HTTP ${response.statusCode}"
    }
    require(response.body.size <= MAX_HTML_BYTES) {
      "Web ページが大きすぎるため蔵書へ追加できません"
    }
    val contentType = response.header("Content-Type").orEmpty()
    require(contentType.isBlank() || contentType.contains("html", ignoreCase = true)) {
      "HTML ページではないため蔵書へ追加できません"
    }
    val finalUrl = normalizeWebUrl(response.finalUrl.ifBlank { requestedUrl })
    val charset = responseCharset(contentType)
    return parseWebLibraryBook(
      url = finalUrl,
      html = response.body.toString(charset),
      titleHint = titleHint,
    )
  }
}

internal fun parseWebLibraryBook(
  url: String,
  html: String,
  titleHint: String? = null,
): LibraryBook {
  val normalizedUrl = normalizeWebUrl(url)
  val metadata = htmlMetaValues(html)
  val title = metadata["property:og:title"]
    ?: metadata["name:twitter:title"]
    ?: htmlTitle(html)
    ?: titleHint?.trim()?.takeIf(String::isNotEmpty)
    ?: URI(normalizedUrl).host.removePrefix("www.")
  val description = metadata["property:og:description"]
    ?: metadata["name:description"]
    ?: metadata["name:twitter:description"]
  val image = metadata["property:og:image:secure_url"]
    ?: metadata["property:og:image"]
    ?: metadata["name:twitter:image"]
  val author = metadata["name:author"]
    ?.split(',', '、')
    ?.map(String::trim)
    ?.filter(String::isNotEmpty)
    .orEmpty()

  return LibraryBook(
    source = LibrarySource.WEB,
    sourceId = normalizedUrl,
    title = decodeHtmlEntities(title).trim(),
    authors = author.map(::decodeHtmlEntities),
    publisher = null,
    publishedDate = null,
    description = description?.let(::decodeHtmlEntities)?.trim()?.takeIf(String::isNotEmpty),
    isbn10 = null,
    isbn13 = null,
    thumbnailUrl = image
      ?.let(::decodeHtmlEntities)
      ?.trim()
      ?.takeIf(String::isNotEmpty)
      ?.let { resolveWebUrl(normalizedUrl, it) }
      ?.takeIf { URI(it).scheme.equals("https", ignoreCase = true) },
    infoUrl = normalizedUrl,
  )
}

internal fun normalizeWebUrl(input: String): String {
  val raw = input.trim()
  require(raw.isNotEmpty()) { "URL を入力してください" }
  val uri = runCatching { URI(raw) }.getOrElse { throw IllegalArgumentException("URL が正しくありません") }
  val scheme = uri.scheme?.lowercase(Locale.ROOT)
  require(scheme == "https" || scheme == "http") { "http または https の URL を指定してください" }
  val host = uri.host?.lowercase(Locale.ROOT)
  require(!host.isNullOrBlank()) { "URL のホスト名を確認してください" }
  return URI(
    scheme,
    uri.userInfo,
    host,
    uri.port,
    uri.rawPath,
    uri.rawQuery,
    null,
  ).normalize().toASCIIString()
}

private fun responseCharset(contentType: String): Charset {
  val charsetName = Regex("charset\\s*=\\s*[\"']?([^;\"'\\s]+)", RegexOption.IGNORE_CASE)
    .find(contentType)
    ?.groupValues
    ?.getOrNull(1)
  return charsetName?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
}

private fun htmlMetaValues(html: String): Map<String, String> = buildMap {
  META_TAG.findAll(html).forEach { tagMatch ->
    val attributes = parseAttributes(tagMatch.value)
    val content = attributes["content"]?.trim()?.takeIf(String::isNotEmpty) ?: return@forEach
    attributes["property"]?.lowercase(Locale.ROOT)?.let { putIfAbsent("property:$it", content) }
    attributes["name"]?.lowercase(Locale.ROOT)?.let { putIfAbsent("name:$it", content) }
  }
}

private fun parseAttributes(tag: String): Map<String, String> = buildMap {
  ATTRIBUTE.findAll(tag).forEach { match ->
    val name = match.groupValues[1].lowercase(Locale.ROOT)
    val value = sequenceOf(match.groupValues[2], match.groupValues[3], match.groupValues[4])
      .firstOrNull(String::isNotEmpty)
      .orEmpty()
    put(name, value)
  }
}

private fun htmlTitle(html: String): String? = TITLE
  .find(html)
  ?.groupValues
  ?.getOrNull(1)
  ?.replace(Regex("<[^>]+>"), " ")
  ?.let(::decodeHtmlEntities)
  ?.trim()
  ?.takeIf(String::isNotEmpty)

private fun resolveWebUrl(baseUrl: String, value: String): String? = runCatching {
  normalizeWebUrl(URI(baseUrl).resolve(value).toString())
}.getOrNull()

private fun decodeHtmlEntities(value: String): String {
  val named = value
    .replace("&amp;", "&", ignoreCase = true)
    .replace("&quot;", "\"", ignoreCase = true)
    .replace("&#39;", "'", ignoreCase = true)
    .replace("&apos;", "'", ignoreCase = true)
    .replace("&lt;", "<", ignoreCase = true)
    .replace("&gt;", ">", ignoreCase = true)
    .replace("&nbsp;", " ", ignoreCase = true)
  return NUMERIC_ENTITY.replace(named) { match ->
    val raw = match.groupValues[1]
    val codePoint = if (raw.startsWith("x", ignoreCase = true)) {
      raw.drop(1).toIntOrNull(16)
    } else {
      raw.toIntOrNull()
    }
    codePoint?.takeIf(Character::isValidCodePoint)?.let(Character::toChars)?.concatToString()
      ?: match.value
  }
}

private val META_TAG = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
private val ATTRIBUTE = Regex("""([:\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))""")
private val TITLE = Regex("<title\\b[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val NUMERIC_ENTITY = Regex("&#(x[0-9a-fA-F]+|[0-9]+);")
private const val MAX_HTML_BYTES = 4 * 1024 * 1024
private const val USER_AGENT = "Yomitori/1.0 WebLibraryMetadata"
