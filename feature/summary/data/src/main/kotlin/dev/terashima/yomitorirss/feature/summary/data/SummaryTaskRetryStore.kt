package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.time.Instant

internal fun YomitoriDatabase.requeueRunningSummaryTaskForRetry(
  articleId: String,
  safeMessage: String,
): Boolean = writableDatabase.update(
  "summary_tasks",
  ContentValues().apply {
    put("state", SUMMARY_QUEUED)
    put("queued_at", Instant.now().toString())
    putNull("started_at")
    putNull("finished_at")
    put("error", safeMessage.take(500))
    putNull("progress_stage")
    putNull("progress_current")
    putNull("progress_total")
  },
  "article_id=? AND state=?",
  arrayOf(articleId, SUMMARY_RUNNING),
) == 1
