package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.feature.library.LibraryBook
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

internal class GoogleBooksCoverClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  suspend fun lookup(book: LibraryBook): TracedCoverLookupResult {
    val isbn = book.isbn13.cleanBookIsbn() ?: book.isbn10.cleanBookIsbn()
    return if (isbn != null) lookupByIsbn(isbn) else lookupByTitle(book)
  }

  private suspend fun lookupByIsbn(isbn: String): TracedCoverLookupResult {
    val response = search("isbn:$isbn", ISBN_RESULT_LIMIT)
    val withCover = response.candidates.filter { it.thumbnailUrl != null }
    val exact = withCover.filter { candidate ->
      candidate.isbns.any { it.cleanBookIsbn() == isbn }
    }.distinctBy(GoogleBooksCoverCandidate::thumbnailUrl)
    val lookup = when (exact.size) {
      0 -> CoverLookupResult(CoverLookupStatus.NOT_FOUND)
      1 -> CoverLookupResult(
        status = CoverLookupStatus.FOUND,
        thumbnailUrl = exact.single().thumbnailUrl,
        matchedIdentifier = "ISBN:$isbn",
      )
      else -> CoverLookupResult(CoverLookupStatus.AMBIGUOUS)
    }
    return TracedCoverLookupResult(
      lookup = lookup,
      step = CoverLookupTraceStep(
        provider = GOOGLE_BOOKS_PROVIDER,
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
    val searchableTitle = searchableBookTitle(book.title)
    if (searchableTitle.isBlank()) {
      return TracedCoverLookupResult(
        lookup = CoverLookupResult(CoverLookupStatus.NOT_FOUND),
        step = CoverLookupTraceStep(
          provider = GOOGLE_BOOKS_PROVIDER,
          status = CoverLookupStatus.NOT_FOUND,
          reason = "EMPTY_TITLE",
        ),
      )
    }

    val author = book.authors.firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
    val query = buildString {
      append("intitle:\"")
      append(searchableTitle.escapeSearchValue())
      append('"')
      if (author != null) {
        append(" inauthor:\"")
        append(author.escapeSearchValue())
        append('"')
      }
    }
    val response = search(query, TITLE_RESULT_LIMIT)
    return selectGoogleBooksTitleCandidate(book, response)
  }

  private suspend fun search(query: String, limit: Int): GoogleBooksSearchResponse {
    val url = "$SEARCH_URL?q=${query.urlEncode()}&maxResults=$limit&projection=lite"
    val response = httpClient.execute(
      HttpRequest(url = url, headers = mapOf("Accept" to "application/json")),
    )
    if (!response.isSuccessful) {
      val step = CoverLookupTraceStep(
        provider = GOOGLE_BOOKS_PROVIDER,
        status = CoverLookupStatus.ERROR,
        reason = if (isRetryableGoogleBooksStatus(response.statusCode)) "HTTP_RETRYABLE" else "HTTP_ERROR",
        httpStatus = response.statusCode,
        responseBytes = response.body.size,
      )
      if (isRetryableGoogleBooksStatus(response.statusCode)) {
        throw CoverProviderIOException(
          "Google Books API の一時的な検索エラー (${response.statusCode})",
          step,
        )
      }
      return GoogleBooksSearchResponse(
        candidates = emptyList(),
        httpStatus = response.statusCode,
        responseBytes = response.body.size,
        terminalError = step,
      )
    }

    val root = runCatching { JSONObject(response.body.toString(Charsets.UTF_8)) }
      .getOrElse { error ->
        throw CoverProviderIOException(
          "Google Books API の応答を解析できませんでした",
          CoverLookupTraceStep(
            provider = GOOGLE_BOOKS_PROVIDER,
            status = CoverLookupStatus.ERROR,
            reason = "PARSE_ERROR",
            httpStatus = response.statusCode,
            responseBytes = response.body.size,
          ),
          error,
        )
      }
    val items = root.optJSONArray("items")
    val candidates = buildList {
      if (items != null) {
        for (index in 0 until items.length()) {
          items.optJSONObject(index)?.toGoogleBooksCoverCandidate()?.let(::add)
        }
      }
    }
    return GoogleBooksSearchResponse(
      candidates = candidates,
      httpStatus = response.statusCode,
      responseBytes = response.body.size,
    )
  }
}

private fun selectGoogleBooksTitleCandidate(
  book: LibraryBook,
  response: GoogleBooksSearchResponse,
): TracedCoverLookupResult {
  response.terminalError?.let { step ->
    return TracedCoverLookupResult(CoverLookupResult(CoverLookupStatus.ERROR), step)
  }
  val expectedTitle = normalizedSearchableBookTitle(book.title)
  val expectedAuthors = book.authors.map(::normalizeBookText).filter(String::isNotEmpty).toSet()
  val expectedVolume = explicitVolumeNumber(book.title)
  val withCover = response.candidates.filter { it.thumbnailUrl != null }
  val titleMatches = withCover.filter { normalizedSearchableBookTitle(it.title) == expectedTitle }
  val volumeMatches = titleMatches.filter { candidate ->
    expectedVolume == null || explicitVolumeNumber(candidate.title) == expectedVolume
  }
  val authorMatches = volumeMatches.filter { candidate ->
    expectedAuthors.isEmpty() || candidate.authors.any { candidateAuthor ->
      val normalizedCandidate = normalizeBookText(candidateAuthor)
      expectedAuthors.any { expected ->
        normalizedCandidate == expected || normalizedCandidate.contains(expected) || expected.contains(normalizedCandidate)
      }
    }
  }.distinctBy(GoogleBooksCoverCandidate::thumbnailUrl)

  val lookup = when (authorMatches.size) {
    0 -> CoverLookupResult(CoverLookupStatus.NOT_FOUND)
    1 -> {
      val match = authorMatches.single()
      CoverLookupResult(
        status = CoverLookupStatus.FOUND,
        thumbnailUrl = match.thumbnailUrl,
        matchedIdentifier = match.id?.let { "GOOGLE_BOOKS:$it" },
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
      provider = GOOGLE_BOOKS_PROVIDER,
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

private data class GoogleBooksSearchResponse(
  val candidates: List<GoogleBooksCoverCandidate>,
  val httpStatus: Int,
  val responseBytes: Int,
  val terminalError: CoverLookupTraceStep? = null,
)

internal data class GoogleBooksCoverCandidate(
  val id: String?,
  val title: String,
  val authors: List<String>,
  val isbns: List<String>,
  val thumbnailUrl: String?,
)

private fun JSONObject.toGoogleBooksCoverCandidate(): GoogleBooksCoverCandidate? {
  val info = optJSONObject("volumeInfo") ?: return null
  val title = info.optString("title").trim().takeIf(String::isNotEmpty) ?: return null
  val imageLinks = info.optJSONObject("imageLinks")
  val thumbnail = GOOGLE_BOOKS_IMAGE_KEYS.firstNotNullOfOrNull { key ->
    imageLinks?.optString(key)?.trim()?.takeIf(String::isNotEmpty)
  }?.replace("http://", "https://")
  val identifiers = info.optJSONArray("industryIdentifiers")
  val isbns = buildList {
    if (identifiers != null) {
      for (index in 0 until identifiers.length()) {
        identifiers.optJSONObject(index)?.optString("identifier")
          ?.trim()
          ?.takeIf(String::isNotEmpty)
          ?.let(::add)
      }
    }
  }
  return GoogleBooksCoverCandidate(
    id = optString("id").trim().takeIf(String::isNotEmpty),
    title = title,
    authors = info.stringList("authors"),
    isbns = isbns,
    thumbnailUrl = thumbnail,
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

internal fun isRetryableGoogleBooksStatus(statusCode: Int): Boolean =
  statusCode == 408 || statusCode == 429 || statusCode in 500..599

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
private fun String.escapeSearchValue(): String = replace("\\", "\\\\").replace("\"", "\\\"")

private const val SEARCH_URL = "https://www.googleapis.com/books/v1/volumes"
internal const val GOOGLE_BOOKS_PROVIDER = "GOOGLE_BOOKS"
private const val ISBN_RESULT_LIMIT = 5
private const val TITLE_RESULT_LIMIT = 10
private val GOOGLE_BOOKS_IMAGE_KEYS = listOf("extraLarge", "large", "medium", "small", "thumbnail", "smallThumbnail")
