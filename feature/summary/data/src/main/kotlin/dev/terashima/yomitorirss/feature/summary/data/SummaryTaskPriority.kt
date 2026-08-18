package dev.terashima.yomitorirss.feature.summary.data

import android.database.Cursor
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import dev.terashima.yomitorirss.core.database.YomitoriDatabase

internal fun YomitoriDatabase.peekNextSummaryTaskPriority(): LocalAiBackgroundTaskPriority? {
  val hasReadLaterSchema = hasReadLaterBookmarkSchema()
  val readiness = summaryInferenceReadyWhereClause()
  val sql = if (hasReadLaterSchema) {
    """
      SELECT CASE WHEN EXISTS(
        SELECT 1
        FROM article_folders af
        JOIN bookmark_folders bf ON bf.id = af.folder_id
        WHERE af.article_id = q.article_id AND bf.system_kind = 'read_later'
      ) THEN 1 ELSE 0 END AS read_later
      FROM summary_tasks q
      WHERE q.state=? AND $readiness
      ORDER BY read_later DESC,q.queued_at ASC
      LIMIT 1
    """.trimIndent()
  } else {
    "SELECT 0 AS read_later FROM summary_tasks q WHERE q.state=? AND $readiness ORDER BY q.queued_at ASC LIMIT 1"
  }
  return readableDatabase.rawQuery(sql, arrayOf(SUMMARY_QUEUED)).use { cursor ->
    if (!cursor.moveToFirst()) null else if (cursor.getInt(0) == 1) {
      LocalAiBackgroundTaskPriority.HIGH
    } else {
      LocalAiBackgroundTaskPriority.NORMAL
    }
  }
}

internal fun YomitoriDatabase.claimNextSummaryTaskByPriority(): SummaryTaskRecord? =
  claimNextInferenceReadySummaryTask()

internal fun YomitoriDatabase.summaryTaskPriorityOrderByClause(): String =
  if (hasReadLaterBookmarkSchema()) {
    """
      CASE WHEN EXISTS(
        SELECT 1
        FROM article_folders af
        JOIN bookmark_folders bf ON bf.id = af.folder_id
        WHERE af.article_id = q.article_id AND bf.system_kind = 'read_later'
      ) THEN 0 ELSE 1 END,
      q.queued_at ASC
    """.trimIndent()
  } else {
    "q.queued_at ASC"
  }

internal fun YomitoriDatabase.readLaterSummaryTaskIds(articleIds: List<String>): Set<String> {
  if (articleIds.isEmpty() || !hasReadLaterBookmarkSchema()) return emptySet()
  val placeholders = articleIds.joinToString(",") { "?" }
  return readableDatabase.rawQuery(
    """
      SELECT DISTINCT af.article_id
      FROM article_folders af
      JOIN bookmark_folders bf ON bf.id = af.folder_id
      WHERE bf.system_kind = 'read_later' AND af.article_id IN($placeholders)
    """.trimIndent(),
    articleIds.toTypedArray(),
  ).use { cursor ->
    buildSet {
      while (cursor.moveToNext()) add(cursor.getString(0))
    }
  }
}

private fun YomitoriDatabase.hasReadLaterBookmarkSchema(): Boolean =
  tableExists("article_folders") && tableExists("bookmark_folders")

private fun YomitoriDatabase.tableExists(name: String): Boolean = readableDatabase.rawQuery(
  "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
  arrayOf(name),
).use(Cursor::moveToFirst)
