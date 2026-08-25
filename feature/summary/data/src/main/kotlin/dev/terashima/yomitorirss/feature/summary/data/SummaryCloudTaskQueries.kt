package dev.terashima.yomitorirss.feature.summary.data

import dev.terashima.yomitorirss.core.database.YomitoriDatabase

internal fun YomitoriDatabase.listCloudReadySummaryTasks(): List<SummaryTaskRecord> =
  readableDatabase.rawQuery(
    "SELECT * FROM summary_tasks WHERE state=? ORDER BY queued_at ASC",
    arrayOf(SUMMARY_QUEUED),
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.summaryTaskRecord()) } }
