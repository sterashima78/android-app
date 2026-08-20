package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibrarySource

internal class SmbCoverCacheCoordinator(
  context: Context,
  private val database: DatabaseConnection,
) {
  private val appContext = context.applicationContext
  private val queue = SmbCoverPrefetchQueueStore(database)

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

    books.forEach { (sourceId, title) ->
      database.writable.update(
        "library_items",
        ContentValues().apply { putNull("thumbnail_url") },
        "source = ? AND source_id = ?",
        arrayOf(LibrarySource.SMB.name, sourceId),
      )
      queue.markSkipped(
        sourceId = sourceId,
        title = title,
        reason = "表紙キャッシュの200MiB上限によりLRU削除しました",
      )
    }
  }
}
