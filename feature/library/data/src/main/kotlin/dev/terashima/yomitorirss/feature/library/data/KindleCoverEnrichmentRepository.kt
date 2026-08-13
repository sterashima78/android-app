package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import java.io.IOException
import kotlinx.coroutines.delay
import org.json.JSONArray

class KindleCoverEnrichmentRepository(
  private val database: DatabaseConnection,
  private val amazonCoverClient: KindleAmazonCoverClient = KindleAmazonCoverClient(),
  private val openLibraryCoverClient: OpenLibraryCoverClient = OpenLibraryCoverClient(),
) {
  private var schemaEnsured = false

  suspend fun enrichBatch(limit: Int = KINDLE_COVER_BATCH_SIZE): Boolean {
    require(limit > 0) { "表紙補完の処理件数は1件以上で指定してください" }
    ensureSchema()
    if (!isEnabled()) return false

    val books = queryCandidates(limit)
    books.forEachIndexed { index, book ->
      saveLookup(book, lookup(book))
      if (index < books.lastIndex) delay(COVER_REQUEST_DELAY_MILLIS)
    }
    return books.size == limit
  }

  private suspend fun lookup(book: LibraryBook): KindleCoverLookupResult {
    try {
      val amazon = amazonCoverClient.lookup(book.sourceId)
      if (amazon.lookup.status == CoverLookupStatus.FOUND) return amazon
    } catch (_: IOException) {
      // Amazon 商品ページの取得・解析に失敗しても Open Library を試す。
    }

    return KindleCoverLookupResult(
      lookup = openLibraryCoverClient.lookup(book),
      provider = KindleCoverProvider.OPEN_LIBRARY,
    )
  }
