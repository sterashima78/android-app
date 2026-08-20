package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchStatus

internal class SmbCoverCacheCoordinator(
  context: Context,
  private val database: DatabaseConnection,
) {
  private val appContext = context.applicationContext

  fun trim(protectedUrl: String? = null) {
    val evictedUrls = trimSmbBookCoverCache(appContext, protectedUrl)
    evictedUrls.forEach(::markEvicted)
  }

  private fun markEvicted(coverUrl: String) {
    val books = database.readable.rawQuery(
      """
        SELECT source_id, title
        FROM library_items
        WHERE source = ? AND thumbnail_url = ?
      """.trimIndent(),
      arrayOf(LibrarySource.SMB.name, coverUrl),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(cursor.getString(0) to cursor.getString(1))
        }
      }
    }
    if (books.isEmpty()) return

    val now = System.currentTimeMillis()
    database.transaction {
      books.forEach { (sourceId, title) ->
        update(
          "library_items",
          ContentValues().apply { putNull("thumbnail_url") },
          "source = ? AND source_id = ?",
          arrayOf(LibrarySource.SMB.name, sourceId),
        )
        insertWithOnConflict(
          "smb_cover_prefetch_queue",
          null,
          ContentValues().apply {
            put("source_id", sourceId)
            put("title", title)
            put("status", SmbCoverPrefetchStatus.SKIPPED.name)
            put("downloaded_bytes", 0L)
            put("total_bytes", 0L)
            put("message", "表紙キャッシュの200MiB上限によりLRU削除しました")
            put("updated_at", now)
          },
          SQLiteDatabase.CONFLICT_REPLACE,
        )
      }
    }
  }
}
