package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.MAX_SMB_METADATA_REANALYSIS_CONTEXT_CHARS
import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationBatchSnapshot
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationBatchStatus
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationItem
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationRepository
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationStatus
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// Shared persistence schemas, normalization codecs, and immutable projections.
internal data class ClaimedSmbMetadataNormalizationItem(
  val batchId: String,
  val sourceId: String,
  val originalFileName: String,
  val inputSize: Long,
  val inputModifiedAt: Long,
  val previousProposal: SmbBookMetadataProposal? = null,
  val supplementalContext: String? = null,
)

internal data class SmbNormalizationInput(
  val fileName: String,
  val size: Long,
  val modifiedAt: Long,
)

internal fun smbNormalizationInput(book: LibraryBook): SmbNormalizationInput? {
  if (book.source != LibrarySource.SMB) return null
  val uri = book.infoUrl?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
  if (uri.scheme != "yomitori" || uri.host != "smb-book" || uri.path != "/open") return null
  val path = uri.getQueryParameter("path")?.takeIf(String::isNotBlank) ?: return null
  val fileName = path.substringAfterLast('\\').substringAfterLast('/').takeIf(String::isNotBlank) ?: return null
  val size = uri.getQueryParameter("size")?.toLongOrNull()?.takeIf { it >= 0L } ?: return null
  val modifiedAt = uri.getQueryParameter("modified")?.toLongOrNull() ?: return null
  return SmbNormalizationInput(fileName = fileName, size = size, modifiedAt = modifiedAt)
}

internal fun applyConfirmedSmbMetadata(
  database: DatabaseConnection,
  books: List<LibraryBook>,
): List<LibraryBook> {
  if (books.none { it.source == LibrarySource.SMB }) return books
  ensureSmbMetadataNormalizationSchema(database.writable)
  val overrides = database.readable.rawQuery(
    """
      SELECT source_id, title, authors_json, publisher, published_date, isbn10, isbn13
      FROM $SMB_NORMALIZATION_DECISION_TABLE
      WHERE decision_status = ?
    """.trimIndent(),
    arrayOf(SmbMetadataNormalizationStatus.APPLIED.name),
  ).use { cursor ->
    buildMap {
      while (cursor.moveToNext()) {
        val sourceId = cursor.getString(0)
        val title = if (cursor.isNull(1)) null else cursor.getString(1)
        if (title.isNullOrBlank()) continue
        val authors = if (cursor.isNull(2)) emptyList() else jsonStringList(cursor.getString(2))
        put(
          sourceId,
          SmbConfirmedMetadata(
            title = title,
            authors = authors,
            publisher = if (cursor.isNull(3)) null else cursor.getString(3),
            publishedDate = if (cursor.isNull(4)) null else cursor.getString(4),
            isbn10 = if (cursor.isNull(5)) null else cursor.getString(5),
            isbn13 = if (cursor.isNull(6)) null else cursor.getString(6),
          ),
        )
      }
    }
  }
  if (overrides.isEmpty()) return books
  return books.map { book ->
    if (book.source != LibrarySource.SMB) return@map book
    val metadata = overrides[book.sourceId] ?: return@map book
    book.copy(
      title = metadata.title,
      authors = metadata.authors,
      publisher = metadata.publisher,
      publishedDate = metadata.publishedDate,
      isbn10 = metadata.isbn10,
      isbn13 = metadata.isbn13,
    )
  }
}

internal fun ensureSmbMetadataNormalizationSchema(db: SQLiteDatabase) {
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $SMB_NORMALIZATION_BATCH_TABLE(
        batch_id TEXT PRIMARY KEY NOT NULL,
        status TEXT NOT NULL,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $SMB_NORMALIZATION_ITEM_TABLE(
        batch_id TEXT NOT NULL,
        source_id TEXT NOT NULL,
        original_file_name TEXT NOT NULL,
        input_size INTEGER NOT NULL,
        input_modified_at INTEGER NOT NULL,
        status TEXT NOT NULL,
        proposed_file_name TEXT,
        metadata_json TEXT,
        reanalysis_context TEXT,
        error TEXT,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        PRIMARY KEY(batch_id, source_id)
      )
    """.trimIndent(),
  )
  val itemColumns = db.rawQuery("PRAGMA table_info($SMB_NORMALIZATION_ITEM_TABLE)", null).use { cursor ->
    buildSet {
      val nameIndex = cursor.getColumnIndexOrThrow("name")
      while (cursor.moveToNext()) add(cursor.getString(nameIndex))
    }
  }
  if ("reanalysis_context" !in itemColumns) {
    db.execSQL("ALTER TABLE $SMB_NORMALIZATION_ITEM_TABLE ADD COLUMN reanalysis_context TEXT")
  }
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_smb_metadata_normalization_status ON $SMB_NORMALIZATION_ITEM_TABLE(status, updated_at)",
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $SMB_NORMALIZATION_DECISION_TABLE(
        source_id TEXT PRIMARY KEY NOT NULL,
        decision_status TEXT NOT NULL,
        title TEXT,
        authors_json TEXT,
        publisher TEXT,
        published_date TEXT,
        isbn10 TEXT,
        isbn13 TEXT,
        updated_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
}

internal fun migrateSmbMetadataNormalizationIdentity(
  db: SQLiteDatabase,
  originalSourceId: String,
  renamedSourceId: String,
) {
  if (originalSourceId == renamedSourceId) return
  ensureSmbMetadataNormalizationSchema(db)
  db.update(
    SMB_NORMALIZATION_DECISION_TABLE,
    ContentValues().apply { put("source_id", renamedSourceId) },
    "source_id = ?",
    arrayOf(originalSourceId),
  )
  db.update(
    SMB_NORMALIZATION_ITEM_TABLE,
    ContentValues().apply { put("source_id", renamedSourceId) },
    "source_id = ?",
    arrayOf(originalSourceId),
  )
}

internal fun deleteSmbMetadataNormalizationIdentity(db: SQLiteDatabase, sourceId: String) {
  ensureSmbMetadataNormalizationSchema(db)
  db.delete(SMB_NORMALIZATION_DECISION_TABLE, "source_id = ?", arrayOf(sourceId))
  db.delete(SMB_NORMALIZATION_ITEM_TABLE, "source_id = ?", arrayOf(sourceId))
}

internal fun finishBatchIfIdle(db: SQLiteDatabase, batchId: String, now: Long) {
  val remaining = db.rawQuery(
    "SELECT COUNT(*) FROM $SMB_NORMALIZATION_ITEM_TABLE WHERE batch_id = ? AND status IN (?, ?, ?)",
    arrayOf(
      batchId,
      SmbMetadataNormalizationStatus.WAITING_FOR_COVER.name,
      SmbMetadataNormalizationStatus.QUEUED.name,
      SmbMetadataNormalizationStatus.PROCESSING.name,
    ),
  ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
  if (remaining == 0) {
    db.update(
      SMB_NORMALIZATION_BATCH_TABLE,
      ContentValues().apply {
        put("status", SmbMetadataNormalizationBatchStatus.COMPLETED.name)
        put("updated_at", now)
      },
      "batch_id = ?",
      arrayOf(batchId),
    )
  }
}

internal fun clearStaleCoverReference(
  db: SQLiteDatabase,
  sourceId: String,
  coverUrl: String?,
) {
  if (coverUrl.isNullOrBlank() || validCoverFile(coverUrl) != null) return
  db.update(
    "library_items",
    ContentValues().apply { putNull("thumbnail_url") },
    "source = ? AND source_id = ? AND thumbnail_url = ?",
    arrayOf(LibrarySource.SMB.name, sourceId, coverUrl),
  )
}

internal fun sanitizeSmbBookMetadataProposal(proposal: SmbBookMetadataProposal): SmbBookMetadataProposal {
  val title = proposal.title.trim()
  require(title.isNotEmpty()) { "タイトル候補がありません" }
  require(title.length <= 240) { "タイトル候補が長すぎます" }
  val authors = proposal.authors
    .map(String::trim)
    .filter(String::isNotEmpty)
    .distinctBy(String::lowercase)
  require(authors.size <= 12) { "著者候補が多すぎます" }
  require(authors.all { it.length <= 120 }) { "著者名候補が長すぎます" }
  val publisher = proposal.publisher.cleaned(160)
  val publishedDate = proposal.publishedDate.cleaned(40)
  val isbn10 = proposal.isbn10.cleaned(20)
  val isbn13 = proposal.isbn13.cleaned(20)
  val seriesName = proposal.seriesName.cleaned(240)
  val seriesPosition = proposal.seriesPosition
  require(seriesPosition == null || seriesPosition > 0) { "巻数は1以上である必要があります" }
  val confidence = proposal.confidence
  require(confidence == null || confidence in 0f..1f) { "確信度は0〜1である必要があります" }
  val reason = proposal.reason.cleaned(500)
  return proposal.copy(
    title = title,
    authors = authors,
    publisher = publisher,
    publishedDate = publishedDate,
    isbn10 = isbn10,
    isbn13 = isbn13,
    seriesName = seriesName,
    seriesPosition = seriesPosition,
    confidence = confidence,
    reason = reason,
  )
}

internal fun sanitizeReanalysisContext(value: String?): String? =
  value?.trim()?.takeIf(String::isNotEmpty)?.also {
    require(it.length <= MAX_SMB_METADATA_REANALYSIS_CONTEXT_CHARS) { "再解析の補足情報が長すぎます" }
  }

private fun String?.cleaned(maxLength: Int): String? = this?.trim()?.takeIf(String::isNotEmpty)?.also {
  require(it.length <= maxLength) { "書誌情報候補が長すぎます" }
}

internal fun validateProposedSmbFileName(originalFileName: String, proposedFileName: String): String {
  val value = proposedFileName.trim()
  require(value.isNotEmpty()) { "変更後のファイル名を入力してください" }
  require(value.length <= 240) { "変更後のファイル名が長すぎます" }
  require(value.none { it == '/' || it == '\\' || it == '\u0000' }) { "ファイル名にパス区切り文字は使用できません" }
  require(value != "." && value != "..") { "このファイル名は使用できません" }
  val originalExtension = originalFileName.substringAfterLast('.', "").lowercase()
  val proposedExtension = value.substringAfterLast('.', "").lowercase()
  require(originalExtension.isNotEmpty() && proposedExtension == originalExtension) {
    "ファイルの拡張子は変更できません"
  }
  return value
}

internal fun proposalToJson(proposal: SmbBookMetadataProposal): String = JSONObject().apply {
  put("title", proposal.title)
  put("authors", JSONArray(proposal.authors))
  putNullable("publisher", proposal.publisher)
  putNullable("publishedDate", proposal.publishedDate)
  putNullable("isbn10", proposal.isbn10)
  putNullable("isbn13", proposal.isbn13)
  putNullable("seriesName", proposal.seriesName)
  putNullable("seriesPosition", proposal.seriesPosition)
  putNullable("confidence", proposal.confidence)
  putNullable("reason", proposal.reason)
}.toString()

internal fun proposalFromJson(json: String): SmbBookMetadataProposal? = runCatching {
  val value = JSONObject(json)
  sanitizeSmbBookMetadataProposal(
    SmbBookMetadataProposal(
      title = value.getString("title"),
      authors = value.optJSONArray("authors")?.let(::jsonStringList).orEmpty(),
      publisher = value.optNullableString("publisher"),
      publishedDate = value.optNullableString("publishedDate"),
      isbn10 = value.optNullableString("isbn10"),
      isbn13 = value.optNullableString("isbn13"),
      seriesName = value.optNullableString("seriesName"),
      seriesPosition = if (value.isNull("seriesPosition")) null else value.optInt("seriesPosition"),
      confidence = if (value.isNull("confidence")) null else value.optDouble("confidence").toFloat(),
      reason = value.optNullableString("reason"),
    ),
  )
}.getOrNull()

private fun JSONObject.putNullable(name: String, value: Any?) {
  put(name, value ?: JSONObject.NULL)
}

private fun JSONObject.optNullableString(name: String): String? =
  if (!has(name) || isNull(name)) null else optString(name).trim().takeIf(String::isNotEmpty)

private fun jsonStringList(json: String): List<String> = runCatching { jsonStringList(JSONArray(json)) }.getOrDefault(emptyList())

private fun jsonStringList(array: JSONArray): List<String> = buildList {
  for (index in 0 until array.length()) {
    array.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
  }
}

internal fun validCoverFile(url: String?): File? {
  val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
  if (uri.scheme != "file") return null
  return uri.path?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
}

internal data class SmbMetadataNormalizationBatchHeader(
  val batchId: String,
  val status: SmbMetadataNormalizationBatchStatus,
  val createdAt: Long,
  val updatedAt: Long,
)

internal data class WaitingCoverItem(
  val batchId: String,
  val sourceId: String,
  val queueStatus: String?,
  val queueMessage: String?,
)

private data class SmbConfirmedMetadata(
  val title: String,
  val authors: List<String>,
  val publisher: String?,
  val publishedDate: String?,
  val isbn10: String?,
  val isbn13: String?,
)

internal val UNRESOLVED_STATUSES = setOf(
  SmbMetadataNormalizationStatus.WAITING_FOR_COVER,
  SmbMetadataNormalizationStatus.QUEUED,
  SmbMetadataNormalizationStatus.PROCESSING,
  SmbMetadataNormalizationStatus.PENDING_REVIEW,
  SmbMetadataNormalizationStatus.DEFERRED,
  SmbMetadataNormalizationStatus.FAILED,
  SmbMetadataNormalizationStatus.SKIPPED,
)

internal val REANALYZABLE_STATUSES = setOf(
  SmbMetadataNormalizationStatus.PENDING_REVIEW,
  SmbMetadataNormalizationStatus.DEFERRED,
  SmbMetadataNormalizationStatus.REJECTED,
  SmbMetadataNormalizationStatus.FAILED,
  SmbMetadataNormalizationStatus.SKIPPED,
)

internal const val SMB_METADATA_NORMALIZATION_BATCH_TABLE = "smb_metadata_normalization_batches"
internal const val SMB_METADATA_NORMALIZATION_ITEM_TABLE = "smb_metadata_normalization_items"
internal const val SMB_METADATA_NORMALIZATION_DECISION_TABLE = "smb_metadata_normalization_decisions"
internal const val SMB_NORMALIZATION_BATCH_TABLE = SMB_METADATA_NORMALIZATION_BATCH_TABLE
internal const val SMB_NORMALIZATION_ITEM_TABLE = SMB_METADATA_NORMALIZATION_ITEM_TABLE
internal const val SMB_NORMALIZATION_DECISION_TABLE = SMB_METADATA_NORMALIZATION_DECISION_TABLE
