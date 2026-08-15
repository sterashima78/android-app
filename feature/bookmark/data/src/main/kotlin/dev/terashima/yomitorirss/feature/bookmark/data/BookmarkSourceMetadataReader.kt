package dev.terashima.yomitorirss.feature.bookmark.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection

data class BookmarkSourceMetadata(
  val url: String,
  val sourceFeedUrl: String,
)

class BookmarkSourceMetadataReader(
  private val database: DatabaseConnection,
) {
  fun find(articleId: String): BookmarkSourceMetadata? = database.readable.rawQuery(
    "SELECT url,source_feed_url FROM articles WHERE id=? AND saved_at IS NOT NULL LIMIT 1",
    arrayOf(articleId),
  ).use { cursor ->
    if (!cursor.moveToFirst()) return@use null
    BookmarkSourceMetadata(
      url = cursor.getString(0),
      sourceFeedUrl = if (cursor.isNull(1)) "" else cursor.getString(1),
    )
  }
}
