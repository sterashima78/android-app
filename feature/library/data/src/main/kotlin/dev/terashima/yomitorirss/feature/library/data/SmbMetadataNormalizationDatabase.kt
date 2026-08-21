package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySeries
import dev.terashima.yomitorirss.feature.library.LibrarySource
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

class DefaultSmbMetadataNormalizationRepository(
  private val database: DatabaseConnection,
  private val smbRepository: SmbLibraryRepository,
) : SmbMetadataNormalizationRepository {
  override suspend fun batchSnapshot(): SmbMetadataNormalizationBatchSnapshot? = withContext(Dispatchers.IO) {
    ensureSmbMetadataNormalizationSchema(database.writable)
    promoteCoverReadyItems()
    queryLatestBatchSnapshot()
  }

  override suspend fun startBatch(books: List<LibraryBook>): Int = withContext(Dispatchers.IO) {
    ensureSmbMetadataNormalizationSchema(database.writable)
    val smbBooks = books
      .asSequence()
      .filter { it.source == LibrarySource.SMB }
      .distinctBy(LibraryBook::sourceId)
      .mapNotNull { book -> smbNormalizationInput(book)?.let { input -> book to input } }
      .toList()
    require(smbBooks.isNotEmpty()) { "ファイルサーバ由来の蔵書がありません" }

    val now = System.currentTimeMillis()
    database.transaction {
      val latest = queryLatestBatchSnapshot(this)
      if (latest != null && latest.items.any { it.status in UNRESOLVED_STATUSES }) {
        error("前回の書誌正規化候補を仕分けしてから新しい一括解析を開始してください")
      }

      val confirmed = queryConfirmedSourceIds(this)
      val targets = smbBooks.filterNot { (book, _) -> book.sourceId in confirmed }
      require(targets.isNotEmpty()) { "未確定のファイルサーバ書籍はありません" }

      targets.forEach { (book, _) ->
        clearStaleCoverReference(this, book.sourceId, book.thumbnailUrl)
      }

      val batchId = "smbmeta-${UUID.randomUUID()}"
      insertOrThrow(
        BATCH_TABLE,
        null,
        ContentValues().apply {
          put("batch_id", batchId)
          put("status", SmbMetadataNormalizationBatchStatus.RUNNING.name)
          put("created_at", now)
          put("updated_at", now)
        },
      )
      targets.forEach { (book, input) ->
        insertOrThrow(
          ITEM_TABLE,
          null,
          ContentValues().apply {
            put("batch_id", batchId)
            put("source_id", book.sourceId)
            put("original_file_name", input.fileName)
            put("input_size", input.size)
            put("input_modified_at", input.modifiedAt)
            put(
              "status",
              if (validCoverFile(book.thumbnailUrl) != null) {
                SmbMetadataNormalizationStatus.QUEUED.name
              } else {
                SmbMetadataNormalizationStatus.WAITING_FOR_COVER.name
              },
            )
            putNull("proposed_file_name")
            putNull("metadata_json")
            putNull("error")
            put("created_at", now)
            put("updated_at", now)
          },
        )
      }
      targets.size
    }
  }

  override suspend fun applyCandidate(
    sourceId: String,
    proposedFileName: String,
    proposal: SmbBookMetadataProposal,
  ): Unit = withContext(Dispatchers.IO) {
    ensureSmbMetadataNormalizationSchema(database.writable)
    val sanitizedProposal = sanitizeSmbBookMetadataProposal(proposal)
    val item = queryLatestItem(sourceId)
      ?: error("反映できる書誌正規化候補がありません")
    require(
      item.status == SmbMetadataNormalizationStatus.PENDING_REVIEW ||
        item.status == SmbMetadataNormalizationStatus.DEFERRED,
    ) { "反映できる書誌正規化候補がありません" }
    val normalizedFileName = validateProposedSmbFileName(item.originalFileName, proposedFileName)

    val snapshot = DefaultLibraryRepository(database).snapshot()
    val currentBook = (snapshot.books + snapshot.hiddenBooks).firstOrNull {
      it.source == LibrarySource.SMB && it.sourceId == sourceId
    } ?: error("対象のファイルサーバ書籍が見つかりません")
    val currentInput = smbNormalizationInput(currentBook)
      ?: error("対象書籍のファイル情報を読み取れません")
    val revisionMatches =
      currentInput.fileName == item.originalFileName &&
        currentInput.size == item.inputSize &&
        currentInput.modifiedAt == item.inputModifiedAt
    if (!revisionMatches) {
      val message = "候補生成後にファイルが変更されています。再解析してください"
      markCandidateRetriable(item, message)
      throw IllegalArgumentException(message)
    }

    val renamedBook = if (normalizedFileName == currentInput.fileName) {
      currentBook
    } else {
      smbRepository.renameBook(currentBook, normalizedFileName)
    }

    try {
      persistAppliedCandidate(
        item = item,
        oldSourceId = sourceId,
        renamedBook = renamedBook,
        proposedFileName = normalizedFileName,
        proposal = sanitizedProposal,
      )
    } catch (error: Throwable) {
      if (renamedBook.sourceId != currentBook.sourceId) {
        runCatching { smbRepository.renameBook(renamedBook, item.originalFileName) }
      }
      throw error
    }
  }

  override suspend fun deferCandidate(sourceId: String) {
    changeStatus(
      sourceId = sourceId,
      allowed = setOf(SmbMetadataNormalizationStatus.PENDING_REVIEW),
      target = SmbMetadataNormalizationStatus.DEFERRED,
    )
  }

  override suspend fun rejectCandidate(sourceId: String): Unit = withContext(Dispatchers.IO) {
    ensureSmbMetadataNormalizationSchema(database.writable)
    val item = queryLatestItem(sourceId) ?: error("却下できる候補がありません")
    require(
      item.status in setOf(
        SmbMetadataNormalizationStatus.PENDING_REVIEW,
        SmbMetadataNormalizationStatus.DEFERRED,
        SmbMetadataNormalizationStatus.FAILED,
        SmbMetadataNormalizationStatus.SKIPPED,
      ),
    ) { "却下できる候補がありません" }
    val now = System.currentTimeMillis()
    database.transaction {
      insertWithOnConflict(
        DECISION_TABLE,
        null,
        ContentValues().apply {
          put("source_id", sourceId)
          put("decision_status", SmbMetadataNormalizationStatus.REJECTED.name)
          putNull("title")
          putNull("authors_json")
          putNull("publisher")
          putNull("published_date")
          putNull("isbn10")
          putNull("isbn13")
          put("updated_at", now)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
      update(
        ITEM_TABLE,
        ContentValues().apply {
          put("status", SmbMetadataNormalizationStatus.REJECTED.name)
          putNull("error")
          put("updated_at", now)
        },
        "batch_id = ? AND source_id = ?",
        arrayOf(item.batchId, sourceId),
      )
      touchBatch(item.batchId, now)
      finishBatchIfIdle(this, item.batchId, now)
    }
  }

  override suspend fun reopenCandidate(sourceId: String) {
    changeStatus(
      sourceId = sourceId,
      allowed = setOf(SmbMetadataNormalizationStatus.DEFERRED),
      target = SmbMetadataNormalizationStatus.PENDING_REVIEW,
    )
  }

  override suspend fun retryCandidate(sourceId: String): Unit = withContext(Dispatchers.IO) {
    ensureSmbMetadataNormalizationSchema(database.writable)
    val item = queryLatestItem(sourceId) ?: error("再解析できる候補がありません")
    require(item.status in REANALYZABLE_STATUSES) { "再解析できる候補がありません" }

    val snapshot = DefaultLibraryRepository(database).snapshot()
    val currentBook = (snapshot.books + snapshot.hiddenBooks).firstOrNull {
      it.source == LibrarySource.SMB && it.sourceId == sourceId
    } ?: error("対象のファイルサーバ書籍が見つかりません")
    val currentInput = smbNormalizationInput(currentBook)
      ?: error("対象書籍のファイル情報を読み取れません")
    val coverReady = validCoverFile(currentBook.thumbnailUrl) != null
    val now = System.currentTimeMillis()
    database.transaction {
      if (!coverReady) {
        clearStaleCoverReference(this, sourceId, currentBook.thumbnailUrl)
      }
      if (item.status == SmbMetadataNormalizationStatus.REJECTED) {
        delete(DECISION_TABLE, "source_id = ?", arrayOf(sourceId))
      }
      update(
        ITEM_TABLE,
        ContentValues().apply {
          put("original_file_name", currentInput.fileName)
          put("input_size", currentInput.size)
          put("input_modified_at", currentInput.modifiedAt)
          put(
            "status",
            if (coverReady) {
              SmbMetadataNormalizationStatus.QUEUED.name
            } else {
              SmbMetadataNormalizationStatus.WAITING_FOR_COVER.name
            },
          )
          putNull("proposed_file_name")
          putNull("metadata_json")
          putNull("error")
          put("updated_at", now)
        },
        "batch_id = ? AND source_id = ?",
        arrayOf(item.batchId, sourceId),
      )
      update(
        BATCH_TABLE,
        ContentValues().apply {
          put("status", SmbMetadataNormalizationBatchStatus.RUNNING.name)
          put("updated_at", now)
        },
        "batch_id = ?",
        arrayOf(item.batchId),
      )
    }
  }

  internal fun requeueInterrupted() {
    ensureSmbMetadataNormalizationSchema(database.writable)
    database.writable.update(
      ITEM_TABLE,
      ContentValues().apply {
        put("status", SmbMetadataNormalizationStatus.QUEUED.name)
        put("updated_at", System.currentTimeMillis())
      },
      "status = ?",
      arrayOf(SmbMetadataNormalizationStatus.PROCESSING.name),
    )
  }

  internal fun promoteCoverReadyItems() {
    ensureSmbMetadataNormalizationSchema(database.writable)
    val waiting = database.readable.rawQuery(
      """
        SELECT i.batch_id, i.source_id, q.status, q.message
        FROM $ITEM_TABLE i
        JOIN $BATCH_TABLE b ON b.batch_id = i.batch_id
        LEFT JOIN smb_cover_prefetch_queue q ON q.source_id = i.source_id
        WHERE b.status = ? AND i.status = ?
      """.trimIndent(),
      arrayOf(
        SmbMetadataNormalizationBatchStatus.RUNNING.name,
        SmbMetadataNormalizationStatus.WAITING_FOR_COVER.name,
      ),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            WaitingCoverItem(
              batchId = cursor.getString(0),
              sourceId = cursor.getString(1),
              queueStatus = if (cursor.isNull(2)) null else cursor.getString(2),
              queueMessage = if (cursor.isNull(3)) null else cursor.getString(3),
            ),
          )
        }
      }
    }
    if (waiting.isEmpty()) return
    val now = System.currentTimeMillis()
    database.transaction {
      waiting.forEach { item ->
        val cover = queryBookCover(item.sourceId, this)
        val target = when {
          validCoverFile(cover) != null -> SmbMetadataNormalizationStatus.QUEUED
          item.queueStatus == "FAILED" -> SmbMetadataNormalizationStatus.FAILED
          item.queueStatus == "SKIPPED" -> SmbMetadataNormalizationStatus.SKIPPED
          else -> null
        } ?: return@forEach
        update(
          ITEM_TABLE,
          ContentValues().apply {
            put("status", target.name)
            if (target == SmbMetadataNormalizationStatus.FAILED || target == SmbMetadataNormalizationStatus.SKIPPED) {
              put("error", item.queueMessage ?: "表紙を取得できませんでした")
            } else {
              putNull("error")
            }
            put("updated_at", now)
          },
          "batch_id = ? AND source_id = ? AND status = ?",
          arrayOf(
            item.batchId,
            item.sourceId,
            SmbMetadataNormalizationStatus.WAITING_FOR_COVER.name,
          ),
        )
        touchBatch(item.batchId, now)
      }
    }
  }

  internal fun claimNext(): ClaimedSmbMetadataNormalizationItem? {
    ensureSmbMetadataNormalizationSchema(database.writable)
    promoteCoverReadyItems()
    val now = System.currentTimeMillis()
    return database.transaction {
      val next = rawQuery(
        """
          SELECT i.batch_id, i.source_id, i.original_file_name, i.input_size, i.input_modified_at
          FROM $ITEM_TABLE i
          JOIN $BATCH_TABLE b ON b.batch_id = i.batch_id
          WHERE b.status = ? AND i.status = ?
          ORDER BY i.created_at, i.source_id
          LIMIT 1
        """.trimIndent(),
        arrayOf(
          SmbMetadataNormalizationBatchStatus.RUNNING.name,
          SmbMetadataNormalizationStatus.QUEUED.name,
        ),
      ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        ClaimedSmbMetadataNormalizationItem(
          batchId = cursor.getString(0),
          sourceId = cursor.getString(1),
          originalFileName = cursor.getString(2),
          inputSize = cursor.getLong(3),
          inputModifiedAt = cursor.getLong(4),
        )
      } ?: return@transaction null
      val changed = update(
        ITEM_TABLE,
        ContentValues().apply {
          put("status", SmbMetadataNormalizationStatus.PROCESSING.name)
          put("updated_at", now)
        },
        "batch_id = ? AND source_id = ? AND status = ?",
        arrayOf(next.batchId, next.sourceId, SmbMetadataNormalizationStatus.QUEUED.name),
      )
      if (changed == 1) next else null
    }
  }

  internal fun saveGeneratedCandidate(
    item: ClaimedSmbMetadataNormalizationItem,
    proposedFileName: String,
    proposal: SmbBookMetadataProposal,
  ) {
    val sanitized = sanitizeSmbBookMetadataProposal(proposal)
    val fileName = validateProposedSmbFileName(item.originalFileName, proposedFileName)
    val now = System.currentTimeMillis()
    database.transaction {
      val changed = update(
        ITEM_TABLE,
        ContentValues().apply {
          put("status", SmbMetadataNormalizationStatus.PENDING_REVIEW.name)
          put("proposed_file_name", fileName)
          put("metadata_json", proposalToJson(sanitized))
          putNull("error")
          put("updated_at", now)
        },
        "batch_id = ? AND source_id = ? AND status = ?",
        arrayOf(item.batchId, item.sourceId, SmbMetadataNormalizationStatus.PROCESSING.name),
      )
      check(changed == 1) { "書誌正規化候補を保存できませんでした" }
      touchBatch(item.batchId, now)
      finishBatchIfIdle(this, item.batchId, now)
    }
  }

  internal fun fail(item: ClaimedSmbMetadataNormalizationItem, message: String) {
    finishClaimed(item, SmbMetadataNormalizationStatus.FAILED, message)
  }

  internal fun skip(item: ClaimedSmbMetadataNormalizationItem, message: String) {
    finishClaimed(item, SmbMetadataNormalizationStatus.SKIPPED, message)
  }

  internal fun requeue(item: ClaimedSmbMetadataNormalizationItem) {
    finishClaimed(item, SmbMetadataNormalizationStatus.QUEUED, null)
  }

  internal fun finishBatchIfIdle(batchId: String) {
    ensureSmbMetadataNormalizationSchema(database.writable)
    finishBatchIfIdle(database.writable, batchId, System.currentTimeMillis())
  }

  private fun persistAppliedCandidate(
    item: SmbMetadataNormalizationItem,
    oldSourceId: String,
    renamedBook: LibraryBook,
    proposedFileName: String,
    proposal: SmbBookMetadataProposal,
  ) {
    val now = System.currentTimeMillis()
    database.transaction {
      delete(DECISION_TABLE, "source_id = ?", arrayOf(oldSourceId))
      insertWithOnConflict(
        DECISION_TABLE,
        null,
        ContentValues().apply {
          put("source_id", renamedBook.sourceId)
          put("decision_status", SmbMetadataNormalizationStatus.APPLIED.name)
          put("title", proposal.title)
          put("authors_json", JSONArray(proposal.authors).toString())
          put("publisher", proposal.publisher)
          put("published_date", proposal.publishedDate)
          put("isbn10", proposal.isbn10)
          put("isbn13", proposal.isbn13)
          put("updated_at", now)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
      update(
        ITEM_TABLE,
        ContentValues().apply {
          put("source_id", renamedBook.sourceId)
          put("status", SmbMetadataNormalizationStatus.APPLIED.name)
          put("proposed_file_name", proposedFileName)
          put("metadata_json", proposalToJson(proposal))
          putNull("error")
          put("updated_at", now)
        },
        "batch_id = ? AND source_id = ?",
        arrayOf(item.batchId, oldSourceId),
      )
      proposal.seriesName?.trim()?.takeIf(String::isNotEmpty)?.let { seriesName ->
        insertWithOnConflict(
          "library_item_series",
          null,
          ContentValues().apply {
            put("source", LibrarySource.SMB.name)
            put("source_id", renamedBook.sourceId)
            put("series_name", seriesName)
            proposal.seriesPosition?.let { put("series_position", it) } ?: putNull("series_position")
            put("updated_at", now)
          },
          SQLiteDatabase.CONFLICT_REPLACE,
        )
        delete(
          "library_item_series_exclusions",
          "source = ? AND source_id = ?",
          arrayOf(LibrarySource.SMB.name, renamedBook.sourceId),
        )
      }
      touchBatch(item.batchId, now)
      finishBatchIfIdle(this, item.batchId, now)
    }
  }

  private fun changeStatus(
    sourceId: String,
    allowed: Set<SmbMetadataNormalizationStatus>,
    target: SmbMetadataNormalizationStatus,
  ) {
    ensureSmbMetadataNormalizationSchema(database.writable)
    val item = queryLatestItem(sourceId) ?: error("変更できる候補がありません")
    require(item.status in allowed) { "変更できる候補がありません" }
    val now = System.currentTimeMillis()
    database.transaction {
      val changed = update(
        ITEM_TABLE,
        ContentValues().apply {
          put("status", target.name)
          put("updated_at", now)
        },
        "batch_id = ? AND source_id = ? AND status = ?",
        arrayOf(item.batchId, sourceId, item.status.name),
      )
      require(changed == 1) { "候補の状態を変更できませんでした" }
      touchBatch(item.batchId, now)
    }
  }

  private fun markCandidateRetriable(
    item: SmbMetadataNormalizationItem,
    message: String,
  ) {
    val now = System.currentTimeMillis()
    database.transaction {
      val changed = update(
        ITEM_TABLE,
        ContentValues().apply {
          put("status", SmbMetadataNormalizationStatus.SKIPPED.name)
          putNull("proposed_file_name")
          putNull("metadata_json")
          put("error", message)
          put("updated_at", now)
        },
        "batch_id = ? AND source_id = ? AND status = ?",
        arrayOf(item.batchId, item.sourceId, item.status.name),
      )
      check(changed == 1) { "書誌正規化候補を再解析可能な状態へ変更できませんでした" }
      touchBatch(item.batchId, now)
      finishBatchIfIdle(this, item.batchId, now)
    }
  }

  private fun finishClaimed(
    item: ClaimedSmbMetadataNormalizationItem,
    target: SmbMetadataNormalizationStatus,
    message: String?,
  ) {
    val now = System.currentTimeMillis()
    database.transaction {
      update(
        ITEM_TABLE,
        ContentValues().apply {
          put("status", target.name)
          if (message == null) putNull("error") else put("error", message)
          put("updated_at", now)
        },
        "batch_id = ? AND source_id = ? AND status = ?",
        arrayOf(item.batchId, item.sourceId, SmbMetadataNormalizationStatus.PROCESSING.name),
      )
      touchBatch(item.batchId, now)
      finishBatchIfIdle(this, item.batchId, now)
    }
  }

  private fun queryLatestItem(sourceId: String): SmbMetadataNormalizationItem? =
    queryLatestBatchSnapshot()?.items?.firstOrNull { it.sourceId == sourceId }

  private fun queryLatestBatchSnapshot(): SmbMetadataNormalizationBatchSnapshot? =
    queryLatestBatchSnapshot(database.readable)

  private fun queryLatestBatchSnapshot(db: SQLiteDatabase): SmbMetadataNormalizationBatchSnapshot? {
    val header = db.rawQuery(
      "SELECT batch_id, status, created_at, updated_at FROM $BATCH_TABLE ORDER BY created_at DESC LIMIT 1",
      null,
    ).use { cursor ->
      if (!cursor.moveToFirst()) return@use null
      SmbMetadataNormalizationBatchHeader(
        batchId = cursor.getString(0),
        status = SmbMetadataNormalizationBatchStatus.valueOf(cursor.getString(1)),
        createdAt = cursor.getLong(2),
        updatedAt = cursor.getLong(3),
      )
    } ?: return null
    val covers = queryBookCovers(db)
    val items = db.rawQuery(
      """
        SELECT source_id, original_file_name, input_size, input_modified_at, status,
               proposed_file_name, metadata_json, error, updated_at
        FROM $ITEM_TABLE
        WHERE batch_id = ?
        ORDER BY
          CASE status
            WHEN 'PROCESSING' THEN 0
            WHEN 'QUEUED' THEN 1
            WHEN 'WAITING_FOR_COVER' THEN 2
            WHEN 'PENDING_REVIEW' THEN 3
            WHEN 'DEFERRED' THEN 4
            WHEN 'FAILED' THEN 5
            WHEN 'SKIPPED' THEN 6
            ELSE 7
          END,
          updated_at DESC,
          original_file_name COLLATE NOCASE
      """.trimIndent(),
      arrayOf(header.batchId),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          val metadata = if (cursor.isNull(6)) null else proposalFromJson(cursor.getString(6))
          add(
            SmbMetadataNormalizationItem(
              batchId = header.batchId,
              sourceId = cursor.getString(0),
              originalFileName = cursor.getString(1),
              inputSize = cursor.getLong(2),
              inputModifiedAt = cursor.getLong(3),
              status = SmbMetadataNormalizationStatus.valueOf(cursor.getString(4)),
              proposedFileName = if (cursor.isNull(5)) null else cursor.getString(5),
              proposal = metadata,
              coverUrl = covers[cursor.getString(0)],
              error = if (cursor.isNull(7)) null else cursor.getString(7),
              updatedAtEpochMillis = cursor.getLong(8),
            ),
          )
        }
      }
    }
    return SmbMetadataNormalizationBatchSnapshot(
      batchId = header.batchId,
      status = header.status,
      items = items,
      createdAtEpochMillis = header.createdAt,
      updatedAtEpochMillis = header.updatedAt,
    )
  }

  private fun queryConfirmedSourceIds(db: SQLiteDatabase): Set<String> = db.rawQuery(
    "SELECT source_id FROM $DECISION_TABLE WHERE decision_status IN (?, ?)",
    arrayOf(SmbMetadataNormalizationStatus.APPLIED.name, SmbMetadataNormalizationStatus.REJECTED.name),
  ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

  private fun queryBookCover(sourceId: String): String? = queryBookCover(sourceId, database.readable)

  private fun queryBookCover(sourceId: String, db: SQLiteDatabase): String? = db.rawQuery(
    "SELECT thumbnail_url FROM library_items WHERE source = ? AND source_id = ? LIMIT 1",
    arrayOf(LibrarySource.SMB.name, sourceId),
  ).use { cursor ->
    if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getString(0)
  }

  private fun queryBookCovers(db: SQLiteDatabase): Map<String, String?> = db.rawQuery(
    "SELECT source_id, thumbnail_url FROM library_items WHERE source = ?",
    arrayOf(LibrarySource.SMB.name),
  ).use { cursor ->
    buildMap {
      while (cursor.moveToNext()) put(cursor.getString(0), if (cursor.isNull(1)) null else cursor.getString(1))
    }
  }

  private fun SQLiteDatabase.touchBatch(batchId: String, now: Long) {
    update(
      BATCH_TABLE,
      ContentValues().apply { put("updated_at", now) },
      "batch_id = ?",
      arrayOf(batchId),
    )
  }
}

internal data class ClaimedSmbMetadataNormalizationItem(
  val batchId: String,
  val sourceId: String,
  val originalFileName: String,
  val inputSize: Long,
  val inputModifiedAt: Long,
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
      FROM $DECISION_TABLE
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
      CREATE TABLE IF NOT EXISTS $ITEM_TABLE(
        batch_id TEXT NOT NULL,
        source_id TEXT NOT NULL,
        original_file_name TEXT NOT NULL,
        input_size INTEGER NOT NULL,
        input_modified_at INTEGER NOT NULL,
        status TEXT NOT NULL,
        proposed_file_name TEXT,
        metadata_json TEXT,
        error TEXT,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        PRIMARY KEY(batch_id, source_id)
      )
    """.trimIndent(),
  )
  db.execSQL(
    "CREATE INDEX IF NOT EXISTS idx_smb_metadata_normalization_status ON $ITEM_TABLE(status, updated_at)",
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS $DECISION_TABLE(
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
    DECISION_TABLE,
    ContentValues().apply { put("source_id", renamedSourceId) },
    "source_id = ?",
    arrayOf(originalSourceId),
  )
  db.update(
    ITEM_TABLE,
    ContentValues().apply { put("source_id", renamedSourceId) },
    "source_id = ?",
    arrayOf(originalSourceId),
  )
}

internal fun deleteSmbMetadataNormalizationIdentity(db: SQLiteDatabase, sourceId: String) {
  ensureSmbMetadataNormalizationSchema(db)
  db.delete(DECISION_TABLE, "source_id = ?", arrayOf(sourceId))
  db.delete(ITEM_TABLE, "source_id = ?", arrayOf(sourceId))
}

private fun finishBatchIfIdle(db: SQLiteDatabase, batchId: String, now: Long) {
  val remaining = db.rawQuery(
    "SELECT COUNT(*) FROM $ITEM_TABLE WHERE batch_id = ? AND status IN (?, ?, ?)",
    arrayOf(
      batchId,
      SmbMetadataNormalizationStatus.WAITING_FOR_COVER.name,
      SmbMetadataNormalizationStatus.QUEUED.name,
      SmbMetadataNormalizationStatus.PROCESSING.name,
    ),
  ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
  if (remaining == 0) {
    db.update(
      BATCH_TABLE,
      ContentValues().apply {
        put("status", SmbMetadataNormalizationBatchStatus.COMPLETED.name)
        put("updated_at", now)
      },
      "batch_id = ?",
      arrayOf(batchId),
    )
  }
}

private fun clearStaleCoverReference(
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

private fun sanitizeSmbBookMetadataProposal(proposal: SmbBookMetadataProposal): SmbBookMetadataProposal {
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

private fun proposalToJson(proposal: SmbBookMetadataProposal): String = JSONObject().apply {
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

private fun proposalFromJson(json: String): SmbBookMetadataProposal? = runCatching {
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

private fun validCoverFile(url: String?): File? {
  val uri = url?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return null
  if (uri.scheme != "file") return null
  return uri.path?.let(::File)?.takeIf { it.isFile && it.length() > 0L }
}

private data class SmbMetadataNormalizationBatchHeader(
  val batchId: String,
  val status: SmbMetadataNormalizationBatchStatus,
  val createdAt: Long,
  val updatedAt: Long,
)

private data class WaitingCoverItem(
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

private val UNRESOLVED_STATUSES = setOf(
  SmbMetadataNormalizationStatus.WAITING_FOR_COVER,
  SmbMetadataNormalizationStatus.QUEUED,
  SmbMetadataNormalizationStatus.PROCESSING,
  SmbMetadataNormalizationStatus.PENDING_REVIEW,
  SmbMetadataNormalizationStatus.DEFERRED,
  SmbMetadataNormalizationStatus.FAILED,
  SmbMetadataNormalizationStatus.SKIPPED,
)

private val REANALYZABLE_STATUSES = setOf(
  SmbMetadataNormalizationStatus.PENDING_REVIEW,
  SmbMetadataNormalizationStatus.DEFERRED,
  SmbMetadataNormalizationStatus.REJECTED,
  SmbMetadataNormalizationStatus.FAILED,
  SmbMetadataNormalizationStatus.SKIPPED,
)

internal const val SMB_METADATA_NORMALIZATION_BATCH_TABLE = "smb_metadata_normalization_batches"
internal const val SMB_METADATA_NORMALIZATION_ITEM_TABLE = "smb_metadata_normalization_items"
internal const val SMB_METADATA_NORMALIZATION_DECISION_TABLE = "smb_metadata_normalization_decisions"
private const val BATCH_TABLE = SMB_METADATA_NORMALIZATION_BATCH_TABLE
private const val ITEM_TABLE = SMB_METADATA_NORMALIZATION_ITEM_TABLE
private const val DECISION_TABLE = SMB_METADATA_NORMALIZATION_DECISION_TABLE
