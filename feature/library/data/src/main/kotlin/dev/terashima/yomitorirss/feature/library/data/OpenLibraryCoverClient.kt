package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.feature.library.LibraryBook
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import org.json.JSONObject

internal enum class CoverLookupStatus {
  FOUND,
  NOT_FOUND,
  AMBIGUOUS,
}

internal data class CoverLookupResult(
  val status: CoverLookupStatus,
  val thumbnailUrl: String? = null,
  val matchedIdentifier: String? = null,
)

internal class OpenLibraryCoverClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  private var requestAttempted = false

  suspend fun lookup(book: LibraryBook): CoverLookupResult {
    book.isbn13.cleanIsbn()?.let { return lookupByIsbn(it) }
    book.isbn10.cleanIsbn()?.let { return lookupByIsbn(it) }
    return lookupByTitle(book)
  }

  private suspend fun lookupByIsbn(isbn: String): CoverLookupResult {
    val exact = search(query = "isbn:$isbn", limit = ISBN_RESULT_LIMIT)
      .filter { candidate ->
        candidate.coverId != null && candidate.isbns.any { it.cleanIsbn() == isbn }
      }
      .distinctBy(OpenLibraryCandidate::coverId)
    if (exact.isEmpty()) return CoverLookupResult(CoverLookupStatus.NOT_FOUND)
    if (exact.size != 1) return CoverLookupResult(CoverLookupStatus.AMBIGUOUS)

    val match = exact.single()
    return CoverLookupResult(
      status = CoverLookupStatus.FOUND,
      thumbnailUrl = "$COVER_BASE_URL/id/${match.coverId}-L.jpg",
      matchedIdentifier = "ISBN:$isbn",
    )
  }

  private suspend fun lookupByTitle(book: LibraryBook): CoverLookupResult {
    val title = book.title.trim()
    if (title.isEmpty()) return CoverLookupResult(CoverLookupStatus.NOT_FOUND)

    val author = book.authors.firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
    val query = buildString {
      append("title:\"")
      append(escapeQueryValue(title))
      append('"')
      if (author != null) {
        append(" AND author:\"")
        append(escapeQueryValue(author))
        append('"')
      }
    }
    return selectTitleCandidate(book, search(query = query, limit = TITLE_RESULT_LIMIT))
  }

  private suspend fun search(query: String, limit: Int): List<OpenLibraryCandidate> {
    if (requestAttempted) {
      throw IOException("Open Library への連続検索を避けるためバックグラウンドで再試行します")
    }
    requestAttempted = true

    val url = "$SEARCH_URL?q=${query.urlEncode()}" +
      "&fields=key,title,author_name,isbn,cover_i&limit=$limit"
    val response = httpClient.execute(
      HttpRequest(url = url, headers = mapOf("Accept" to "application/json")),
    )
    if (!response.isSuccessful) {
      throw IOException("Open Library の検索に失敗しました (${response.statusCode})")
    }

    val root = runCatching { JSONObject(response.body.toString(Charsets.UTF_8)) }
      .getOrElse { error -> throw IOException("Open Library の応答を解析できませんでした", error) }
    val docs = root.optJSONArray("docs") ?: return emptyList()
    return buildList {
      for (index in 0 until docs.length()) {
        docs.optJSONObject(index)?.toCandidate()?.let(::add)
      }
    }
  }
}

internal fun selectTitleCandidate(
  book: LibraryBook,
  candidates: List<OpenLibraryCandidate>,
): CoverLookupResult {
  val expectedTitle = normalizeBookText(book.title)
  if (expectedTitle.isEmpty()) return CoverLookupResult(CoverLookupStatus.NOT_FOUND)
  val expectedAuthors = book.authors.map(::normalizeBookText).filter(String::isNotEmpty).toSet()
  val expectedVolume = explicitVolumeNumber(book.title)

  val matches = candidates
    .asSequence()
    .filter { it.coverId != null }
    .filter { normalizeBookText(it.title) == expectedTitle }
    .filter { candidate ->
      expectedVolume == null || explicitVolumeNumber(candidate.title) == expectedVolume
    }
    .filter { candidate ->
      if (expectedAuthors.isEmpty()) {
        true
      } else {
        candidate.authors
          .map(::normalizeBookText)
          .filter(String::isNotEmpty)
          .any { candidateAuthor ->
            expectedAuthors.any { expectedAuthor ->
              candidateAuthor == expectedAuthor ||
                candidateAuthor.contains(expectedAuthor) ||
                expectedAuthor.contains(candidateAuthor)
            }
          }
      }
    }
    .distinctBy(OpenLibraryCandidate::coverId)
    .toList()

  if (matches.isEmpty()) return CoverLookupResult(CoverLookupStatus.NOT_FOUND)
  if (matches.size != 1) return CoverLookupResult(CoverLookupStatus.AMBIGUOUS)

  val match = matches.single()
  return CoverLookupResult(
    status = CoverLookupStatus.FOUND,
    thumbnailUrl = "$COVER_BASE_URL/id/${match.coverId}-L.jpg",
    matchedIdentifier = match.key,
  )
}

internal data class OpenLibraryCandidate(
  val key: String?,
  val title: String,
  val authors: List<String>,
  val isbns: List<String>,
  val coverId: Long?,
)

private fun JSONObject.toCandidate(): OpenLibraryCandidate? {
  val title = optString("title").trim().takeIf(String::isNotEmpty) ?: return null
  return OpenLibraryCandidate(
    key = optString("key").trim().takeIf(String::isNotEmpty),
    title = title,
    authors = stringList("author_name"),
    isbns = stringList("isbn"),
    coverId = if (has("cover_i") && !isNull("cover_i")) optLong("cover_i").takeIf { it > 0 } else null,
  )
}

private fun JSONObject.stringList(name: String): List<String> {
  val array = optJSONArray(name) ?: return emptyList()
  return buildList {
    for (index in 0 until array.length()) {
      array.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
    }
  }
}

internal fun normalizeBookText(value: String): String = Normalizer
  .normalize(value, Normalizer.Form.NFKC)
  .lowercase(Locale.ROOT)
  .filter(Char::isLetterOrDigit)

internal fun explicitVolumeNumber(value: String): Int? {
  val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
  return VOLUME_PATTERNS.firstNotNullOfOrNull { pattern ->
    pattern.find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull()
  }
}

private fun String?.cleanIsbn(): String? = this
  ?.filter(Char::isLetterOrDigit)
  ?.uppercase(Locale.ROOT)
  ?.takeIf { it.length == 10 || it.length == 13 }

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun escapeQueryValue(value: String): String = value
  .replace("\\", "\\\\")
  .replace("\"", "\\\"")

private const val SEARCH_URL = "https://openlibrary.org/search.json"
private const val COVER_BASE_URL = "https://covers.openlibrary.org/b"
private const val ISBN_RESULT_LIMIT = 5
private const val TITLE_RESULT_LIMIT = 10

private val VOLUME_PATTERNS = listOf(
  Regex("(?:第\\s*)?(\\d{1,3})\\s*巻"),
  Regex("(?:vol(?:ume)?\\.?|#)\\s*(\\d{1,3})", RegexOption.IGNORE_CASE),
)
