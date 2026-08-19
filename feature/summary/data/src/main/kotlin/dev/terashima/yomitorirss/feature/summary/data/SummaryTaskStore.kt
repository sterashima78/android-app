package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.time.Instant

internal fun YomitoriDatabase.findSummaryTask(id: String): SummaryTaskRecord? = readableDatabase.rawQuery(
  "SELECT * FROM summary_tasks WHERE article_id=?",
  arrayOf(id),
).use { cursor -> if (!cursor.moveToFirst()) null else cursor.summaryTaskRecord() }

internal fun YomitoriDatabase.enqueueSummaryTask(id: String, forceRefresh: Boolean): Boolean = transaction {
  rawQuery("SELECT state FROM summary_tasks WHERE article_id=?", arrayOf(id)).use { cursor ->
    if (cursor.moveToFirst() && cursor.getString(0) in setOf(SUMMARY_QUEUED, SUMMARY_RUNNING)) return@transaction false
  }
  insertWithOnConflict(
    "summary_tasks",
    null,
    values(
      "article_id" to id,
      "state" to SUMMARY_QUEUED,
      "force_refresh" to if (forceRefresh) "1" else "0",
      "queued_at" to nowIso(),
      "started_at" to null,
      "finished_at" to null,
      "error" to null,
      "progress_stage" to null,
      "progress_current" to null,
      "progress_total" to null,
    ),
    SQLiteDatabase.CONFLICT_REPLACE,
  )
  true
}

internal fun YomitoriDatabase.markSummaryTaskFailed(id: String, error: String) {
  writableDatabase.update(
    "summary_tasks",
    values("state" to SUMMARY_FAILED, "finished_at" to nowIso(), "error" to error.take(500), "progress_stage" to null, "progress_current" to null, "progress_total" to null),
    "article_id=?",
    arrayOf(id),
  )
}

internal fun YomitoriDatabase.listSummaryTasks(): List<SummaryTaskRecord> = readableDatabase.rawQuery(
  """
    SELECT q.* FROM summary_tasks q
    WHERE q.state <> 'completed'
    ORDER BY
      CASE q.state
        WHEN 'running' THEN 0
        WHEN 'queued' THEN 1
        WHEN 'stopped' THEN 2
        WHEN 'failed' THEN 3
        WHEN 'completed' THEN 4
        WHEN 'cancelled' THEN 5
        ELSE 6
      END,
      CASE WHEN q.state IN ('running','queued') THEN q.queued_at END ASC,
      COALESCE(q.finished_at,q.started_at,q.queued_at) DESC
    LIMIT 200
  """.trimIndent(),
  null,
).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.summaryTaskRecord()) } }

internal fun YomitoriDatabase.listInferenceReadySummaryTasks(limit: Int = 200): List<SummaryTaskRecord> =
  readableDatabase.rawQuery(
    "SELECT q.* FROM summary_tasks q WHERE q.state=? AND ${summaryInferenceReadyWhereClause()} ORDER BY q.queued_at ASC LIMIT ?",
    arrayOf(SUMMARY_QUEUED, limit.toString()),
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.summaryTaskRecord()) } }

internal fun YomitoriDatabase.claimSummaryTask(articleId: String, requireInferenceReady: Boolean = true): SummaryTaskRecord? {
  val db = writableDatabase
  db.beginTransaction()
  try {
    val readiness = if (requireInferenceReady) " AND ${summaryInferenceReadyWhereClause()}" else ""
    val task = db.rawQuery(
      "SELECT q.* FROM summary_tasks q WHERE q.article_id=? AND q.state=?$readiness LIMIT 1",
      arrayOf(articleId, SUMMARY_QUEUED),
    ).use { cursor -> if (!cursor.moveToFirst()) null else cursor.summaryTaskRecord() } ?: run {
      db.setTransactionSuccessful()
      return null
    }
    val startedAt = nowIso()
    val updated = db.update(
      "summary_tasks",
      values("state" to SUMMARY_RUNNING, "started_at" to startedAt, "finished_at" to null, "error" to null, "progress_stage" to null, "progress_current" to null, "progress_total" to null),
      "article_id=? AND state=?",
      arrayOf(articleId, SUMMARY_QUEUED),
    )
    db.setTransactionSuccessful()
    return if (updated == 1) task.copy(state = SUMMARY_RUNNING, startedAt = startedAt, finishedAt = null, error = null, progressStage = null, progressCurrent = null, progressTotal = null) else null
  } finally {
    db.endTransaction()
  }
}

internal fun YomitoriDatabase.claimNextSummaryTask(): SummaryTaskRecord? =
  readableDatabase.rawQuery("SELECT article_id FROM summary_tasks WHERE state=? ORDER BY queued_at ASC LIMIT 1", arrayOf(SUMMARY_QUEUED)).use { cursor ->
    if (!cursor.moveToFirst()) null else claimSummaryTask(cursor.getString(0), requireInferenceReady = false)
  }

internal fun YomitoriDatabase.claimNextInferenceReadySummaryTask(): SummaryTaskRecord? =
  listInferenceReadySummaryTasks(limit = 1).firstOrNull()?.let { claimSummaryTask(it.articleId) }

internal fun summaryInferenceReadyWhereClause(alias: String = "q"): String =
  """
    (
      ($alias.force_refresh=0 AND EXISTS(
        SELECT 1 FROM article_summaries s WHERE s.article_id=$alias.article_id
      ))
      OR EXISTS(
        SELECT 1 FROM summary_article_content c WHERE c.article_id=$alias.article_id
      )
    )
  """.trimIndent()

internal fun YomitoriDatabase.requeueInterruptedSummaryTasks() {
  writableDatabase.update(
    "summary_tasks",
    values("state" to SUMMARY_QUEUED, "started_at" to null, "finished_at" to null, "error" to null, "progress_stage" to null, "progress_current" to null, "progress_total" to null),
    "state=?",
    arrayOf(SUMMARY_RUNNING),
  )
}

internal fun YomitoriDatabase.updateQueuedSummaryTaskProgress(articleId: String, stage: String) {
  writableDatabase.update("summary_tasks", values("progress_stage" to stage, "progress_current" to null, "progress_total" to null), "article_id=? AND state=?", arrayOf(articleId, SUMMARY_QUEUED))
}

internal fun YomitoriDatabase.updateRunningSummaryTaskProgress(articleId: String, stage: String, current: Int? = null, total: Int? = null) {
  writableDatabase.update(
    "summary_tasks",
    values("progress_stage" to stage, "progress_current" to current?.toString(), "progress_total" to total?.toString()),
    "article_id=? AND state=?",
    arrayOf(articleId, SUMMARY_RUNNING),
  )
}

internal fun YomitoriDatabase.completeRunningSummaryTask(articleId: String) {
  transaction {
    val completed = update(
      "summary_tasks",
      values("state" to SUMMARY_COMPLETED, "finished_at" to nowIso(), "error" to null, "progress_stage" to null, "progress_current" to null, "progress_total" to null),
      "article_id=? AND state=?",
      arrayOf(articleId, SUMMARY_RUNNING),
    )
    if (completed == 1) delete("summary_article_content", "article_id=?", arrayOf(articleId))
  }
}

internal fun YomitoriDatabase.failRunningSummaryTask(articleId: String, error: String) {
  writableDatabase.update("summary_tasks", values("state" to SUMMARY_FAILED, "finished_at" to nowIso(), "error" to error.take(500), "progress_stage" to null, "progress_current" to null, "progress_total" to null), "article_id=? AND state=?", arrayOf(articleId, SUMMARY_RUNNING))
}

internal fun YomitoriDatabase.failQueuedSummaryTask(articleId: String, error: String) {
  writableDatabase.update("summary_tasks", values("state" to SUMMARY_FAILED, "finished_at" to nowIso(), "error" to error.take(500), "progress_stage" to null, "progress_current" to null, "progress_total" to null), "article_id=? AND state=?", arrayOf(articleId, SUMMARY_QUEUED))
}

internal fun YomitoriDatabase.deleteFinishedSummaryTasksBefore(cutoff: String): Int =
  writableDatabase.delete("summary_tasks", "state IN (?,?,?) AND finished_at IS NOT NULL AND julianday(finished_at)<julianday(?)", arrayOf(SUMMARY_COMPLETED, SUMMARY_FAILED, SUMMARY_CANCELLED, cutoff))

internal fun YomitoriDatabase.stopSummaryTask(articleId: String): String? =
  transitionSummaryTask(articleId, SUMMARY_STOPPED, setOf(SUMMARY_QUEUED, SUMMARY_RUNNING))

internal fun YomitoriDatabase.cancelSummaryTask(articleId: String): String? = transaction {
  val previousState = transitionSummaryTaskInTransaction(articleId, SUMMARY_CANCELLED, setOf(SUMMARY_QUEUED, SUMMARY_RUNNING, SUMMARY_STOPPED)) ?: return@transaction null
  delete("summary_article_content", "article_id=?", arrayOf(articleId))
  previousState
}

internal fun YomitoriDatabase.resumeSummaryTask(articleId: String): Boolean =
  writableDatabase.update(
    "summary_tasks",
    values("state" to SUMMARY_QUEUED, "queued_at" to nowIso(), "started_at" to null, "finished_at" to null, "error" to null, "progress_stage" to null, "progress_current" to null, "progress_total" to null),
    "article_id=? AND state IN (?,?)",
    arrayOf(articleId, SUMMARY_STOPPED, SUMMARY_FAILED),
  ) == 1

internal fun YomitoriDatabase.listFailedSummaryTaskIds(): Set<String> = readableDatabase.rawQuery(
  "SELECT article_id FROM summary_tasks WHERE state=?",
  arrayOf(SUMMARY_FAILED),
).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

internal fun YomitoriDatabase.requeueFailedSummaryTasks(articleIds: Set<String>): Int {
  if (articleIds.isEmpty()) return 0
  return articleIds.chunked(400).sumOf { ids ->
    val placeholders = ids.joinToString(",") { "?" }
    val args = arrayOf(SUMMARY_FAILED, *ids.toTypedArray())
    writableDatabase.update(
      "summary_tasks",
      values("state" to SUMMARY_QUEUED, "queued_at" to nowIso(), "started_at" to null, "finished_at" to null, "error" to null, "progress_stage" to null, "progress_current" to null, "progress_total" to null),
      "state=? AND article_id IN($placeholders)",
      args,
    )
  }
}

private fun YomitoriDatabase.transitionSummaryTask(articleId: String, targetState: String, allowedStates: Set<String>): String? = transaction {
  transitionSummaryTaskInTransaction(articleId, targetState, allowedStates)
}

private fun SQLiteDatabase.transitionSummaryTaskInTransaction(articleId: String, targetState: String, allowedStates: Set<String>): String? {
  val currentState = rawQuery("SELECT state FROM summary_tasks WHERE article_id=?", arrayOf(articleId)).use { cursor -> if (!cursor.moveToFirst()) null else cursor.getString(0) }
  if (currentState !in allowedStates) return null
  update("summary_tasks", values("state" to targetState, "finished_at" to nowIso(), "error" to null, "progress_stage" to null, "progress_current" to null, "progress_total" to null), "article_id=?", arrayOf(articleId))
  return currentState
}

private inline fun <T> YomitoriDatabase.transaction(block: SQLiteDatabase.() -> T): T {
  val db = writableDatabase
  db.beginTransaction()
  return try {
    val value = db.block()
    db.setTransactionSuccessful()
    value
  } finally {
    db.endTransaction()
  }
}

internal fun Cursor.summaryTaskRecord(): SummaryTaskRecord = SummaryTaskRecord(
  articleId = text("article_id"),
  state = text("state"),
  forceRefresh = getInt(getColumnIndexOrThrow("force_refresh")) == 1,
  queuedAt = text("queued_at"),
  startedAt = nullableText("started_at"),
  finishedAt = nullableText("finished_at"),
  error = nullableText("error"),
  progressStage = nullableText("progress_stage"),
  progressCurrent = nullableInt("progress_current"),
  progressTotal = nullableInt("progress_total"),
)

private fun Cursor.text(name: String): String = getString(getColumnIndexOrThrow(name))
private fun Cursor.nullableText(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
private fun Cursor.nullableInt(name: String): Int? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getInt(it) }
private fun nowIso(): String = Instant.now().toString()
private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}
