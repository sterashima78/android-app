package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.feature.library.LibraryBook
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

internal class NdlSearchBibliographicClient(
  private val httpClient: HttpClient = HttpClient.create(),
) {
  suspend fun lookupByTitle(book: LibraryBook): TracedCoverLookupResult {
    val searchableTitle = searchableBookTitle(book.title)
    val expectedTitle = normalizedSearchableBookTitle(searchableTitle)
    if (expectedTitle.isEmpty()) {
      return result(CoverLookupStatus.NOT_FOUND, "EMPTY_TITLE")
    }

    val author = book.authors.firstOrNull()?.trim()?.takeIf(String::isNotEmpty)
    val url = buildString {
      append(SEARCH_URL)
      append("?cnt=")
      append(TITLE_RESULT_LIMIT)
      append("&mediatype=books&title=")
      append(searchableTitle.urlEncode())
      author?.let {
        append("&creator=")
        append(it.urlEncode())
      }
    }
    val response = httpClient.execute(
      HttpRequest(url = url, headers = mapOf("Accept" to "application/rss+xml, application/xml, text/xml")),
    )
    if (!response.isSuccessful) {
      val retryable = isRetryableNdlSearchStatus(response.statusCode)
      val step = CoverLookupTraceStep(
        provider = NDL_SEARCH_PROVIDER,
        status = CoverLookupStatus.ERROR,
        reason = if (retryable) "HTTP_RETRYABLE" else "HTTP_ERROR",
        operation = "BIBLIOGRAPHIC_SEARCH",
        retryable = retryable,
        retryAfterSeconds = response.retryAfterSeconds(),
        httpStatus = response.statusCode,
        responseBytes = response.body.size,
      )
      if (retryable) {
        throw CoverProviderIOException(
          "NDL Search API の一時的な検索エラー (${response.statusCode})",
          step,
        )
      }
      return TracedCoverLookupResult(CoverLookupResult(CoverLookupStatus.ERROR), step)
    }
    if (response.body.size > MAX_RESPONSE_BYTES) {
      throw CoverProviderIOException(
        "NDL Search API の応答が大きすぎます",
        CoverLookupTraceStep(
          provider = NDL_SEARCH_PROVIDER,
          status = CoverLookupStatus.ERROR,
          reason = "RESPONSE_TOO_LARGE",
          operation = "BIBLIOGRAPHIC_SEARCH",
          retryable = true,
          httpStatus = response.statusCode,
          responseBytes = response.body.size,
        ),
      )
    }

    val candidates = runCatching { parseNdlCandidates(response.body) }
      .getOrElse { error ->
        throw CoverProviderIOException(
          "NDL Search API の応答を解析できませんでした",
          CoverLookupTraceStep(
            provider = NDL_SEARCH_PROVIDER,
            status = CoverLookupStatus.ERROR,
            reason = "PARSE_ERROR",
            operation = "BIBLIOGRAPHIC_SEARCH",
            retryable = true,
            httpStatus = response.statusCode,
            responseBytes = response.body.size,
          ),
          error,
        )
      }

    val titleMatches = candidates.filter { candidate ->
      normalizeBookText(candidate.title) == expectedTitle
    }
    val resolvedIsbns = titleMatches.flatMap(NdlSearchCandidate::isbns).distinct()
    val status = if (resolvedIsbns.size > 1) CoverLookupStatus.AMBIGUOUS else CoverLookupStatus.NOT_FOUND
    val reason = when {
      titleMatches.isEmpty() -> "TITLE_MISMATCH"
      resolvedIsbns.isEmpty() -> "MATCHED_BOOK_WITHOUT_ISBN"
      resolvedIsbns.size == 1 -> "ISBN_RESOLVED"
      else -> "MULTIPLE_ISBN_MATCHES"
    }
    val identifiers = resolvedIsbns.singleOrNull()
      ?.toResolvedIsbn(BookIdentifierRelation.SAME_WORK, NDL_SEARCH_PROVIDER)
      ?.let(::listOf)
      .orEmpty()
    return TracedCoverLookupResult(
      lookup = CoverLookupResult(status),
      step = CoverLookupTraceStep(
        provider = NDL_SEARCH_PROVIDER,
        status = status,
        reason = reason,
        operation = "BIBLIOGRAPHIC_SEARCH",
        httpStatus = response.statusCode,
        responseBytes = response.body.size,
        candidateCount = candidates.size,
        titleMatchCount = titleMatches.size,
        attributes = mapOf(
          "searchMode" to if (author == null) "TITLE" else "TITLE_AUTHOR",
          "matchRelation" to BookIdentifierRelation.SAME_WORK.name,
        ),
      ),
      resolvedIdentifiers = identifiers,
    )
  }

  private fun result(status: CoverLookupStatus, reason: String) = TracedCoverLookupResult(
    lookup = CoverLookupResult(status),
    step = CoverLookupTraceStep(
      provider = NDL_SEARCH_PROVIDER,
      status = status,
      reason = reason,
      operation = "BIBLIOGRAPHIC_SEARCH",
    ),
  )
}

internal fun isLikelyJapaneseBookTitle(value: String): Boolean =
  JAPANESE_CHARACTER.containsMatchIn(value)

internal fun isRetryableNdlSearchStatus(statusCode: Int): Boolean =
  statusCode == 408 || statusCode == 429 || statusCode in 500..599

private fun parseNdlCandidates(body: ByteArray): List<NdlSearchCandidate> {
  val xml = body.toString(Charsets.UTF_8)
  require(!xml.contains("<!DOCTYPE", ignoreCase = true)) { "DOCTYPE is not allowed" }

  val factory = DocumentBuilderFactory.newInstance().apply {
    isNamespaceAware = false
    isExpandEntityReferences = false
    runCatching { isXIncludeAware = false }
    runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
  }
  val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(body))
  val nodes = document.getElementsByTagName("item")
  return buildList {
    for (index in 0 until nodes.length) {
      val element = nodes.item(index) as? Element ?: continue
      val title = element.firstChildText("title") ?: continue
      val isbns = extractIsbn13Values(element.textContent.orEmpty())
      add(NdlSearchCandidate(title = title.substringBefore(" / ").trim(), isbns = isbns))
    }
  }
}

private fun Element.firstChildText(name: String): String? {
  val nodes = getElementsByTagName(name)
  if (nodes.length == 0) return null
  return nodes.item(0)?.textContent?.trim()?.takeIf(String::isNotEmpty)
}

private fun extractIsbn13Values(value: String): List<String> = ISBN13_CANDIDATE.findAll(
  Normalizer.normalize(value, Normalizer.Form.NFKC),
).mapNotNull { match ->
  match.value.cleanBookIsbn()?.takeIf(::isValidIsbn13)
}.distinct().toList()

private fun isValidIsbn13(value: String): Boolean {
  if (value.length != 13 || value.any { !it.isDigit() }) return false
  val sum = value.take(12).mapIndexed { index, char ->
    char.digitToInt() * if (index % 2 == 0) 1 else 3
  }.sum()
  return (10 - sum % 10) % 10 == value.last().digitToInt()
}

private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())

private fun dev.terashima.yomitorirss.core.network.HttpResponse.retryAfterSeconds(): Long? = headers.entries
  .firstOrNull { (name, _) -> name.equals("Retry-After", ignoreCase = true) }
  ?.value
  ?.firstOrNull()
  ?.trim()
  ?.toLongOrNull()

private data class NdlSearchCandidate(
  val title: String,
  val isbns: List<String>,
)

internal const val NDL_SEARCH_PROVIDER = "NDL_SEARCH"
private const val SEARCH_URL = "https://ndlsearch.ndl.go.jp/api/opensearch"
private const val TITLE_RESULT_LIMIT = 10
private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024
private val JAPANESE_CHARACTER = Regex("[\\u3040-\\u30ff\\u3400-\\u9fff]")
private val ISBN13_CANDIDATE = Regex("(?<![0-9])97[89][0-9\\-\\s]{9,24}[0-9](?![0-9])")
