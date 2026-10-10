package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.time.Instant

internal fun YomitoriDatabase.findSummaryTask(id: String): SummaryTaskRecord? = readableDatabase.rawQuery(
  "SELECT * FROM summary_tasks WHERE article_id=?",
  arrayOf(id),
).use { cursor -> if (!cursor.moveToFirst()) null else cursor.summaryTaskRecord() }

internal fun YomitoriDatabase.enqueueSummaryTask(
  id: String,
  forceRefresh: Boolean,
  replaceBookmarkTags: Boolean = false,
): Boolean = localTransaction {
  require(!replaceBookmarkTags || forceRefresh) {
    "Bookmark tag replacement requires a force-refresh summary task"
  }
  rawQuery("SELECT state FROM summary_tasks WHERE article_id=?", arrayOf(id)).use { cursor ->
    if (cursor.moveToFirst() && cursor.getString(0) in setOf(SUMMARY_QUEUED, SUMMARY_RUNNING)) return@localTransaction false
  }
  val refreshMode = when {
    replaceBookmarkTags -> SUMMARY_REFRESH_AND_REPLACE_TAGS
    forceRefresh -> SUMMARY_REFRESH_SUMMARY
    else -> SUMMARY_REFRESH_NONE
  }
  insertWithOnConflict(
    "summary_tasks",
    null,
    values(
      "article_id" to id,
      "state" to SUMMARY_QUEUED,
      "force_refresh" to refreshMode.toString(),
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
  localWrite {
    update(
      "summary_tasks",
      values("state" to SUMMARY_FAILED, "finished_at" to nowIso(), "error" to error.take(500), "progress_stage" to null, "progress_current" to null, "progress_total" to null),
      "article_id=?",
      arrayOf(id),
    )
  }
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

internal fun YomitoriDatabase.listInferenceReadySummaryTasks(): List<SummaryTaskRecord> =
  readableDatabase.rawQuery(
    "SELECT q.* FROM summary_tasks q WHERE q.state=? AND ${summaryInferenceReadyWhereClause()} ORDER BY q.queued_at ASC",
    arrayOf(SUMMARY_QUEUED),
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.summaryTaskRecord()) } }

internal fun YomitoriDatabase.claimSummaryTask(articleId: String, requireInferenceReady: Boolean = true): SummaryTaskRecord? = localTransaction {
    val readiness = if (requireInferenceReady) " AND ${summaryInferenceReadyWhereClause()}" else ""
    val task = rawQuery(
      "SELECT q.* FROM summary_tasks q WHERE q.article_id=? AND q.state=?$readiness LIMIT 1",
      arrayOf(articleId, SUMMARY_QUEUED),
    ).use { cursor -> if (!cursor.moveToFirst()) null else cursor.summaryTaskRecord() } ?: run {
      return@localTransaction null
    }
    val startedAt = nowIso()
    val updated = update(
      "summary_tasks",
      values("state" to SUMMARY_RUNNING, "started_at" to startedAt, "finished_at" to null, "error" to null, "progress_stage" to null, "progress_current" to null, "progress_total" to null),
      "article_id=? AND state=?",
      arrayOf(articleId, SUMMARY_QUEUED),
    )
    if (updated == 1) task.copy(state = SUMMARY_RUNNING, startedAt = startedAt, finishedAt = null, error = null, progressStage = null, progressCurrent = null, progressTotal = null) else null
}

internal fun YomitoriDatabase.claimNextSummaryTask(): SummaryTaskRecord? =
  readableDatabase.rawQuery("SELECT article_id FROM summary_tasks WHERE state=? ORDER BY queued_at ASC LIMIT 1", arrayOf(SUMMARY_QUEUED)).use { cursor ->
    if (!cursor.moveToFirst()) null else claimSummaryTask(cursor.getString(0), requireInferenceReady = false)
  }

internal fun YomitoriDatabase.claimNextInferenceReadySummaryTask(): SummaryTaskRecord? =
  listInferenceReadySummaryTasks().firstOrNull()?.let { claimSummaryTask(it.articleId) }

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
  localWrite {
    update(
      "summary_tasks",
      values("state" to SUMMARY_QUEUED, "started_at" to null, "finished_at" to null, "error" to null, "progress_stage" to null, "progress_current" to null, "progress_total" to null),
      "state=?",
      arrayOf(SUMMARY_RUNNING),
    )
  }
}

internal fun YomitoriDatabase.updateQueuedSummaryTaskProgress(articleId: String, stage: String) {
  localWrite {
    update("summary_tasks", values("progress_stage" to stage, "progress_current" to null, "progress_total" to null), "article_id=? AND state=?", arrayOf(articleId, SUMMARY_QUEUED))
  }
}

internal fun YomitoriDatabase.updateRunningSummaryTaskProgress(articleId: String, stage: String, current: Int? = null, total: Int? = null) {
  localWrite {
    update(
      "summary_tasks",
      values("progress_stage" to stage, "progress_current" to current?.toString(), "progress_total" to total?.toString()),
      "article_id=? AND state=?",
      arrayOf(articleId, SUMMARY_RUNNING),
    )
  }
}

internal fun YomitoriDatabase.completeRunningSummaryTask(articleId: String) {
  localTransaction {
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
  localWrite {
    update("summary_tasks", values("state" to SUMMARY_FAILED, "finished_at" to nowIso(), "error" to error.take(500), "progress_stage" to null, "progress_current" to null, "progress_total" to null), "article_id=? AND state=?", arrayOf(articleId, SUMMARY_RUNNING))
  }
}

internal fun YomitoriDatabase.failQueuedSummaryTask(articleId: String, error: String) {
  localWrite {
    update("summary_tasks", values("state" to SUMMARY_FAILED, "finished_at" to nowIso(), "error" to error.take(500), "progress_stage" to null, "progress_current" to null, "progress_total" to null), "article_id=? AND state=?", arrayOf(articleId, SUMMARY_QUEUED))
  }
}

internal fun YomitoriDatabase.deleteFinishedSummaryTasksBefore(cutoff: String): Int =
  localWrite {
    delete("summary_tasks", "state IN (?,?,?) AND finished_at IS NOT NULL AND julianday(finished_at)<julianday(?)", arrayOf(SUMMARY_COMPLETED, SUMMARY_FAILED, SUMMARY_CANCELLED, cutoff))
  }

internal fun YomitoriDatabase.stopSummaryTask(articleId: String): String? =
  transitionSummaryTask(articleId, SUMMARY_STOPPED, setOf(SUMMARY_QUEUED, SUMMARY_RUNNING))

internal fun YomitoriDatabase.cancelSummaryTask(articleId: String): String? = localTransaction {
  val previousState = transitionSummaryTaskInTransaction(articleId, SUMMARY_CANCELLED, setOf(SUMMARY_QUEUED, SUMMARY_RUNNING, SUMMARY_STOPPED)) ?: return@localTransaction null
  delete("summary_article_content", "article_id=?", arrayOf(articleId))
  previousState
}

internal fun YomitoriDatabase.resumeSummaryTask(articleId: String): Boolean =
  localWrite {
    update(
      "summary_tasks",
      values("state" to SUMMARY_QUEUED, "queued_at" to nowIso(), "started_at" to null, "finished_at" to null, "error" to null, "progress_stage" to null, "progress_current" to null, "progress_total" to null),
      "article_id=? AND state IN (?,?)",
      arrayOf(articleId, SUMMARY_STOPPED, SUMMARY_FAILED),
    ) == 1
  }

internal fun YomitoriDatabase.listFailedSummaryTaskIds(): Set<String> = readableDatabase.rawQuery(
  "SELECT article_id FROM summary_tasks WHERE state=?",
  arrayOf(SUMMARY_FAILED),
).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

internal fun YomitoriDatabase.requeueFailedSummaryTasks(articleIds: Set<String>): Int {
  if (articleIds.isEmpty()) return 0
  return articleIds.chunked(400).sumOf { ids ->
    val placeholders = ids.joinToString(",") { "?" }
    val args = arrayOf(SUMMARY_FAILED, *ids.toTypedArray())
    localWrite {
      update(
        "summary_tasks",
        values("state" to SUMMARY_QUEUED, "queued_at" to nowIso(), "started_at" to null, "finished_at" to null, "error" to null, "progress_stage" to null, "progress_current" to null, "progress_total" to null),
        "state=? AND article_id IN($placeholders)",
        args,
      )
    }
  }
}

private fun YomitoriDatabase.transitionSummaryTask(articleId: String, targetState: String, allowedStates: Set<String>): String? = localTransaction {
  transitionSummaryTaskInTransaction(articleId, targetState, allowedStates)
}

private fun SQLiteDatabase.transitionSummaryTaskInTransaction(articleId: String, targetState: String, allowedStates: Set<String>): String? {
  val currentState = rawQuery("SELECT state FROM summary_tasks WHERE article_id=?", arrayOf(articleId)).use { cursor -> if (!cursor.moveToFirst()) null else cursor.getString(0) }
  if (currentState !in allowedStates) return null
  update("summary_tasks", values("state" to targetState, "finished_at" to nowIso(), "error" to null, "progress_stage" to null, "progress_current" to null, "progress_total" to null), "article_id=?", arrayOf(articleId))
  return currentState
}

internal fun <T> YomitoriDatabase.localWrite(block: SQLiteDatabase.() -> T): T =
  DatabaseConnection(this).localWrite(block)

internal fun <T> YomitoriDatabase.localTransaction(block: SQLiteDatabase.() -> T): T =
  DatabaseConnection(this).localTransaction(block)

internal fun Cursor.summaryTaskRecord(): SummaryTaskRecord {
  val refreshMode = getInt(getColumnIndexOrThrow("force_refresh"))
  return SummaryTaskRecord(
    articleId = text("article_id"),
    state = text("state"),
    forceRefresh = refreshMode != SUMMARY_REFRESH_NONE,
    replaceBookmarkTags = refreshMode == SUMMARY_REFRESH_AND_REPLACE_TAGS,
    queuedAt = text("queued_at"),
    startedAt = nullableText("started_at"),
    finishedAt = nullableText("finished_at"),
    error = nullableText("error"),
    progressStage = nullableText("progress_stage"),
    progressCurrent = nullableInt("progress_current"),
    progressTotal = nullableInt("progress_total"),
  )
}

private fun Cursor.text(name: String): String = getString(getColumnIndexOrThrow(name))
private fun Cursor.nullableText(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
private fun Cursor.nullableInt(name: String): Int? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getInt(it) }
private fun nowIso(): String = Instant.now().toString()
private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}

private const val SUMMARY_REFRESH_NONE = 0
private const val SUMMARY_REFRESH_SUMMARY = 1
private const val SUMMARY_REFRESH_AND_REPLACE_TAGS = 2
