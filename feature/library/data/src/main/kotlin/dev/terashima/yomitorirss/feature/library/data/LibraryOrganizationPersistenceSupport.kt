package dev.terashima.yomitorirss.feature.library.data

import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidateStatus
import java.util.Locale
import org.json.JSONArray

internal fun ensureLibraryOrganizationSchema(db: SQLiteDatabase) {
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $TAG_TABLE(
        tag_id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        normalized_name TEXT NOT NULL UNIQUE,
        created_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $COLLECTION_TABLE(
        collection_id TEXT PRIMARY KEY NOT NULL,
        name TEXT NOT NULL,
        normalized_name TEXT NOT NULL UNIQUE,
        created_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $ITEM_TAG_TABLE(
        source TEXT NOT NULL,
        source_id TEXT NOT NULL,
        tag_id TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        PRIMARY KEY(source, source_id, tag_id),
        FOREIGN KEY(tag_id) REFERENCES $TAG_TABLE(tag_id) ON DELETE CASCADE
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $ITEM_COLLECTION_TABLE(
        source TEXT NOT NULL,
        source_id TEXT NOT NULL,
        collection_id TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        PRIMARY KEY(source, source_id, collection_id),
        FOREIGN KEY(collection_id) REFERENCES $COLLECTION_TABLE(collection_id) ON DELETE CASCADE
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $READING_STATUS_TABLE(
        source TEXT NOT NULL,
        source_id TEXT NOT NULL,
        status TEXT NOT NULL,
        updated_at INTEGER NOT NULL,
        PRIMARY KEY(source, source_id)
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $BATCH_TABLE(
        batch_id TEXT PRIMARY KEY NOT NULL,
        status TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $BATCH_ITEM_TABLE(
        batch_id TEXT NOT NULL,
        source TEXT NOT NULL,
        source_id TEXT NOT NULL,
        status TEXT NOT NULL,
        tag_names_json TEXT NOT NULL,
        collection_names_json TEXT NOT NULL,
        reason TEXT,
        error TEXT,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        PRIMARY KEY(batch_id, source, source_id),
        FOREIGN KEY(batch_id) REFERENCES $BATCH_TABLE(batch_id) ON DELETE CASCADE
      )
    """.trimIndent(),
  )
  db.execSQL("CREATE INDEX IF NOT EXISTS library_item_org_tags_tag ON $ITEM_TAG_TABLE(tag_id)")
  db.execSQL("CREATE INDEX IF NOT EXISTS library_item_org_collections_collection ON $ITEM_COLLECTION_TABLE(collection_id)")
  db.execSQL("CREATE INDEX IF NOT EXISTS library_item_reading_status_status ON $READING_STATUS_TABLE(status)")
  db.execSQL("CREATE INDEX IF NOT EXISTS library_org_batch_status ON $BATCH_TABLE(status, created_at)")
  db.execSQL("CREATE INDEX IF NOT EXISTS library_org_batch_item_status ON $BATCH_ITEM_TABLE(batch_id, status, created_at)")
}

internal fun normalizeLibraryOrganizationName(value: String): String =
  cleanLibraryOrganizationName(value).lowercase(Locale.ROOT)

internal fun cleanLibraryOrganizationName(value: String): String =
  value.trim().replace(Regex("\\s+"), " ")

internal fun sanitizeNames(
  values: List<String>,
  maxCount: Int,
  label: String,
): List<String> {
  val sanitized = values
    .map(::cleanLibraryOrganizationName)
    .filter(String::isNotEmpty)
    .distinctBy(::normalizeLibraryOrganizationName)
  require(sanitized.size <= maxCount) { "$label は最大 $maxCount 件まで設定できます" }
  require(sanitized.all { it.length <= MAX_NAME_LENGTH }) { "$label 名は $MAX_NAME_LENGTH 文字以内で入力してください" }
  return sanitized
}

internal fun namesToJson(values: List<String>): String = JSONArray(values).toString()

internal fun jsonToNames(raw: String): List<String> = runCatching {
  val json = JSONArray(raw)
  buildList {
    for (index in 0 until json.length()) {
      json.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
    }
  }
}.getOrDefault(emptyList())

internal fun decodeCandidateStatus(raw: String): LibraryOrganizationCandidateStatus = when (raw) {
  RETIRED_PENDING_REVIEW_STATUS,
  RETIRED_DEFERRED_STATUS,
  -> LibraryOrganizationCandidateStatus.REJECTED
  else -> LibraryOrganizationCandidateStatus.valueOf(raw)
}

internal val UNFINISHED_CANDIDATE_STATUSES = setOf(
  LibraryOrganizationCandidateStatus.QUEUED,
  LibraryOrganizationCandidateStatus.PROCESSING,
  LibraryOrganizationCandidateStatus.FAILED,
  LibraryOrganizationCandidateStatus.SKIPPED,
)

internal const val RETIRED_PENDING_REVIEW_STATUS = "PENDING_REVIEW"
internal const val RETIRED_DEFERRED_STATUS = "DEFERRED"
internal const val TAG_TABLE = "library_organization_tags"
internal const val COLLECTION_TABLE = "library_organization_collections"
internal const val ITEM_TAG_TABLE = "library_item_organization_tags"
internal const val ITEM_COLLECTION_TABLE = "library_item_organization_collections"
internal const val READING_STATUS_TABLE = "library_item_reading_status"
internal const val BATCH_TABLE = "library_organization_batches"
internal const val BATCH_ITEM_TABLE = "library_organization_batch_items"
internal const val MAX_TAGS_PER_BOOK = 20
internal const val MAX_COLLECTIONS_PER_BOOK = 10
internal const val MAX_NAME_LENGTH = 80
internal const val MAX_ERROR_LENGTH = 400
internal const val MAX_TAXONOMY_CONTEXT = 100
