package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase

internal fun migrateSmbMetadataNormalizationDecisionIdentity(
  db: SQLiteDatabase,
  originalSourceId: String,
  renamedSourceId: String,
) {
  if (originalSourceId == renamedSourceId) return
  ensureSmbMetadataNormalizationSchema(db)
  db.update(
    SMB_METADATA_NORMALIZATION_DECISION_TABLE,
    ContentValues().apply { put("source_id", renamedSourceId) },
    "source_id = ?",
    arrayOf(originalSourceId),
  )
}
