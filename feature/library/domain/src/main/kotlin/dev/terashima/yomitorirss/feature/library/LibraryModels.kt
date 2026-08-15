package dev.terashima.yomitorirss.feature.library

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

enum class LibrarySource(val label: String) {
  GOOGLE_PLAY_BOOKS("Google Play Books"),
  KINDLE("Kindle"),
  AUDIBLE("Audible"),
  SMB("ファイルサーバ"),
}

data class LibrarySeries(
  val name: String,
  val position: Int?,
  val id: String? = null,
)

data class LibraryBook(
  val source: LibrarySource,
  val sourceId: String,
  val title: String,
  val authors: List<String>,
  val publisher: String?,
  val publishedDate: String?,
  val description: String?,
  val isbn10: String?,
  val isbn13: String?,
  val thumbnailUrl: String?,
  val infoUrl: String?,
  val series: LibrarySeries? = null,
  val automaticSeriesExcluded: Boolean = false,
  val narrators: List<String> = emptyList(),
  val duration: String? = null,
) {
  fun openUrl(): String? {
    if (isKindlePersonalDocument()) {
      val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8)
      return "$KINDLE_PERSONAL_DOCUMENT_OPEN_URI_PREFIX$encodedTitle"
    }

    if (source == LibrarySource.KINDLE) {
      amazonAsin(sourceId)?.let { asin ->
        return "kindle://book/?action=open&asin=$asin"
      }
    }

    infoUrl?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    if (source != LibrarySource.AUDIBLE) return null

    val asin = amazonAsin(sourceId) ?: return null
    return "https://www.audible.co.jp/pd/$asin"
  }
}

fun LibraryBook.isKindlePersonalDocument(): Boolean =
  source == LibrarySource.KINDLE && sourceId.startsWith(KINDLE_PERSONAL_DOCUMENT_SOURCE_ID_PREFIX)

fun kindlePersonalDocumentSourceId(documentId: String): String =
  KINDLE_PERSONAL_DOCUMENT_SOURCE_ID_PREFIX + documentId.trim().uppercase(Locale.ROOT)

private fun amazonAsin(sourceId: String): String? {
  val asin = sourceId.trim().uppercase(Locale.ROOT)
  return asin.takeIf(AMAZON_ASIN::matches)
}

private val AMAZON_ASIN = Regex("^[A-Z0-9]{10}$")
const val KINDLE_PERSONAL_DOCUMENT_SOURCE_ID_PREFIX = "PDOC:"
const val KINDLE_PERSONAL_DOCUMENT_OPEN_URI_PREFIX = "yomitori://kindle-personal-document/open?title="

data class LibrarySourceState(
  val source: LibrarySource,
  val accountLabel: String?,
  val lastSyncedAtEpochMillis: Long?,
)

data class LibrarySnapshot(
  val books: List<LibraryBook>,
  val hiddenBooks: List<LibraryBook>,
  val sourceStates: Map<LibrarySource, LibrarySourceState>,
)

data class LibrarySyncResult(
  val importedCount: Int,
  val syncedAtEpochMillis: Long,
)
