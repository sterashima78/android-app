package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import dev.terashima.yomitorirss.feature.library.LibraryBook
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

internal class GoogleBooksCoverClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  suspend fun lookup(
    book: LibraryBook,
    accessToken: String? = null,
  ): TracedCoverLookupResult {
    val isbn = book.isbn13.cleanBookIsbn() ?: book.isbn10.cleanBookIsbn()
    return if (isbn != null) {
      lookupByIsbn(isbn, accessToken)
    } else {
      lookupByTitle(book, accessToken)
    }
  }

  internal suspend fun lookupByIsbn(
    isbn: String,
    accessToken: String?,
  ): TracedCoverLookupResult {
    val cleanedIsbn = isbn.cleanBookIsbn() ?: return TracedCoverLookupResult(
      lookup = CoverLookupResult(CoverLookupStatus.NOT_FOUND),
      step = CoverLookupTraceStep(
        provider = GOOGLE_BOOKS_PROVIDER,
        status = CoverLookupStatus.NOT_FOUND,
        reason = "INVALID_ISBN",
        attributes = mapOf("searchMode" to "ISBN"),
      ),
    )
    val response = search("isbn:$cleanedIsbn", ISBN_RESULT_LIMIT, accessToken)
    response.terminalError?.let { step ->
      return TracedCoverLookupResult(CoverLookupResult(CoverLookupStatus.ERROR), step)
    }

    val exact = response.candidates.filter { candidate ->
      candidate.isbns.any { it.cleanBookIsbn() == cleanedIsbn }
    }
    val uniqueCovers = exact.mapNotNull(GoogleBooksCoverCandidate::thumbnailUrl).distinct()
    val lookup = when (uniqueCovers.size) {
      0 -> CoverLookupResult(CoverLookupStatus.NOT_FOUND)
      1 -> CoverLookupResult(
        status = CoverLookupStatus.FOUND,
        thumbnailUrl = uniqueCovers.single(),
        matchedIdentifier = "ISBN:$cleanedIsbn",
      )
      else -> CoverLookupResult(CoverLookupStatus.AMBIGUOUS)
    }
    val resolvedIdentifiers = exact.flatMap { candidate ->
      candidate.isbns.mapNotNull {
        it.toResolvedIsbn(BookIdentifierRelation.EXACT_EDITION, GOOGLE_BOOKS_PROVIDER)
      }
    }.distinctBy { "${it.type}:${it.value}" }
    return TracedCoverLookupResult(
      lookup = lookup,
      step = CoverLookupTraceStep(
        provider = GOOGLE_BOOKS_PROVIDER,
        status = lookup.status,
        reason = when (lookup.status) {
          CoverLookupStatus.FOUND -> "ISBN_MATCH"
          CoverLookupStatus.AMBIGUOUS -> "MULTIPLE_ISBN_COVER_MATCHES"
          else -> if (exact.isNotEmpty()) "MATCHED_BOOK_WITHOUT_COVER" else "ISBN_MATCH_NOT_FOUND"
        },
        httpStatus = response.httpStatus,
        responseBytes = response.responseBytes,
        candidateCount = response.candidates.size,
        coverCandidateCount = response.candidates.count { it.thumbnailUrl != null },
        attributes = mapOf(
          "searchMode" to "ISBN",
          "matchRelation" to BookIdentifierRelation.EXACT_EDITION.name,
          "requestAttempts" to response.requestAttempts.toString(),
        ),
      ),
      resolvedIdentifiers = resolvedIdentifiers,
    )
  }

  internal suspend fun lookupByTitle(
    book: LibraryBook,
    accessToken: String?,
  ): TracedCoverLookupResult {
    val searchableTitle = searchableBookTitle(book.title)
    if (normalizedSearchableBookTitle(searchableTitle).isEmpty()) {
      return TracedCoverLookupResult(
        lookup = CoverLookupResult(CoverLookupStatus.NOT_FOUND),
        step = CoverLookupTraceStep(
          provider = GOOGLE_BOOKS_PROVIDER,
          status = CoverLookupStatus.NOT_FOUND,
          reason = "EMPTY_TITLE",
          operation = "BIBLIOGRAPHIC_SEARCH",
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
    val response = search(query, TITLE_RESULT_LIMIT, accessToken)
    return selectGoogleBooksTitleCandidate(book, response)
  }

  private suspend fun search(
    query: String,
    limit: Int,
    accessToken: String?,
  ): GoogleBooksSearchResponse {
    if (accessToken.isNullOrBlank()) {
      return GoogleBooksSearchResponse(
        candidates = emptyList(),
        httpStatus = null,
        responseBytes = 0,
        requestAttempts = 0,
        terminalError = CoverLookupTraceStep(
          provider = GOOGLE_BOOKS_PROVIDER,
          status = CoverLookupStatus.ERROR,
          reason = "AUTH_UNAVAILABLE",
          operation = "BIBLIOGRAPHIC_SEARCH",
        ),
      )
    }

    val url = "$SEARCH_URL?q=${query.urlEncode()}&maxResults=$limit"
    val request = HttpRequest(
      url = url,
      headers = mapOf(
        "Accept" to "application/json",
        "Authorization" to "Bearer $accessToken",
      ),
    )
    var requestAttempts = 0
    var response: HttpResponse
    do {
      requestAttempts++
      response = httpClient.execute(request)
    } while (
      requestAttempts < MAX_IMMEDIATE_REQUEST_ATTEMPTS &&
      response.statusCode in IMMEDIATE_RETRY_STATUSES
    )

    if (!response.isSuccessful) {
      val retryable = isRetryableGoogleBooksStatus(response.statusCode)
      val step = CoverLookupTraceStep(
        provider = GOOGLE_BOOKS_PROVIDER,
        status = CoverLookupStatus.ERROR,
        reason = if (retryable) "HTTP_RETRYABLE" else "HTTP_ERROR",
        retryable = retryable,
        retryAfterSeconds = response.retryAfterSeconds(),
        httpStatus = response.statusCode,
        responseBytes = response.body.size,
        attributes = mapOf("requestAttempts" to requestAttempts.toString()),
      )
      if (retryable) {
        throw CoverProviderIOException(
          "Google Books API の一時的な検索エラー (${response.statusCode})",
          step,
        )
      }
      return GoogleBooksSearchResponse(
        candidates = emptyList(),
        httpStatus = response.statusCode,
        responseBytes = response.body.size,
        requestAttempts = requestAttempts,
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
            retryable = true,
            httpStatus = response.statusCode,
            responseBytes = response.body.size,
            attributes = mapOf("requestAttempts" to requestAttempts.toString()),
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
      requestAttempts = requestAttempts,
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
  if (expectedTitle.isEmpty()) {
    return TracedCoverLookupResult(
      CoverLookupResult(CoverLookupStatus.NOT_FOUND),
      CoverLookupTraceStep(
        provider = GOOGLE_BOOKS_PROVIDER,
        status = CoverLookupStatus.NOT_FOUND,
        reason = "EMPTY_TITLE",
        operation = "BIBLIOGRAPHIC_SEARCH",
      ),
    )
  }
  val expectedAuthors = book.authors.map(::normalizeBookText).filter(String::isNotEmpty).toSet()
  val expectedVolume = explicitVolumeNumber(book.title)
  val titleMatches = response.candidates.filter { normalizedSearchableBookTitle(it.title) == expectedTitle }
  val volumeMatches = titleMatches.filter { candidate ->
    expectedVolume == null || explicitVolumeNumber(candidate.title) == expectedVolume
  }
  val authorMatches = volumeMatches.filter { candidate ->
    expectedAuthors.isEmpty() || candidate.authors.any { candidateAuthor ->
      val normalizedCandidate = normalizeBookText(candidateAuthor)
      normalizedCandidate.isNotEmpty() && expectedAuthors.any { expected ->
        normalizedCandidate == expected || normalizedCandidate.contains(expected) || expected.contains(normalizedCandidate)
      }
    }
  }.distinctBy { candidate -> candidate.id ?: "${candidate.title}:${candidate.authors.joinToString()}" }

  val lookup = when (authorMatches.size) {
    0 -> CoverLookupResult(CoverLookupStatus.NOT_FOUND)
    1 -> {
      val match = authorMatches.single()
      if (match.thumbnailUrl == null) {
        CoverLookupResult(CoverLookupStatus.NOT_FOUND)
      } else {
        CoverLookupResult(
          status = CoverLookupStatus.FOUND,
          thumbnailUrl = match.thumbnailUrl,
          matchedIdentifier = match.id?.let { "GOOGLE_BOOKS:$it" },
        )
      }
    }
    else -> CoverLookupResult(CoverLookupStatus.AMBIGUOUS)
  }
  val reason = when {
    lookup.status == CoverLookupStatus.FOUND -> "TITLE_AUTHOR_MATCH"
    lookup.status == CoverLookupStatus.AMBIGUOUS -> "MULTIPLE_HIGH_CONFIDENCE_MATCHES"
    titleMatches.isEmpty() -> "TITLE_MISMATCH"
    volumeMatches.isEmpty() -> "VOLUME_MISMATCH"
    authorMatches.isEmpty() -> "AUTHOR_MISMATCH"
    authorMatches.singleOrNull()?.thumbnailUrl == null -> "MATCHED_BOOK_WITHOUT_COVER"
    else -> "NOT_FOUND"
  }
  val resolvedIdentifiers = authorMatches.singleOrNull()?.isbns.orEmpty()
    .mapNotNull { it.toResolvedIsbn(BookIdentifierRelation.SAME_WORK, GOOGLE_BOOKS_PROVIDER) }
    .distinctBy { "${it.type}:${it.value}" }
  return TracedCoverLookupResult(
    lookup = lookup,
    step = CoverLookupTraceStep(
      provider = GOOGLE_BOOKS_PROVIDER,
      status = lookup.status,
      reason = reason,
      operation = "BIBLIOGRAPHIC_SEARCH",
      httpStatus = response.httpStatus,
      responseBytes = response.responseBytes,
      candidateCount = response.candidates.size,
      coverCandidateCount = response.candidates.count { it.thumbnailUrl != null },
      titleMatchCount = titleMatches.size,
      volumeMatchCount = volumeMatches.size,
      authorMatchCount = authorMatches.size,
      attributes = mapOf(
        "searchMode" to "TITLE_AUTHOR",
        "matchRelation" to BookIdentifierRelation.SAME_WORK.name,
        "requestAttempts" to response.requestAttempts.toString(),
      ),
    ),
    resolvedIdentifiers = resolvedIdentifiers,
  )
}

private data class GoogleBooksSearchResponse(
  val candidates: List<GoogleBooksCoverCandidate>,
  val httpStatus: Int?,
  val responseBytes: Int,
  val requestAttempts: Int,
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

private fun HttpResponse.retryAfterSeconds(): Long? = headers.entries
  .firstOrNull { (name, _) -> name.equals("Retry-After", ignoreCase = true) }
  ?.value
  ?.firstOrNull()
  ?.trim()
  ?.toLongOrNull()

internal fun isRetryableGoogleBooksStatus(statusCode: Int): Boolean =
  statusCode == 408 || statusCode == 429 || statusCode in 500..599

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
private fun String.escapeSearchValue(): String = replace("\\", "\\\\").replace("\"", "\\\"")

private const val SEARCH_URL = "https://www.googleapis.com/books/v1/volumes"
internal const val GOOGLE_BOOKS_PROVIDER = "GOOGLE_BOOKS"
private const val ISBN_RESULT_LIMIT = 5
private const val TITLE_RESULT_LIMIT = 10
private const val MAX_IMMEDIATE_REQUEST_ATTEMPTS = 2
private val IMMEDIATE_RETRY_STATUSES = setOf(502, 503, 504)
private val GOOGLE_BOOKS_IMAGE_KEYS = listOf("extraLarge", "large", "medium", "small", "thumbnail", "smallThumbnail")
