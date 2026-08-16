package dev.terashima.yomitorirss.feature.summary.data

import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.summary.SummaryQueueTaskCounts

internal fun YomitoriDatabase.countSummaryQueueTasks(): SummaryQueueTaskCounts {
  var queued = 0
  var running = 0
  var stopped = 0
  readableDatabase.rawQuery(
    """
      SELECT state, COUNT(*)
      FROM summary_tasks
      WHERE state IN (?, ?, ?)
      GROUP BY state
    """.trimIndent(),
    arrayOf(SUMMARY_QUEUED, SUMMARY_RUNNING, SUMMARY_STOPPED),
  ).use { cursor ->
    while (cursor.moveToNext()) {
      when (cursor.getString(0)) {
        SUMMARY_QUEUED -> queued = cursor.getInt(1)
        SUMMARY_RUNNING -> running = cursor.getInt(1)
        SUMMARY_STOPPED -> stopped = cursor.getInt(1)
      }
    }
  }
  return SummaryQueueTaskCounts(
    queued = queued,
    running = running,
    stopped = stopped,
  )
}
