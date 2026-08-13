package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.IOException
import org.json.JSONArray

class KindleCoverEnrichmentRepository(
  private val database: DatabaseConnection,
) {
  private val amazonCoverClient = KindleAmazonCoverClient()
  private val openLibraryCoverClient = OpenLibraryCoverClient()
  private var schemaEnsured = false

  suspend fun enrichNext(): Boolean {
    ensureSchema()
    if (!isEnabled()) return false
    val book = queryCandidate() ?: return false
    saveLookup(book, lookup(book))
    return true
  }

  private suspend fun lookup(book: LibraryBook): KindleCoverLookupResult {
    var amazonFailure: IOException? = null
    val amazon = try {
      amazonCoverClient.lookup(book.sourceId)
    } catch (error: IOException) {
      amazonFailure = error
      null
    }
    if (amazon?.lookup?.status == CoverLookupStatus.FOUND) return amazon

    val openLibrary = openLibraryCoverClient.lookup(book)
    if (amazonFailure != null && openLibrary.status != CoverLookupStatus.FOUND) {
      throw amazonFailure
    }
    return KindleCoverLookupResult(
      lookup = openLibrary,
      provider = KindleCoverProvider.OPEN_LIBRARY,
    )
  }
