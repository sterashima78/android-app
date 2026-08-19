package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.time.Instant

internal fun YomitoriDatabase.findSummary(id: String): SummaryRecord? = readableDatabase.rawQuery(
  "SELECT article_id,summary,model_id,created_at FROM article_summaries WHERE article_id=?",
  arrayOf(id),
).use { cursor ->
  if (!cursor.moveToFirst()) null else SummaryRecord(
    articleId = cursor.getString(0),
    summary = cursor.getString(1),
    modelId = cursor.getString(2),
    createdAt = cursor.getString(3),
  )
}

internal fun YomitoriDatabase.saveSummary(id: String, text: String, model: String) {
  writableDatabase.insertWithOnConflict(
    "article_summaries",
    null,
    ContentValues().apply {
      put("article_id", id)
      put("summary", text)
      put("model_id", model)
      put("created_at", Instant.now().toString())
    },
    SQLiteDatabase.CONFLICT_REPLACE,
  )
}
