package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.time.Instant

/**
 * 失敗済みの要約タスクのうち、現在もブックマークとして保存されている記事だけを
 * まとめて待機状態へ戻す。停止・キャンセル・実行中タスクには触れない。
 */
internal fun YomitoriDatabase.retryFailedBookmarkSummaryTasks(): Int =
  writableDatabase.update(
    "summary_tasks",
    ContentValues().apply {
      put("state", SUMMARY_QUEUED)
      put("queued_at", Instant.now().toString())
      putNull("started_at")
      putNull("finished_at")
      putNull("error")
      putNull("progress_stage")
      putNull("progress_current")
      putNull("progress_total")
    },
    "state=? AND article_id IN (SELECT id FROM articles WHERE saved_at IS NOT NULL)",
    arrayOf(SUMMARY_FAILED),
  )
