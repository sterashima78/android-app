package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import android.database.Cursor
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.time.Instant

internal fun YomitoriDatabase.peekNextSummaryTaskPriority(): LocalAiBackgroundTaskPriority? {
  val hasReadLaterSchema = hasReadLaterBookmarkSchema()
  val sql = if (hasReadLaterSchema) {
    """
      SELECT CASE WHEN EXISTS(
        SELECT 1
        FROM article_folders af
        JOIN bookmark_folders bf ON bf.id = af.folder_id
        WHERE af.article_id = q.article_id AND bf.system_kind = 'read_later'
      ) THEN 1 ELSE 0 END AS read_later
      FROM summary_tasks q
      WHERE q.state=?
      ORDER BY read_later DESC,q.queued_at ASC
      LIMIT 1
    """.trimIndent()
  } else {
    "SELECT 0 AS read_later FROM summary_tasks q WHERE q.state=? ORDER BY q.queued_at ASC LIMIT 1"
  }
  return readableDatabase.rawQuery(sql, arrayOf(SUMMARY_QUEUED)).use { cursor ->
    if (!cursor.moveToFirst()) null else if (cursor.getInt(0) == 1) {
      LocalAiBackgroundTaskPriority.HIGH
    } else {
      LocalAiBackgroundTaskPriority.NORMAL
    }
  }
}

internal fun YomitoriDatabase.claimNextSummaryTaskByPriority(): SummaryTaskRecord? {
  val db = writableDatabase
  db.beginTransaction()
  try {
    val hasReadLaterSchema = hasReadLaterBookmarkSchema()
    val ordering = if (hasReadLaterSchema) {
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
    val task = db.rawQuery(
      "SELECT q.* FROM summary_tasks q WHERE q.state=? ORDER BY $ordering LIMIT 1",
      arrayOf(SUMMARY_QUEUED),
    ).use { cursor -> if (!cursor.moveToFirst()) null else cursor.summaryTaskRecord() } ?: run {
      db.setTransactionSuccessful()
      return null
    }

    val startedAt = Instant.now().toString()
    val updated = db.update(
      "summary_tasks",
      ContentValues().apply {
        put("state", SUMMARY_RUNNING)
        put("started_at", startedAt)
        putNull("finished_at")
        putNull("error")
        putNull("progress_stage")
        putNull("progress_current")
        putNull("progress_total")
      },
      "article_id=? AND state=?",
      arrayOf(task.articleId, SUMMARY_QUEUED),
    )
    db.setTransactionSuccessful()
    return if (updated == 1) {
      task.copy(
        state = SUMMARY_RUNNING,
        startedAt = startedAt,
        finishedAt = null,
        error = null,
        progressStage = null,
        progressCurrent = null,
        progressTotal = null,
      )
    } else {
      null
    }
  } finally {
    db.endTransaction()
  }
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

private fun Cursor.summaryTaskRecord(): SummaryTaskRecord = SummaryTaskRecord(
  articleId = getString(getColumnIndexOrThrow("article_id")),
  state = getString(getColumnIndexOrThrow("state")),
  forceRefresh = getInt(getColumnIndexOrThrow("force_refresh")) == 1,
  queuedAt = getString(getColumnIndexOrThrow("queued_at")),
  startedAt = nullableString("started_at"),
  finishedAt = nullableString("finished_at"),
  error = nullableString("error"),
  progressStage = nullableString("progress_stage"),
  progressCurrent = nullableInt("progress_current"),
  progressTotal = nullableInt("progress_total"),
)

private fun Cursor.nullableString(name: String): String? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getString(index) }

private fun Cursor.nullableInt(name: String): Int? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getInt(index) }
