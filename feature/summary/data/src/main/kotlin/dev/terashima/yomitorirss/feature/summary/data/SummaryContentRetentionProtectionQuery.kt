package dev.terashima.yomitorirss.feature.summary.data

import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ContentRetentionProtectionQuery

/** Summary context の永続化構造を隠し、Content 削除を保護する ID だけを公開する adapter。 */
class SummaryContentRetentionProtectionQuery(
  private val database: DatabaseConnection,
) : ContentRetentionProtectionQuery {
  override fun protectedContentIds(contentIds: Set<String>): Set<String> {
    if (contentIds.isEmpty()) return emptySet()

    return buildSet {
      contentIds.chunked(MAX_CONTENT_IDS_PER_QUERY).forEach { ids ->
        val placeholders = ids.joinToString(",") { "?" }
        val args = (ids + ids).toTypedArray()
        database.readable.rawQuery(
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
          while (cursor.moveToNext()) add(cursor.getString(0))
        }
      }
    }
  }

  private companion object {
    // 同じ ID list を2回 bind するため、SQLite の一般的な999変数上限を十分に下回る値にする。
    const val MAX_CONTENT_IDS_PER_QUERY = 400
  }
}
