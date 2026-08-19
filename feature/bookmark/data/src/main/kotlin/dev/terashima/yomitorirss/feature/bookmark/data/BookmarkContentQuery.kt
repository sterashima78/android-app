package dev.terashima.yomitorirss.feature.bookmark.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkContentQuery

class DefaultBookmarkContentQuery(
  private val database: DatabaseConnection,
) : BookmarkContentQuery {
  override fun bookmarkedContentIds(contentIds: Set<String>): Set<String> = queryIds(contentIds) { placeholders ->
    "SELECT article_id FROM bookmarks WHERE article_id IN($placeholders)"
  }

  override fun readLaterContentIds(contentIds: Set<String>): Set<String> = queryIds(contentIds) { placeholders ->
    """
      SELECT af.article_id
      FROM article_folders af
      JOIN bookmark_folders bf ON bf.id=af.folder_id
      WHERE bf.system_kind='read_later' AND af.article_id IN($placeholders)
    """.trimIndent()
  }

  private fun queryIds(
    contentIds: Set<String>,
    sql: (placeholders: String) -> String,
  ): Set<String> {
    if (contentIds.isEmpty()) return emptySet()
    return buildSet {
      contentIds.chunked(QUERY_CHUNK_SIZE).forEach { ids ->
        val placeholders = ids.joinToString(",") { "?" }
        database.readable.rawQuery(sql(placeholders), ids.toTypedArray()).use { cursor ->
          while (cursor.moveToNext()) add(cursor.getString(0))
        }
      }
    }
  }

  private companion object {
    const val QUERY_CHUNK_SIZE = 400
  }
}
