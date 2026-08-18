package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.time.Instant

internal data class SummaryRecord(
  val articleId: String,
  val summary: String,
  val modelId: String,
  val createdAt: String,
)

internal data class SummaryTaskRecord(
  val articleId: String,
  val state: String,
  val forceRefresh: Boolean,
  val queuedAt: String,
  val startedAt: String?,
  val finishedAt: String?,
  val error: String?,
  val progressStage: String?,
  val progressCurrent: Int?,
  val progressTotal: Int?,
)

internal data class SummaryTaskListItem(
  val task: SummaryTaskRecord,
  val articleTitle: String,
  val sourceTitle: String,
)

internal data class SummaryArticle(
  val id: String,
  val url: String,
  val title: String,
)

internal data class PreparedSummaryArticleContent(
  val articleId: String,
  val content: String,
  val fetchedAt: String,
)

internal const val SUMMARY_QUEUED = "queued"
internal const val SUMMARY_RUNNING = "running"
internal const val SUMMARY_COMPLETED = "completed"
internal const val SUMMARY_FAILED = "failed"
internal const val SUMMARY_STOPPED = "stopped"
internal const val SUMMARY_CANCELLED = "cancelled"

internal const val SUMMARY_PROGRESS_FETCHING_ARTICLE = "fetching_article"
internal const val SUMMARY_PROGRESS_PREPARING_MODEL = "preparing_model"
internal const val SUMMARY_PROGRESS_GENERATING_SUMMARY = "generating_summary"
internal const val SUMMARY_PROGRESS_SUMMARIZING_CHUNK = "summarizing_chunk"
internal const val SUMMARY_PROGRESS_REDUCING_SUMMARY = "reducing_summary"
internal const val SUMMARY_PROGRESS_FINALIZING_SUMMARY = "finalizing_summary"

internal fun YomitoriDatabase.findArticle(id: String): SummaryArticle? = readableDatabase.rawQuery(
  "SELECT id,url,title FROM articles WHERE id=? LIMIT 1",
  arrayOf(id),
).use { cursor ->
  if (!cursor.moveToFirst()) null else SummaryArticle(cursor.getString(0), cursor.getString(1), cursor.getString(2))
}

internal fun YomitoriDatabase.findSummary(id: String): SummaryRecord? = readableDatabase.rawQuery(
  "SELECT article_id,summary,model_id,created_at FROM article_summaries WHERE article_id=?",
  arrayOf(id),
).use { cursor ->
  if (!cursor.moveToFirst()) null else SummaryRecord(
    articleId = cursor.text("article_id"),
    summary = cursor.text("summary"),
    modelId = cursor.text("model_id"),
    createdAt = cursor.text("created_at"),
  )
}

internal fun YomitoriDatabase.saveSummary(id: String, text: String, model: String) {
  writableDatabase.insertWithOnConflict(
    "article_summaries",
    null,
    values("article_id" to id, "summary" to text, "model_id" to model, "created_at" to nowIso()),
    SQLiteDatabase.CONFLICT_REPLACE,
  )
}

internal fun YomitoriDatabase.findPreparedSummaryArticleContent(articleId: String): PreparedSummaryArticleContent? =
  readableDatabase.rawQuery(
    "SELECT article_id,content,fetched_at FROM summary_article_content WHERE article_id=? LIMIT 1",
    arrayOf(articleId),
  ).use { cursor ->
    if (!cursor.moveToFirst()) null else PreparedSummaryArticleContent(
      articleId = cursor.text("article_id"),
      content = cursor.text("content"),
      fetchedAt = cursor.text("fetched_at"),
    )
  }

internal fun YomitoriDatabase.nextSummaryArticleForContentFetch(): SummaryArticle? {
  val orderBy = summaryTaskPriorityOrderByClause()
  return readableDatabase.rawQuery(
    """
      SELECT a.id,a.url,a.title
      FROM summary_tasks q
      JOIN articles a ON a.id = q.article_id
      WHERE q.state=?
        AND (q.force_refresh=1 OR NOT EXISTS(
          SELECT 1 FROM article_summaries s WHERE s.article_id=q.article_id
        ))
        AND NOT EXISTS(
          SELECT 1 FROM summary_article_content c WHERE c.article_id=q.article_id
        )
      ORDER BY $orderBy
      LIMIT 1
    """.trimIndent(),
    arrayOf(SUMMARY_QUEUED),
  ).use { cursor ->
    if (!cursor.moveToFirst()) null else SummaryArticle(cursor.getString(0), cursor.getString(1), cursor.getString(2))
  }
}

internal fun YomitoriDatabase.countPreparedSummaryArticleContentsForActiveTasks(): Int =
  readableDatabase.rawQuery(
    """
      SELECT COUNT(*)
      FROM summary_article_content c
      JOIN summary_tasks q ON q.article_id = c.article_id
      WHERE q.state IN (?,?)
    """.trimIndent(),
    arrayOf(SUMMARY_QUEUED, SUMMARY_RUNNING),
  ).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getInt(0)
  }

internal fun YomitoriDatabase.savePreparedSummaryArticleContentIfQueued(
  articleId: String,
  content: String,
): Boolean = transaction {
  val stillQueued = rawQuery(
    "SELECT 1 FROM summary_tasks WHERE article_id=? AND state=? LIMIT 1",
    arrayOf(articleId, SUMMARY_QUEUED),
  ).use(Cursor::moveToFirst)
  if (!stillQueued) return@transaction false

  insertWithOnConflict(
    "summary_article_content",
    null,
    values(
      "article_id" to articleId,
      "content" to content,
      "fetched_at" to nowIso(),
    ),
    SQLiteDatabase.CONFLICT_REPLACE,
  )
  update(
    "summary_tasks",
    values(
      "progress_stage" to null,
      "progress_current" to null,
      "progress_total" to null,
    ),
    "article_id=? AND state=?",
    arrayOf(articleId, SUMMARY_QUEUED),
  )
  true
}

internal fun YomitoriDatabase.findSummaryTask(id: String): SummaryTaskRecord? = readableDatabase.rawQuery(
  "SELECT * FROM summary_tasks WHERE article_id=?",
  arrayOf(id),
).use { cursor -> if (!cursor.moveToFirst()) null else cursor.summaryTask() }

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
    values(
      "state" to SUMMARY_FAILED,
      "finished_at" to nowIso(),
      "error" to error.take(500),
      "progress_stage" to null,
      "progress_current" to null,
      "progress_total" to null,
    ),
    "article_id=?",
    arrayOf(id),
  )
}

internal fun YomitoriDatabase.listSummaryTaskItems(): List<SummaryTaskListItem> =
  readableDatabase.rawQuery(
    """
      SELECT q.*, a.title AS article_title, a.source_title AS article_source_title
      FROM summary_tasks q
      JOIN articles a ON a.id = q.article_id
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
        CASE WHEN q.state IN ('running', 'queued') THEN q.queued_at END ASC,
        COALESCE(q.finished_at, q.started_at, q.queued_at) DESC
      LIMIT 200
    """.trimIndent(),
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(
          SummaryTaskListItem(
            task = cursor.summaryTask(),
            articleTitle = cursor.text("article_title"),
            sourceTitle = cursor.text("article_source_title"),
          ),
        )
      }
    }
  }

internal fun YomitoriDatabase.claimNextSummaryTask(): SummaryTaskRecord? =
  claimNextSummaryTaskMatching(extraWhere = null)

internal fun YomitoriDatabase.claimNextInferenceReadySummaryTask(): SummaryTaskRecord? =
  claimNextSummaryTaskMatching(extraWhere = summaryInferenceReadyWhereClause())

private fun YomitoriDatabase.claimNextSummaryTaskMatching(extraWhere: String?): SummaryTaskRecord? {
  val orderBy = summaryTaskPriorityOrderByClause()
  val db = writableDatabase
  db.beginTransaction()
  try {
    val readiness = extraWhere?.let { " AND $it" }.orEmpty()
    val task = db.rawQuery(
      "SELECT q.* FROM summary_tasks q WHERE q.state=?$readiness ORDER BY $orderBy LIMIT 1",
      arrayOf(SUMMARY_QUEUED),
    ).use { cursor -> if (!cursor.moveToFirst()) null else cursor.summaryTask() } ?: run {
      db.setTransactionSuccessful()
      return null
    }

    val startedAt = nowIso()
    val updated = db.update(
      "summary_tasks",
      values(
        "state" to SUMMARY_RUNNING,
        "started_at" to startedAt,
        "finished_at" to null,
        "error" to null,
        "progress_stage" to null,
        "progress_current" to null,
        "progress_total" to null,
      ),
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
    values(
      "state" to SUMMARY_QUEUED,
      "started_at" to null,
      "finished_at" to null,
      "error" to null,
      "progress_stage" to null,
      "progress_current" to null,
      "progress_total" to null,
    ),
    "state=?",
    arrayOf(SUMMARY_RUNNING),
  )
}

internal fun YomitoriDatabase.updateQueuedSummaryTaskProgress(
  articleId: String,
  stage: String,
) {
  writableDatabase.update(
    "summary_tasks",
    values(
      "progress_stage" to stage,
      "progress_current" to null,
      "progress_total" to null,
    ),
    "article_id=? AND state=?",
    arrayOf(articleId, SUMMARY_QUEUED),
  )
}

internal fun YomitoriDatabase.updateRunningSummaryTaskProgress(
  articleId: String,
  stage: String,
  current: Int? = null,
  total: Int? = null,
) {
  writableDatabase.update(
    "summary_tasks",
    values(
      "progress_stage" to stage,
      "progress_current" to current?.toString(),
      "progress_total" to total?.toString(),
    ),
    "article_id=? AND state=?",
    arrayOf(articleId, SUMMARY_RUNNING),
  )
}

internal fun YomitoriDatabase.completeRunningSummaryTask(articleId: String) {
  transaction {
    val completed = update(
      "summary_tasks",
      values(
        "state" to SUMMARY_COMPLETED,
        "finished_at" to nowIso(),
        "error" to null,
        "progress_stage" to null,
        "progress_current" to null,
        "progress_total" to null,
      ),
      "article_id=? AND state=?",
      arrayOf(articleId, SUMMARY_RUNNING),
    )
    if (completed == 1) {
      delete("summary_article_content", "article_id=?", arrayOf(articleId))
    }
  }
}

internal fun YomitoriDatabase.failRunningSummaryTask(articleId: String, error: String) {
  writableDatabase.update(
    "summary_tasks",
    values(
      "state" to SUMMARY_FAILED,
      "finished_at" to nowIso(),
      "error" to error.take(500),
      "progress_stage" to null,
      "progress_current" to null,
      "progress_total" to null,
    ),
    "article_id=? AND state=?",
    arrayOf(articleId, SUMMARY_RUNNING),
  )
}

internal fun YomitoriDatabase.failQueuedSummaryTask(articleId: String, error: String) {
  writableDatabase.update(
    "summary_tasks",
    values(
      "state" to SUMMARY_FAILED,
      "finished_at" to nowIso(),
      "error" to error.take(500),
      "progress_stage" to null,
      "progress_current" to null,
      "progress_total" to null,
    ),
    "article_id=? AND state=?",
    arrayOf(articleId, SUMMARY_QUEUED),
  )
}

internal fun YomitoriDatabase.deleteFinishedSummaryTasksBefore(cutoff: String): Int =
  writableDatabase.delete(
    "summary_tasks",
    "state IN (?,?,?) AND finished_at IS NOT NULL AND julianday(finished_at) < julianday(?)",
    arrayOf(SUMMARY_COMPLETED, SUMMARY_FAILED, SUMMARY_CANCELLED, cutoff),
  )

internal fun YomitoriDatabase.stopSummaryTask(articleId: String): String? = transitionSummaryTask(
  articleId = articleId,
  targetState = SUMMARY_STOPPED,
  allowedStates = setOf(SUMMARY_QUEUED, SUMMARY_RUNNING),
)

internal fun YomitoriDatabase.cancelSummaryTask(articleId: String): String? = transitionSummaryTask(
  articleId = articleId,
  targetState = SUMMARY_CANCELLED,
  allowedStates = setOf(SUMMARY_QUEUED, SUMMARY_RUNNING, SUMMARY_STOPPED),
)

internal fun YomitoriDatabase.resumeSummaryTask(articleId: String): Boolean {
  return writableDatabase.update(
    "summary_tasks",
    values(
      "state" to SUMMARY_QUEUED,
      "queued_at" to nowIso(),
      "started_at" to null,
      "finished_at" to null,
      "error" to null,
      "progress_stage" to null,
      "progress_current" to null,
      "progress_total" to null,
    ),
    "article_id=? AND state IN (?,?)",
    arrayOf(articleId, SUMMARY_STOPPED, SUMMARY_FAILED),
  ) == 1
}

private fun YomitoriDatabase.transitionSummaryTask(
  articleId: String,
  targetState: String,
  allowedStates: Set<String>,
): String? = transaction {
  val currentState = rawQuery(
    "SELECT state FROM summary_tasks WHERE article_id=?",
    arrayOf(articleId),
  ).use { cursor -> if (!cursor.moveToFirst()) null else cursor.getString(0) }
  if (currentState !in allowedStates) return@transaction null

  update(
    "summary_tasks",
    values(
      "state" to targetState,
      "finished_at" to nowIso(),
      "error" to null,
      "progress_stage" to null,
      "progress_current" to null,
      "progress_total" to null,
    ),
    "article_id=?",
    arrayOf(articleId),
  )
  currentState
}

private inline fun <T> YomitoriDatabase.transaction(block: SQLiteDatabase.() -> T): T {
  val db = writableDatabase
  db.beginTransaction()
  return try {
    val result = db.block()
    db.setTransactionSuccessful()
    result
  } finally {
    db.endTransaction()
  }
}

private fun Cursor.summaryTask(): SummaryTaskRecord = SummaryTaskRecord(
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
private fun Cursor.nullableText(name: String): String? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getString(index) }
private fun Cursor.nullableInt(name: String): Int? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getInt(index) }
private fun nowIso(): String = Instant.now().toString()
private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}
