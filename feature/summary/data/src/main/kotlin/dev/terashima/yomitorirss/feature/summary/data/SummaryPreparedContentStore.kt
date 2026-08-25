package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.time.Instant

internal fun YomitoriDatabase.findPreparedSummaryArticleContent(articleId: String): PreparedSummaryArticleContent? =
  readableDatabase.rawQuery(
    "SELECT article_id,content,fetched_at FROM summary_article_content WHERE article_id=? LIMIT 1",
    arrayOf(articleId),
  ).use { cursor ->
    if (!cursor.moveToFirst()) null else PreparedSummaryArticleContent(
      articleId = cursor.getString(0),
      content = cursor.getString(1),
      fetchedAt = cursor.getString(2),
    )
  }

internal fun YomitoriDatabase.listSummaryContentFetchCandidates(): List<SummaryTaskRecord> =
  readableDatabase.rawQuery(
    """
      SELECT q.*
      FROM summary_tasks q
      WHERE q.state=?
        AND (q.force_refresh<>0 OR NOT EXISTS(
          SELECT 1 FROM article_summaries s WHERE s.article_id=q.article_id
        ))
        AND NOT EXISTS(
          SELECT 1 FROM summary_article_content c WHERE c.article_id=q.article_id
        )
      ORDER BY q.queued_at ASC
    """.trimIndent(),
    arrayOf(SUMMARY_QUEUED),
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.summaryTaskRecord()) } }

internal fun YomitoriDatabase.countPreparedSummaryArticleContentsForActiveTasks(): Int =
  readableDatabase.rawQuery(
    """
      SELECT COUNT(*)
      FROM summary_article_content c
      JOIN summary_tasks q ON q.article_id=c.article_id
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
    ContentValues().apply {
      put("article_id", articleId)
      put("content", content)
      put("fetched_at", Instant.now().toString())
    },
    SQLiteDatabase.CONFLICT_REPLACE,
  )
  update(
    "summary_tasks",
    ContentValues().apply {
      putNull("progress_stage")
      putNull("progress_current")
      putNull("progress_total")
    },
    "article_id=? AND state=?",
    arrayOf(articleId, SUMMARY_QUEUED),
  )
  true
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
