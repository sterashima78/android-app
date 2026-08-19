package dev.terashima.yomitorirss.feature.summary.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ContentRetentionProtectionQuery

/** Summary context の永続化構造を隠し、Content 削除を保護する ID だけを公開する adapter。 */
class SummaryContentRetentionProtectionQuery(
  private val database: DatabaseConnection,
) : ContentRetentionProtectionQuery {
  override suspend fun protectedContentIds(contentIds: Set<String>): Set<String> {
    if (contentIds.isEmpty()) return emptySet()

    val ids = contentIds.toList()
    val placeholders = ids.joinToString(",") { "?" }
    val args = (ids + ids).toTypedArray()
    return database.readable.rawQuery(
      """
        SELECT article_id
        FROM article_summaries
        WHERE article_id IN ($placeholders)
        UNION
        SELECT article_id
        FROM summary_tasks
        WHERE article_id IN ($placeholders)
          AND state IN ('queued','running')
      """.trimIndent(),
      args,
    ).use { cursor ->
      buildSet {
        while (cursor.moveToNext()) add(cursor.getString(0))
      }
    }
  }
}
