package dev.terashima.yomitorirss.feature.bookmark.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.resolveContentType
import dev.terashima.yomitorirss.feature.article.toContentTypeOrNull

data class BookmarkSourceMetadata(
  val url: String,
  val sourceFeedUrl: String,
  val effectiveContentType: ContentType,
)

class BookmarkSourceMetadataReader(
  private val database: DatabaseConnection,
) {
  fun find(articleId: String): BookmarkSourceMetadata? = database.readable.rawQuery(
    """
      SELECT
        a.url,
        a.source_feed_url,
        a.content_type AS article_content_type,
        f.content_type AS feed_content_type,
        ff.content_type AS folder_content_type
      FROM articles a
      LEFT JOIN feeds f ON f.id = a.feed_id
      LEFT JOIN feed_folders ff ON ff.id = f.folder_id
      WHERE a.id=? AND a.saved_at IS NOT NULL
      LIMIT 1
    """.trimIndent(),
    arrayOf(articleId),
  ).use { cursor ->
    if (!cursor.moveToFirst()) return@use null
    val articleType = cursor.nullableString("article_content_type").toContentTypeOrNull()
    val feedType = cursor.nullableString("feed_content_type").toContentTypeOrNull()
    val folderType = cursor.nullableString("folder_content_type").toContentTypeOrNull()
    BookmarkSourceMetadata(
      url = cursor.getString(cursor.getColumnIndexOrThrow("url")),
      sourceFeedUrl = cursor.nullableString("source_feed_url").orEmpty(),
      effectiveContentType = resolveContentType(articleType, feedType, folderType),
    )
  }
}

private fun android.database.Cursor.nullableString(name: String): String? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getString(index) }
