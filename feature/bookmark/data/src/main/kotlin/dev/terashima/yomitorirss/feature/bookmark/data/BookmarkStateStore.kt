package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import java.time.Instant

/** Owns the durable state of the Bookmark aggregate. */
internal class BookmarkStateStore(
  private val database: DatabaseConnection,
) {
  fun isBookmarked(articleId: String): Boolean = database.readable.rawQuery(
    "SELECT 1 FROM bookmarks WHERE article_id=? LIMIT 1",
    arrayOf(articleId),
  ).use { cursor -> cursor.moveToFirst() }

  fun save(articleId: String, savedAt: String = Instant.now().toString()): Boolean =
    database.writable.insertWithOnConflict(
      "bookmarks",
      null,
      ContentValues().apply {
        put("article_id", articleId)
        put("saved_at", savedAt)
      },
      SQLiteDatabase.CONFLICT_IGNORE,
    ) != -1L

  fun unsave(articleId: String): Boolean =
    database.writable.delete("bookmarks", "article_id=?", arrayOf(articleId)) > 0
}
