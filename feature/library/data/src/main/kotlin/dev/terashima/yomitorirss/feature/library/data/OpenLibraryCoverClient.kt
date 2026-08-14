package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.feature.library.LibraryBook
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.Locale
import org.json.JSONObject

internal enum class CoverLookupStatus {
  FOUND,
  NOT_FOUND,
  AMBIGUOUS,
  ERROR,
}

internal data class CoverLookupResult(
  val status: CoverLookupStatus,
  val thumbnailUrl: String? = null,
  val matchedIdentifier: String? = null,
)

internal class OpenLibraryCoverClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  suspend fun lookup(book: LibraryBook): CoverLookupResult = lookupWithTrace(book).lookup

  suspend fun lookupWithTrace(book: LibraryBook): TracedCoverLookupResult {
    val isbn = book.isbn13.cleanBookIsbn() ?: book.isbn10.cleanBookIsbn()
    return if (isbn != null) lookupByIsbn(isbn) else lookupByTitle(book)
  }

  private suspend fun lookupByIsbn(isbn: String): TracedCoverLookupResult {
    val response = search(query = "isbn:$isbn", limit = ISBN_RESULT_LIMIT)
    response.terminalError?.let { step ->
      return TracedCoverLookupResult(CoverLookupResult(CoverLookupStatus.ERROR), step)
    }
    val withCover = response.candidates.filter { it.coverId != null }
    val exact = withCover.filter { candidate ->
      candidate.isbns.any { it.cleanBookIsbn() == isbn }
    }.distinctBy(OpenLibraryCandidate::coverId)
    val lookup = when (exact.size) {
      0 -> CoverLookupResult(CoverLookupStatus.NOT_FOUND)
      1 -> {
        val match = exact.single()
        CoverLookupResult(
          status = CoverLookupStatus.FOUND,
          thumbnailUrl = "$COVER_BASE_URL/id/${match.coverId}-L.jpg",
          matchedIdentifier = "ISBN:$isbn",
        )
      }
      else -> CoverLookupResult(CoverLookupStatus.AMBIGUOUS)
    }
    return TracedCoverLookupResult(
      lookup = lookup,
      step = CoverLookupTraceStep(
        provider = OPEN_LIBRARY_PROVIDER,
        status = lookup.status,
        reason = when (lookup.status) {
          CoverLookupStatus.FOUND -> "ISBN_MATCH"
          CoverLookupStatus.AMBIGUOUS -> "MULTIPLE_ISBN_COVER_MATCHES"
          else -> "ISBN_COVER_MATCH_NOT_FOUND"
        },
        httpStatus = response.httpStatus,
        responseBytes = response.responseBytes,
        candidateCount = response.candidates.size,
        coverCandidateCount = withCover.size,
        attributes = mapOf("searchMode" to "ISBN"),
      ),
    )
  }

  private suspend fun lookupByTitle(book: LibraryBook): TracedCoverLookupResult {
    val title = searchableBookTitle(book.title)
    if (title.isEmpty()) {
      return TracedCoverLookupResult(
        CoverLookupResult(CoverLookupStatus.NOT_FOUND),
        CoverLookupTraceStep(
          provider = OPEN_LIBRARY_PROVIDER,
          status = CoverLookupStatus.NOT_FOUND,
          reason = "EMPTY_TITLE",
        ),
      )
    }

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
    return selectOpenLibraryTitleCandidate(book, search(query = query, limit = TITLE_RESULT_LIMIT))
  }

  private suspend fun search(query: String, limit: Int): OpenLibrarySearchResponse {
    val url = "$SEARCH_URL?q=${query.urlEncode()}" +
      "&fields=key,title,author_name,isbn,cover_i&limit=$limit"
    val response = httpClient.execute(
      HttpRequest(url = url, headers = mapOf("Accept" to "application/json")),
    )
    if (!response.isSuccessful) {
      val step = CoverLookupTraceStep(
        provider = OPEN_LIBRARY_PROVIDER,
        status = CoverLookupStatus.ERROR,
        reason = if (isRetryableOpenLibraryStatus(response.statusCode)) "HTTP_RETRYABLE" else "HTTP_ERROR",
        httpStatus = response.statusCode,
        responseBytes = response.body.size,
      )
      if (isRetryableOpenLibraryStatus(response.statusCode)) {
        throw CoverProviderIOException(
          "Open Library の一時的な検索エラー (${response.statusCode})",
          step,
        )
      }
      return OpenLibrarySearchResponse(
        candidates = emptyList(),
        httpStatus = response.statusCode,
        responseBytes = response.body.size,
        terminalError = step,
      )
    }

    val root = runCatching { JSONObject(response.body.toString(Charsets.UTF_8)) }
      .getOrElse { error ->
        throw CoverProviderIOException(
          "Open Library の応答を解析できませんでした",
          CoverLookupTraceStep(
            provider = OPEN_LIBRARY_PROVIDER,
            status = CoverLookupStatus.ERROR,
            reason = "PARSE_ERROR",
            httpStatus = response.statusCode,
            responseBytes = response.body.size,
          ),
          error,
        )
      }
    val docs = root.optJSONArray("docs")
    val candidates = buildList {
      if (docs != null) {
        for (index in 0 until docs.length()) {
          docs.optJSONObject(index)?.toCandidate()?.let(::add)
        }
      }
    }
    return OpenLibrarySearchResponse(
      candidates = candidates,
      httpStatus = response.statusCode,
      responseBytes = response.body.size,
    )
  }
}

internal fun isRetryableOpenLibraryStatus(statusCode: Int): Boolean =
  statusCode == 408 || statusCode == 429 || statusCode in 500..599

internal fun selectTitleCandidate(
  book: LibraryBook,
  candidates: List<OpenLibraryCandidate>,
): CoverLookupResult = selectOpenLibraryTitleCandidate(
  book,
  OpenLibrarySearchResponse(candidates, httpStatus = 200, responseBytes = 0),
).lookup

private fun selectOpenLibraryTitleCandidate(
  book: LibraryBook,
  response: OpenLibrarySearchResponse,
): TracedCoverLookupResult {
  response.terminalError?.let { step ->
    return TracedCoverLookupResult(CoverLookupResult(CoverLookupStatus.ERROR), step)
  }
  val expectedTitle = normalizedSearchableBookTitle(book.title)
  if (expectedTitle.isEmpty()) {
    return TracedCoverLookupResult(
      CoverLookupResult(CoverLookupStatus.NOT_FOUND),
      CoverLookupTraceStep(
        provider = OPEN_LIBRARY_PROVIDER,
        status = CoverLookupStatus.NOT_FOUND,
        reason = "EMPTY_TITLE",
      ),
    )
  }
  val expectedAuthors = book.authors.map(::normalizeBookText).filter(String::isNotEmpty).toSet()
  val expectedVolume = explicitVolumeNumber(book.title)
  val withCover = response.candidates.filter { it.coverId != null }
  val titleMatches = withCover.filter { normalizedSearchableBookTitle(it.title) == expectedTitle }
  val volumeMatches = titleMatches.filter { candidate ->
    expectedVolume == null || explicitVolumeNumber(candidate.title) == expectedVolume
  }
  val authorMatches = volumeMatches.filter { candidate ->
    expectedAuthors.isEmpty() || candidate.authors.any { candidateAuthor ->
      val normalizedCandidate = normalizeBookText(candidateAuthor)
      expectedAuthors.any { expectedAuthor ->
        normalizedCandidate == expectedAuthor ||
          normalizedCandidate.contains(expectedAuthor) ||
          expectedAuthor.contains(normalizedCandidate)
      }
    }
  }.distinctBy(OpenLibraryCandidate::coverId)

  val lookup = when (authorMatches.size) {
    0 -> CoverLookupResult(CoverLookupStatus.NOT_FOUND)
    1 -> {
      val match = authorMatches.single()
      CoverLookupResult(
        status = CoverLookupStatus.FOUND,
        thumbnailUrl = "$COVER_BASE_URL/id/${match.coverId}-L.jpg",
        matchedIdentifier = match.key,
      )
    }
    else -> CoverLookupResult(CoverLookupStatus.AMBIGUOUS)
  }
  val reason = when {
    lookup.status == CoverLookupStatus.FOUND -> "TITLE_AUTHOR_MATCH"
    lookup.status == CoverLookupStatus.AMBIGUOUS -> "MULTIPLE_HIGH_CONFIDENCE_MATCHES"
    withCover.isEmpty() -> "NO_COVER_CANDIDATES"
    titleMatches.isEmpty() -> "TITLE_MISMATCH"
    volumeMatches.isEmpty() -> "VOLUME_MISMATCH"
    authorMatches.isEmpty() -> "AUTHOR_MISMATCH"
    else -> "NOT_FOUND"
  }
  return TracedCoverLookupResult(
    lookup = lookup,
    step = CoverLookupTraceStep(
      provider = OPEN_LIBRARY_PROVIDER,
      status = lookup.status,
      reason = reason,
      httpStatus = response.httpStatus,
      responseBytes = response.responseBytes,
      candidateCount = response.candidates.size,
      coverCandidateCount = withCover.size,
      titleMatchCount = titleMatches.size,
      volumeMatchCount = volumeMatches.size,
      authorMatchCount = authorMatches.size,
      attributes = mapOf("searchMode" to "TITLE_AUTHOR"),
    ),
  )
}

private data class OpenLibrarySearchResponse(
  val candidates: List<OpenLibraryCandidate>,
  val httpStatus: Int,
  val responseBytes: Int,
  val terminalError: CoverLookupTraceStep? = null,
)

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

internal fun searchableBookTitle(value: String): String {
  var title = value.trim()
  while (true) {
    val stripped = title.replace(EDITION_SUFFIX, "").trim()
    if (stripped == title) return title
    title = stripped
  }
}

internal fun normalizedSearchableBookTitle(value: String): String = normalizeBookText(searchableBookTitle(value))

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

internal fun String?.cleanBookIsbn(): String? = this
  ?.filter(Char::isLetterOrDigit)
  ?.uppercase(Locale.ROOT)
  ?.takeIf { it.length == 10 || it.length == 13 }

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun escapeQueryValue(value: String): String = value
  .replace("\\", "\\\\")
  .replace("\"", "\\\"")

private const val SEARCH_URL = "https://openlibrary.org/search.json"
private const val COVER_BASE_URL = "https://covers.openlibrary.org/b"
internal const val OPEN_LIBRARY_PROVIDER = "OPEN_LIBRARY"
private const val ISBN_RESULT_LIMIT = 5
private const val TITLE_RESULT_LIMIT = 10

private val EDITION_SUFFIX = Regex(
  """\s*[（(]\s*(?:Japanese|Kindle|English)\s+Edition\s*[)）]\s*$""",
  RegexOption.IGNORE_CASE,
)
private val VOLUME_PATTERNS = listOf(
  Regex("(?:第\\s*)?(\\d{1,3})\\s*巻"),
  Regex("(?:vol(?:ume)?\\.?|#)\\s*(\\d{1,3})", RegexOption.IGNORE_CASE),
)
