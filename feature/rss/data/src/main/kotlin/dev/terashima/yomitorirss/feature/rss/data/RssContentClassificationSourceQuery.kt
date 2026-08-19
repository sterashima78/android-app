package dev.terashima.yomitorirss.feature.rss.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ContentClassificationSourceQuery
import dev.terashima.yomitorirss.feature.article.SourceContentTypeOverrides
import dev.terashima.yomitorirss.feature.article.toContentTypeOrNull

/** RSS context が所有する Feed / FeedFolder の分類 override だけを Content context へ公開する adapter。 */
class RssContentClassificationSourceQuery(
  private val database: DatabaseConnection,
) : ContentClassificationSourceQuery {
  override suspend fun findOverrides(sourceIds: Set<String>): Map<String, SourceContentTypeOverrides> {
    if (sourceIds.isEmpty()) return emptyMap()

    val ids = sourceIds.toList()
    val placeholders = ids.joinToString(",") { "?" }
    return database.readable.rawQuery(
      """
        SELECT
          f.id,
          f.content_type AS source_content_type,
          ff.content_type AS source_container_content_type
        FROM feeds f
        LEFT JOIN feed_folders ff ON ff.id = f.folder_id
        WHERE f.id IN ($placeholders)
      """.trimIndent(),
      ids.toTypedArray(),
    ).use { cursor ->
      buildMap {
        while (cursor.moveToNext()) {
          val sourceId = cursor.getString(cursor.getColumnIndexOrThrow("id"))
          put(
            sourceId,
            SourceContentTypeOverrides(
              sourceOverride = cursor.nullableString("source_content_type").toContentTypeOrNull(),
              sourceContainerOverride = cursor.nullableString("source_container_content_type").toContentTypeOrNull(),
            ),
          )
        }
      }
    }
  }
}

private fun android.database.Cursor.nullableString(name: String): String? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getString(index) }
