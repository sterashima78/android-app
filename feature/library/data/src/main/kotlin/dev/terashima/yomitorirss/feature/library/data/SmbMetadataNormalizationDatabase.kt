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

class DefaultSmbMetadataNormalizationRepository(
  private val database: DatabaseConnection,
  private val smbRepository: SmbLibraryRepository,
) : SmbMetadataNormalizationRepository {
  private val queries = SmbMetadataNormalizationQueries(database)
  override suspend fun batchSnapshot(): SmbMetadataNormalizationBatchSnapshot? = withContext(Dispatchers.IO) {
    promoteCoverReadyItems()
    queries.queryLatestBatchSnapshot()
  }

  override suspend fun startBatch(books: List<LibraryBook>): Int = withContext(Dispatchers.IO) {
    val smbBooks = books
      .asSequence()
      .filter { it.source == LibrarySource.SMB }
      .distinctBy(LibraryBook::sourceId)
      .mapNotNull { book -> smbNormalizationInput(book)?.let { input -> book to input } }
      .toList()
    require(smbBooks.isNotEmpty()) { "ファイルサーバ由来の蔵書がありません" }

    val now = System.currentTimeMillis()
    database.transaction {
      val latest = queries.queryLatestBatchSnapshot(this)
      if (latest != null && latest.items.any { it.status in UNRESOLVED_STATUSES }) {
        error("前回の書誌正規化候補を仕分けしてから新しい一括解析を開始してください")
      }

      val confirmed = queries.queryConfirmedSourceIds(this)
      val targets = smbBooks.filterNot { (book, _) -> book.sourceId in confirmed }
      require(targets.isNotEmpty()) { "未確定のファイルサーバ書籍はありません" }

      targets.forEach { (book, _) ->
        clearStaleCoverReference(this, book.sourceId, book.thumbnailUrl)
      }

      val batchId = "smbmeta-${UUID.randomUUID()}"
      insertOrThrow(
        SMB_NORMALIZATION_BATCH_TABLE,
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
          SMB_NORMALIZATION_ITEM_TABLE,
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
            putNull("reanalysis_context")
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
    val sanitizedProposal = sanitizeSmbBookMetadataProposal(proposal)
    val item = queries.queryLatestItem(sourceId)
      ?: error("反映できる書誌正規化候補がありません")
    require(
      item.status == SmbMetadataNormalizationStatus.PENDING_REVIEW ||
        item.status == SmbMetadataNormalizationStatus.DEFERRED ||
        item.status == SmbMetadataNormalizationStatus.APPLIED,
    ) { "反映できる書誌正規化候補がありません" }

    if (item.status == SmbMetadataNormalizationStatus.APPLIED) {
      val currentBook = findLibraryBook(database, LibrarySource.SMB, sourceId)
        ?: error("対象のファイルサーバ書籍が見つかりません")
      val currentFileName = smbNormalizationInput(currentBook)?.fileName
        ?: error("対象書籍のファイル情報を読み取れません")
      require(proposedFileName.trim() == currentFileName) {
        "反映済み書誌情報の編集ではファイル名を変更できません"
      }
      persistEditedAppliedMetadata(item, sanitizedProposal)
      return@withContext
    }

    val normalizedFileName = validateProposedSmbFileName(item.originalFileName, proposedFileName)

    val currentBook = findLibraryBook(database, LibrarySource.SMB, sourceId)
      ?: error("対象のファイルサーバ書籍が見つかりません")
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
    val item = queries.queryLatestItem(sourceId) ?: error("却下できる候補がありません")
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
        SMB_NORMALIZATION_DECISION_TABLE,
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
        SMB_NORMALIZATION_ITEM_TABLE,
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

  override suspend fun retryCandidate(
    sourceId: String,
    supplementalContext: String?,
  ): Unit = withContext(Dispatchers.IO) {
    val item = queries.queryLatestItem(sourceId) ?: error("再解析できる候補がありません")
    require(item.status in REANALYZABLE_STATUSES) { "再解析できる候補がありません" }
    val context = sanitizeReanalysisContext(supplementalContext)

    val currentBook = findLibraryBook(database, LibrarySource.SMB, sourceId)
      ?: error("対象のファイルサーバ書籍が見つかりません")
    val currentInput = smbNormalizationInput(currentBook)
      ?: error("対象書籍のファイル情報を読み取れません")
    val coverReady = validCoverFile(currentBook.thumbnailUrl) != null
    val latestBatchId = queries.queryLatestBatchSnapshot()?.batchId ?: item.batchId
    val now = System.currentTimeMillis()
    database.transaction {
      if (!coverReady) {
        clearStaleCoverReference(this, sourceId, currentBook.thumbnailUrl)
      }
      if (item.status == SmbMetadataNormalizationStatus.REJECTED) {
        delete(SMB_NORMALIZATION_DECISION_TABLE, "source_id = ?", arrayOf(sourceId))
        insertWithOnConflict(
          SMB_NORMALIZATION_ITEM_TABLE,
          null,
          ContentValues().apply {
            put("batch_id", latestBatchId)
            put("source_id", sourceId)
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
            item.proposal?.let { put("metadata_json", proposalToJson(it)) } ?: putNull("metadata_json")
            context?.let { put("reanalysis_context", it) } ?: putNull("reanalysis_context")
            putNull("error")
            put("created_at", now)
            put("updated_at", now)
          },
          SQLiteDatabase.CONFLICT_REPLACE,
        )
      } else {
        val changed = update(
          SMB_NORMALIZATION_ITEM_TABLE,
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
            context?.let { put("reanalysis_context", it) } ?: putNull("reanalysis_context")
            putNull("error")
            put("updated_at", now)
          },
          "batch_id = ? AND source_id = ? AND status = ?",
          arrayOf(item.batchId, sourceId, item.status.name),
        )
        require(changed == 1) { "再解析対象をキューへ戻せませんでした" }
      }
      val targetBatchId = if (item.status == SmbMetadataNormalizationStatus.REJECTED) latestBatchId else item.batchId
      update(
        SMB_NORMALIZATION_BATCH_TABLE,
        ContentValues().apply {
          put("status", SmbMetadataNormalizationBatchStatus.RUNNING.name)
          put("updated_at", now)
        },
        "batch_id = ?",
        arrayOf(targetBatchId),
      )
    }
  }

  internal fun requeueInterrupted() {
    database.write {
      update(
        SMB_NORMALIZATION_ITEM_TABLE,
        ContentValues().apply {
          put("status", SmbMetadataNormalizationStatus.QUEUED.name)
          put("updated_at", System.currentTimeMillis())
        },
        "status = ?",
        arrayOf(SmbMetadataNormalizationStatus.PROCESSING.name),
      )
    }
  }

  internal fun promoteCoverReadyItems() {
    val waiting = database.readable.rawQuery(
      """
        SELECT i.batch_id, i.source_id, q.status, q.message
        FROM $SMB_NORMALIZATION_ITEM_TABLE i
        JOIN $SMB_NORMALIZATION_BATCH_TABLE b ON b.batch_id = i.batch_id
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
        val cover = queries.queryBookCover(item.sourceId, this)
        val target = when {
          validCoverFile(cover) != null -> SmbMetadataNormalizationStatus.QUEUED
          item.queueStatus == "FAILED" -> SmbMetadataNormalizationStatus.FAILED
          item.queueStatus == "SKIPPED" -> SmbMetadataNormalizationStatus.SKIPPED
          else -> null
        } ?: return@forEach
        update(
          SMB_NORMALIZATION_ITEM_TABLE,
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
    promoteCoverReadyItems()
    val now = System.currentTimeMillis()
    return database.transaction {
      val next = rawQuery(
        """
          SELECT i.batch_id, i.source_id, i.original_file_name, i.input_size, i.input_modified_at,
                 i.metadata_json, i.reanalysis_context
          FROM $SMB_NORMALIZATION_ITEM_TABLE i
          JOIN $SMB_NORMALIZATION_BATCH_TABLE b ON b.batch_id = i.batch_id
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
          previousProposal = if (cursor.isNull(5)) null else proposalFromJson(cursor.getString(5)),
          supplementalContext = if (cursor.isNull(6)) null else cursor.getString(6),
        )
      } ?: return@transaction null
      val changed = update(
        SMB_NORMALIZATION_ITEM_TABLE,
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
        SMB_NORMALIZATION_ITEM_TABLE,
        ContentValues().apply {
          put("status", SmbMetadataNormalizationStatus.PENDING_REVIEW.name)
          put("proposed_file_name", fileName)
          put("metadata_json", proposalToJson(sanitized))
          putNull("reanalysis_context")
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
      delete(SMB_NORMALIZATION_DECISION_TABLE, "source_id = ?", arrayOf(oldSourceId))
      insertWithOnConflict(
        SMB_NORMALIZATION_DECISION_TABLE,
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
        SMB_NORMALIZATION_ITEM_TABLE,
        ContentValues().apply {
          put("source_id", renamedBook.sourceId)
          put("status", SmbMetadataNormalizationStatus.APPLIED.name)
          put("proposed_file_name", proposedFileName)
          put("metadata_json", proposalToJson(proposal))
          putNull("reanalysis_context")
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

  private fun persistEditedAppliedMetadata(
    item: SmbMetadataNormalizationItem,
    proposal: SmbBookMetadataProposal,
  ) {
    val previousProposal = item.proposal ?: error("反映済み書誌情報を読み取れません")
    val previousSeriesName = previousProposal.seriesName?.trim()?.takeIf(String::isNotEmpty)
    val nextSeriesName = proposal.seriesName?.trim()?.takeIf(String::isNotEmpty)
    val seriesChanged =
      previousSeriesName != nextSeriesName || previousProposal.seriesPosition != proposal.seriesPosition
    val now = System.currentTimeMillis()
    database.transaction {
      val decisionChanged = update(
        SMB_NORMALIZATION_DECISION_TABLE,
        ContentValues().apply {
          put("title", proposal.title)
          put("authors_json", JSONArray(proposal.authors).toString())
          put("publisher", proposal.publisher)
          put("published_date", proposal.publishedDate)
          put("isbn10", proposal.isbn10)
          put("isbn13", proposal.isbn13)
          put("updated_at", now)
        },
        "source_id = ? AND decision_status = ?",
        arrayOf(item.sourceId, SmbMetadataNormalizationStatus.APPLIED.name),
      )
      require(decisionChanged == 1) { "反映済み書誌情報が見つかりません" }

      val itemChanged = update(
        SMB_NORMALIZATION_ITEM_TABLE,
        ContentValues().apply {
          put("metadata_json", proposalToJson(proposal))
          putNull("reanalysis_context")
          putNull("error")
          put("updated_at", now)
        },
        "batch_id = ? AND source_id = ? AND status = ?",
        arrayOf(item.batchId, item.sourceId, SmbMetadataNormalizationStatus.APPLIED.name),
      )
      require(itemChanged == 1) { "反映済み書誌情報を更新できませんでした" }

      if (seriesChanged) {
        if (nextSeriesName != null) {
          insertWithOnConflict(
            "library_item_series",
            null,
            ContentValues().apply {
              put("source", LibrarySource.SMB.name)
              put("source_id", item.sourceId)
              put("series_name", nextSeriesName)
              proposal.seriesPosition?.let { put("series_position", it) } ?: putNull("series_position")
              put("updated_at", now)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
          )
          delete(
            "library_item_series_exclusions",
            "source = ? AND source_id = ?",
            arrayOf(LibrarySource.SMB.name, item.sourceId),
          )
        } else {
          delete(
            "library_item_series",
            "source = ? AND source_id = ?",
            arrayOf(LibrarySource.SMB.name, item.sourceId),
          )
          insertWithOnConflict(
            "library_item_series_exclusions",
            null,
            ContentValues().apply {
              put("source", LibrarySource.SMB.name)
              put("source_id", item.sourceId)
              put("updated_at", now)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
          )
        }
      }
      touchBatch(item.batchId, now)
    }
  }

  private fun changeStatus(
    sourceId: String,
    allowed: Set<SmbMetadataNormalizationStatus>,
    target: SmbMetadataNormalizationStatus,
  ) {
    val item = queries.queryLatestItem(sourceId) ?: error("変更できる候補がありません")
    require(item.status in allowed) { "変更できる候補がありません" }
    val now = System.currentTimeMillis()
    database.transaction {
      val changed = update(
        SMB_NORMALIZATION_ITEM_TABLE,
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
        SMB_NORMALIZATION_ITEM_TABLE,
        ContentValues().apply {
          put("status", SmbMetadataNormalizationStatus.SKIPPED.name)
          putNull("proposed_file_name")
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
        SMB_NORMALIZATION_ITEM_TABLE,
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

  private fun SQLiteDatabase.touchBatch(batchId: String, now: Long) {
    update(
      SMB_NORMALIZATION_BATCH_TABLE,
      ContentValues().apply { put("updated_at", now) },
      "batch_id = ?",
      arrayOf(batchId),
    )
  }
}
