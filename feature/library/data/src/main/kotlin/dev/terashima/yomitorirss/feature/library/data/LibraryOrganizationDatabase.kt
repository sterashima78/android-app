package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibraryBookKey
import dev.terashima.yomitorirss.feature.library.LibraryCollection
import dev.terashima.yomitorirss.feature.library.LibraryItemOrganization
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationBatchStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidate
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationCandidateStatus
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationDraft
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationRepository
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSnapshot
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationSuggestion
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationTag
import dev.terashima.yomitorirss.feature.library.LibraryOrganizationUpdate
import dev.terashima.yomitorirss.feature.library.LibraryReadingStatus
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.organizationKey
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class DefaultLibraryOrganizationRepository(
  private val database: DatabaseConnection,
) : LibraryOrganizationRepository {
  override suspend fun snapshot(): LibraryOrganizationSnapshot = withContext(Dispatchers.IO) {
    ensureLibraryOrganizationSchema(database.writable)
    queryOrganizationSnapshot()
  }

  override suspend fun save(
    book: LibraryBook,
    draft: LibraryOrganizationDraft,
  ) {
    saveAll(listOf(LibraryOrganizationUpdate(book, draft)))
  }

  override suspend fun saveAll(updates: List<LibraryOrganizationUpdate>): Unit = withContext(Dispatchers.IO) {
    if (updates.isEmpty()) return@withContext
    val sanitized = updates.map { update ->
      sanitizeUpdate(update.book.organizationKey(), update.draft)
    }
    ensureLibraryOrganizationSchema(database.writable)
    val now = System.currentTimeMillis()
    database.transaction {
      sanitized.forEach { update -> writeOrganization(update, now) }
      Unit
    }
  }

  override suspend fun batchSnapshot(): LibraryOrganizationBatchSnapshot? = withContext(Dispatchers.IO) {
    ensureLibraryOrganizationSchema(database.writable)
    queryLatestBatchSnapshot()
  }

  override suspend fun startBatch(books: List<LibraryBook>): String = withContext(Dispatchers.IO) {
    ensureLibraryOrganizationSchema(database.writable)
    val uniqueBooks = books.distinctBy(LibraryBook::organizationKey)
    val now = System.currentTimeMillis()
    database.transaction {
      val latest = queryLatestBatchSnapshot(this)
      if (latest != null && latest.candidates.any { candidate ->
          candidate.status in ACTIVE_OR_REVIEW_CANDIDATE_STATUSES
        }
      ) {
        error("前回のAI整理候補を仕分けしてから新しい一括解析を開始してください")
      }

      val targets = uniqueBooks.filterNot { book -> hasClassification(book.organizationKey()) }
      require(targets.isNotEmpty()) { "未整理の蔵書はありません" }
      val batchId = "lbatch-${UUID.randomUUID()}"
      insertOrThrow(
        BATCH_TABLE,
        null,
        ContentValues().apply {
          put("batch_id", batchId)
          put("status", LibraryOrganizationBatchStatus.RUNNING.name)
          put("created_at", now)
          put("updated_at", now)
        },
      )
      targets.forEach { book ->
        val key = book.organizationKey()
        insertOrThrow(
          BATCH_ITEM_TABLE,
          null,
          ContentValues().apply {
            put("batch_id", batchId)
            put("source", key.source.name)
            put("source_id", key.sourceId)
            put("status", LibraryOrganizationCandidateStatus.QUEUED.name)
            put("tag_names_json", "[]")
            put("collection_names_json", "[]")
            putNull("reason")
            putNull("error")
            put("created_at", now)
            put("updated_at", now)
          },
        )
      }
      batchId
    }
  }

  override suspend fun pauseBatch(): Unit = withContext(Dispatchers.IO) {
    ensureLibraryOrganizationSchema(database.writable)
    val now = System.currentTimeMillis()
    database.transaction {
      val batchId = latestBatchId() ?: return@transaction Unit
      update(
        BATCH_TABLE,
        ContentValues().apply {
          put("status", LibraryOrganizationBatchStatus.PAUSED.name)
          put("updated_at", now)
        },
        "batch_id = ? AND status = ?",
        arrayOf(batchId, LibraryOrganizationBatchStatus.RUNNING.name),
      )
      update(
        BATCH_ITEM_TABLE,
        ContentValues().apply {
          put("status", LibraryOrganizationCandidateStatus.QUEUED.name)
          put("updated_at", now)
        },
        "batch_id = ? AND status = ?",
        arrayOf(batchId, LibraryOrganizationCandidateStatus.PROCESSING.name),
      )
      Unit
    }
  }

  override suspend fun resumeBatch(): Unit = withContext(Dispatchers.IO) {
    ensureLibraryOrganizationSchema(database.writable)
    val now = System.currentTimeMillis()
    database.transaction {
      val batchId = latestBatchId() ?: error("再開できる一括整理がありません")
      val changed = update(
        BATCH_TABLE,
        ContentValues().apply {
          put("status", LibraryOrganizationBatchStatus.RUNNING.name)
          put("updated_at", now)
        },
        "batch_id = ? AND status = ?",
        arrayOf(batchId, LibraryOrganizationBatchStatus.PAUSED.name),
      )
      require(changed > 0) { "一時停止中の一括整理がありません" }
      Unit
    }
  }

  override suspend fun updateCandidate(
    key: LibraryBookKey,
    draft: LibraryOrganizationDraft,
  ): Unit = withContext(Dispatchers.IO) {
    ensureLibraryOrganizationSchema(database.writable)
    val update = sanitizeUpdate(key, draft)
    val now = System.currentTimeMillis()
    database.transaction {
      val batchId = latestBatchId() ?: error("AI整理候補がありません")
      val changed = update(
        BATCH_ITEM_TABLE,
        ContentValues().apply {
          put("tag_names_json", namesToJson(update.tagNames))
          put("collection_names_json", namesToJson(update.collectionNames))
          put("updated_at", now)
        },
        "batch_id = ? AND source = ? AND source_id = ? AND status IN (?, ?)",
        arrayOf(
          batchId,
          key.source.name,
          key.sourceId,
          LibraryOrganizationCandidateStatus.PENDING_REVIEW.name,
          LibraryOrganizationCandidateStatus.DEFERRED.name,
        ),
      )
      require(changed > 0) { "編集できるAI整理候補がありません" }
      Unit
    }
  }

  override suspend fun acceptCandidate(
    book: LibraryBook,
    draft: LibraryOrganizationDraft,
  ): Unit = withContext(Dispatchers.IO) {
    ensureLibraryOrganizationSchema(database.writable)
    val key = book.organizationKey()
    val update = sanitizeUpdate(key, draft)
    val now = System.currentTimeMillis()
    database.transaction {
      val batchId = latestBatchId() ?: error("AI整理候補がありません")
      val candidateStatus = queryCandidateStatus(batchId, key)
      require(
        candidateStatus == LibraryOrganizationCandidateStatus.PENDING_REVIEW ||
          candidateStatus == LibraryOrganizationCandidateStatus.DEFERRED,
      ) { "採用できるAI整理候補がありません" }
      require(!hasClassification(key)) {
        "この蔵書は別の操作ですでに整理されています。現在の整理情報を確認してください"
      }
      val currentReadingStatus = queryReadingStatus(key)
      writeOrganization(update.copy(readingStatus = currentReadingStatus), now)
      update(
        BATCH_ITEM_TABLE,
        ContentValues().apply {
          put("status", LibraryOrganizationCandidateStatus.APPLIED.name)
          put("tag_names_json", namesToJson(update.tagNames))
          put("collection_names_json", namesToJson(update.collectionNames))
          putNull("error")
          put("updated_at", now)
        },
        "batch_id = ? AND source = ? AND source_id = ?",
        arrayOf(batchId, key.source.name, key.sourceId),
      )
      Unit
    }
  }

  override suspend fun deferCandidate(key: LibraryBookKey) {
    changeCandidateStatus(
      key = key,
      allowed = setOf(LibraryOrganizationCandidateStatus.PENDING_REVIEW),
      target = LibraryOrganizationCandidateStatus.DEFERRED,
    )
  }

  override suspend fun rejectCandidate(key: LibraryBookKey) {
    changeCandidateStatus(
      key = key,
      allowed = setOf(
        LibraryOrganizationCandidateStatus.PENDING_REVIEW,
        LibraryOrganizationCandidateStatus.DEFERRED,
        LibraryOrganizationCandidateStatus.FAILED,
        LibraryOrganizationCandidateStatus.SKIPPED,
      ),
      target = LibraryOrganizationCandidateStatus.REJECTED,
    )
  }

  override suspend fun reopenCandidate(key: LibraryBookKey) {
    changeCandidateStatus(
      key = key,
      allowed = setOf(
        LibraryOrganizationCandidateStatus.DEFERRED,
        LibraryOrganizationCandidateStatus.REJECTED,
      ),
      target = LibraryOrganizationCandidateStatus.PENDING_REVIEW,
    )
  }

  override suspend fun retryCandidate(key: LibraryBookKey): Unit = withContext(Dispatchers.IO) {
    ensureLibraryOrganizationSchema(database.writable)
    val now = System.currentTimeMillis()
    database.transaction {
      val batchId = latestBatchId() ?: error("再解析できる候補がありません")
      val changed = update(
        BATCH_ITEM_TABLE,
        ContentValues().apply {
          put("status", LibraryOrganizationCandidateStatus.QUEUED.name)
          putNull("error")
          put("updated_at", now)
        },
        "batch_id = ? AND source = ? AND source_id = ? AND status IN (?, ?)",
        arrayOf(
          batchId,
          key.source.name,
          key.sourceId,
          LibraryOrganizationCandidateStatus.FAILED.name,
          LibraryOrganizationCandidateStatus.SKIPPED.name,
        ),
      )
      require(changed > 0) { "再解析できる候補がありません" }
      update(
        BATCH_TABLE,
        ContentValues().apply {
          put("status", LibraryOrganizationBatchStatus.RUNNING.name)
          put("updated_at", now)
        },
        "batch_id = ?",
        arrayOf(batchId),
      )
      Unit
    }
  }

  internal fun requeueInterruptedBatchItems() {
    ensureLibraryOrganizationSchema(database.writable)
    val now = System.currentTimeMillis()
    database.transaction {
      execSQL(
        """
          UPDATE $BATCH_ITEM_TABLE
          SET status = ?, updated_at = ?
          WHERE status = ?
            AND batch_id IN (
              SELECT batch_id FROM $BATCH_TABLE WHERE status = ?
            )
        """.trimIndent(),
        arrayOf<Any?>(
          LibraryOrganizationCandidateStatus.QUEUED.name,
          now,
          LibraryOrganizationCandidateStatus.PROCESSING.name,
          LibraryOrganizationBatchStatus.RUNNING.name,
        ),
      )
      Unit
    }
  }

  internal fun claimNextBatchItem(): ClaimedLibraryOrganizationBatchItem? {
    ensureLibraryOrganizationSchema(database.writable)
    val now = System.currentTimeMillis()
    return database.transaction {
      val claimed = rawQuery(
        """
          SELECT i.batch_id, i.source, i.source_id
          FROM $BATCH_ITEM_TABLE i
          JOIN $BATCH_TABLE b ON b.batch_id = i.batch_id
          WHERE b.status = ? AND i.status = ?
          ORDER BY i.created_at, i.source, i.source_id
          LIMIT 1
        """.trimIndent(),
        arrayOf(
          LibraryOrganizationBatchStatus.RUNNING.name,
          LibraryOrganizationCandidateStatus.QUEUED.name,
        ),
      ).use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        ClaimedLibraryOrganizationBatchItem(
          batchId = cursor.getString(0),
          key = LibraryBookKey(
            source = LibrarySource.valueOf(cursor.getString(1)),
            sourceId = cursor.getString(2),
          ),
        )
      } ?: return@transaction null

      val changed = update(
        BATCH_ITEM_TABLE,
        ContentValues().apply {
          put("status", LibraryOrganizationCandidateStatus.PROCESSING.name)
          put("updated_at", now)
        },
        "batch_id = ? AND source = ? AND source_id = ? AND status = ?",
        arrayOf(
          claimed.batchId,
          claimed.key.source.name,
          claimed.key.sourceId,
          LibraryOrganizationCandidateStatus.QUEUED.name,
        ),
      )
      if (changed == 0) null else claimed
    }
  }

  internal fun saveGeneratedCandidate(
    item: ClaimedLibraryOrganizationBatchItem,
    suggestion: LibraryOrganizationSuggestion,
  ) {
    val tagNames = sanitizeNames(suggestion.tagNames, MAX_TAGS_PER_BOOK, "タグ")
    val collectionNames = sanitizeNames(
      suggestion.collectionNames,
      MAX_COLLECTIONS_PER_BOOK,
      "コレクション",
    )
    require(tagNames.isNotEmpty() || collectionNames.isNotEmpty()) { "分類候補がありませんでした" }
    val now = System.currentTimeMillis()
    database.transaction {
      update(
        BATCH_ITEM_TABLE,
        ContentValues().apply {
          put("status", LibraryOrganizationCandidateStatus.PENDING_REVIEW.name)
          put("tag_names_json", namesToJson(tagNames))
          put("collection_names_json", namesToJson(collectionNames))
          put("reason", suggestion.reason)
          putNull("error")
          put("updated_at", now)
        },
        "batch_id = ? AND source = ? AND source_id = ? AND status = ?",
        arrayOf(
          item.batchId,
          item.key.source.name,
          item.key.sourceId,
          LibraryOrganizationCandidateStatus.PROCESSING.name,
        ),
      )
      touchBatch(item.batchId, now)
      Unit
    }
  }

  internal fun failBatchItem(
    item: ClaimedLibraryOrganizationBatchItem,
    error: String,
  ) {
    changeClaimedBatchItemStatus(
      item = item,
      status = LibraryOrganizationCandidateStatus.FAILED,
      error = error,
    )
  }

  internal fun skipBatchItem(
    item: ClaimedLibraryOrganizationBatchItem,
    reason: String,
  ) {
    changeClaimedBatchItemStatus(
      item = item,
      status = LibraryOrganizationCandidateStatus.SKIPPED,
      error = reason,
    )
  }

  internal fun requeueBatchItem(item: ClaimedLibraryOrganizationBatchItem) {
    changeClaimedBatchItemStatus(
      item = item,
      status = LibraryOrganizationCandidateStatus.QUEUED,
      error = null,
    )
  }

  internal fun finishBatchIfIdle(batchId: String) {
    val now = System.currentTimeMillis()
    database.transaction {
      val remaining = rawQuery(
        "SELECT COUNT(*) FROM $BATCH_ITEM_TABLE WHERE batch_id = ? AND status IN (?, ?)",
        arrayOf(
          batchId,
          LibraryOrganizationCandidateStatus.QUEUED.name,
          LibraryOrganizationCandidateStatus.PROCESSING.name,
        ),
      ).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
      }
      if (remaining == 0) {
        update(
          BATCH_TABLE,
          ContentValues().apply {
            put("status", LibraryOrganizationBatchStatus.COMPLETED.name)
            put("updated_at", now)
          },
          "batch_id = ? AND status = ?",
          arrayOf(batchId, LibraryOrganizationBatchStatus.RUNNING.name),
        )
      }
      Unit
    }
  }

  internal fun batchTaxonomyContext(batchId: String): Pair<List<String>, List<String>> {
    ensureLibraryOrganizationSchema(database.writable)
    val snapshot = queryOrganizationSnapshot()
    val tags = snapshot.tags.map(LibraryOrganizationTag::name).toMutableList()
    val collections = snapshot.collections.map(LibraryCollection::name).toMutableList()
    database.readable.rawQuery(
      """
        SELECT tag_names_json, collection_names_json
        FROM $BATCH_ITEM_TABLE
        WHERE batch_id = ? AND status IN (?, ?)
        ORDER BY updated_at
      """.trimIndent(),
      arrayOf(
        batchId,
        LibraryOrganizationCandidateStatus.PENDING_REVIEW.name,
        LibraryOrganizationCandidateStatus.DEFERRED.name,
      ),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        addDistinctNames(tags, jsonToNames(cursor.getString(0)))
        addDistinctNames(collections, jsonToNames(cursor.getString(1)))
      }
    }
    return tags.takeLast(MAX_TAXONOMY_CONTEXT) to collections.takeLast(MAX_TAXONOMY_CONTEXT)
  }

  private suspend fun changeCandidateStatus(
    key: LibraryBookKey,
    allowed: Set<LibraryOrganizationCandidateStatus>,
    target: LibraryOrganizationCandidateStatus,
  ): Unit = withContext(Dispatchers.IO) {
    ensureLibraryOrganizationSchema(database.writable)
    val now = System.currentTimeMillis()
    database.transaction {
      val batchId = latestBatchId() ?: error("AI整理候補がありません")
      val current = queryCandidateStatus(batchId, key)
      require(current in allowed) { "この候補は現在 ${target.label} に変更できません" }
      update(
        BATCH_ITEM_TABLE,
        ContentValues().apply {
          put("status", target.name)
          put("updated_at", now)
        },
        "batch_id = ? AND source = ? AND source_id = ?",
        arrayOf(batchId, key.source.name, key.sourceId),
      )
      touchBatch(batchId, now)
      Unit
    }
  }

  private fun changeClaimedBatchItemStatus(
    item: ClaimedLibraryOrganizationBatchItem,
    status: LibraryOrganizationCandidateStatus,
    error: String?,
  ) {
    val now = System.currentTimeMillis()
    database.transaction {
      update(
        BATCH_ITEM_TABLE,
        ContentValues().apply {
          put("status", status.name)
          if (error == null) putNull("error") else put("error", error.take(MAX_ERROR_LENGTH))
          put("updated_at", now)
        },
        "batch_id = ? AND source = ? AND source_id = ? AND status = ?",
        arrayOf(
          item.batchId,
          item.key.source.name,
          item.key.sourceId,
          LibraryOrganizationCandidateStatus.PROCESSING.name,
        ),
      )
      touchBatch(item.batchId, now)
      Unit
    }
  }

  private fun queryOrganizationSnapshot(): LibraryOrganizationSnapshot {
    val tags = queryTags()
    val collections = queryCollections()
    val tagsById = tags.associateBy(LibraryOrganizationTag::id)
    val collectionsById = collections.associateBy(LibraryCollection::id)
    val itemTags = queryItemTags(tagsById)
    val itemCollections = queryItemCollections(collectionsById)
    val readingStatuses = queryReadingStatuses()
    val keys = linkedSetOf<LibraryBookKey>().apply {
      addAll(itemTags.keys)
      addAll(itemCollections.keys)
      addAll(readingStatuses.keys)
    }
    return LibraryOrganizationSnapshot(
      tags = tags,
      collections = collections,
      items = keys.associateWith { key ->
        LibraryItemOrganization(
          key = key,
          tags = itemTags[key].orEmpty(),
          collections = itemCollections[key].orEmpty(),
          readingStatus = readingStatuses[key],
        )
      },
    )
  }

  private fun queryLatestBatchSnapshot(
    db: SQLiteDatabase = database.readable,
  ): LibraryOrganizationBatchSnapshot? {
    val header = db.rawQuery(
      "SELECT batch_id, status, created_at, updated_at FROM $BATCH_TABLE ORDER BY created_at DESC LIMIT 1",
      null,
    ).use { cursor ->
      if (!cursor.moveToFirst()) return null
      BatchHeader(
        batchId = cursor.getString(0),
        status = LibraryOrganizationBatchStatus.valueOf(cursor.getString(1)),
        createdAt = cursor.getLong(2),
        updatedAt = cursor.getLong(3),
      )
    }
    val candidates = db.rawQuery(
      """
        SELECT source, source_id, status, tag_names_json, collection_names_json,
               reason, error, updated_at
        FROM $BATCH_ITEM_TABLE
        WHERE batch_id = ?
        ORDER BY created_at, source, source_id
      """.trimIndent(),
      arrayOf(header.batchId),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            LibraryOrganizationCandidate(
              batchId = header.batchId,
              key = LibraryBookKey(
                source = LibrarySource.valueOf(cursor.getString(0)),
                sourceId = cursor.getString(1),
              ),
              status = LibraryOrganizationCandidateStatus.valueOf(cursor.getString(2)),
              tagNames = jsonToNames(cursor.getString(3)),
              collectionNames = jsonToNames(cursor.getString(4)),
              reason = cursor.getString(5),
              error = cursor.getString(6),
              updatedAt = cursor.getLong(7),
            ),
          )
        }
      }
    }
    return LibraryOrganizationBatchSnapshot(
      batchId = header.batchId,
      status = header.status,
      candidates = candidates,
      createdAt = header.createdAt,
      updatedAt = header.updatedAt,
    )
  }

  private fun sanitizeUpdate(
    key: LibraryBookKey,
    draft: LibraryOrganizationDraft,
  ): SanitizedOrganizationUpdate = SanitizedOrganizationUpdate(
    key = key,
    tagNames = sanitizeNames(draft.tagNames, MAX_TAGS_PER_BOOK, "タグ"),
    collectionNames = sanitizeNames(
      draft.collectionNames,
      MAX_COLLECTIONS_PER_BOOK,
      "コレクション",
    ),
    readingStatus = draft.readingStatus,
  )

  private fun SQLiteDatabase.writeOrganization(
    update: SanitizedOrganizationUpdate,
    now: Long,
  ) {
    val key = update.key
    delete(
      ITEM_TAG_TABLE,
      "source = ? AND source_id = ?",
      arrayOf(key.source.name, key.sourceId),
    )
    update.tagNames.forEach { name ->
      val tagId = resolveTaxonomyId(
        table = TAG_TABLE,
        idColumn = "tag_id",
        name = name,
        idPrefix = "ltag",
        now = now,
      )
      insertOrThrow(
        ITEM_TAG_TABLE,
        null,
        ContentValues().apply {
          put("source", key.source.name)
          put("source_id", key.sourceId)
          put("tag_id", tagId)
          put("created_at", now)
        },
      )
    }

    delete(
      ITEM_COLLECTION_TABLE,
      "source = ? AND source_id = ?",
      arrayOf(key.source.name, key.sourceId),
    )
    update.collectionNames.forEach { name ->
      val collectionId = resolveTaxonomyId(
        table = COLLECTION_TABLE,
        idColumn = "collection_id",
        name = name,
        idPrefix = "lcol",
        now = now,
      )
      insertOrThrow(
        ITEM_COLLECTION_TABLE,
        null,
        ContentValues().apply {
          put("source", key.source.name)
          put("source_id", key.sourceId)
          put("collection_id", collectionId)
          put("created_at", now)
        },
      )
    }

    val readingStatus = update.readingStatus
    if (readingStatus == null) {
      delete(
        READING_STATUS_TABLE,
        "source = ? AND source_id = ?",
        arrayOf(key.source.name, key.sourceId),
      )
    } else {
      insertWithOnConflict(
        READING_STATUS_TABLE,
        null,
        ContentValues().apply {
          put("source", key.source.name)
          put("source_id", key.sourceId)
          put("status", readingStatus.name)
          put("updated_at", now)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
  }

  private fun SQLiteDatabase.hasClassification(key: LibraryBookKey): Boolean =
    rawQuery(
      """
        SELECT EXISTS(
          SELECT 1 FROM $ITEM_TAG_TABLE WHERE source = ? AND source_id = ?
          UNION ALL
          SELECT 1 FROM $ITEM_COLLECTION_TABLE WHERE source = ? AND source_id = ?
        )
      """.trimIndent(),
      arrayOf(
        key.source.name,
        key.sourceId,
        key.source.name,
        key.sourceId,
      ),
    ).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0) != 0
    }

  private fun SQLiteDatabase.queryReadingStatus(key: LibraryBookKey): LibraryReadingStatus? =
    rawQuery(
      "SELECT status FROM $READING_STATUS_TABLE WHERE source = ? AND source_id = ?",
      arrayOf(key.source.name, key.sourceId),
    ).use { cursor ->
      if (!cursor.moveToFirst()) return null
      runCatching { LibraryReadingStatus.valueOf(cursor.getString(0)) }.getOrNull()
    }

  private fun SQLiteDatabase.queryCandidateStatus(
    batchId: String,
    key: LibraryBookKey,
  ): LibraryOrganizationCandidateStatus? = rawQuery(
    "SELECT status FROM $BATCH_ITEM_TABLE WHERE batch_id = ? AND source = ? AND source_id = ?",
    arrayOf(batchId, key.source.name, key.sourceId),
  ).use { cursor ->
    if (!cursor.moveToFirst()) return null
    runCatching { LibraryOrganizationCandidateStatus.valueOf(cursor.getString(0)) }.getOrNull()
  }

  private fun SQLiteDatabase.latestBatchId(): String? = rawQuery(
    "SELECT batch_id FROM $BATCH_TABLE ORDER BY created_at DESC LIMIT 1",
    null,
  ).use { cursor ->
    if (cursor.moveToFirst()) cursor.getString(0) else null
  }

  private fun SQLiteDatabase.touchBatch(batchId: String, now: Long) {
    update(
      BATCH_TABLE,
      ContentValues().apply { put("updated_at", now) },
      "batch_id = ?",
      arrayOf(batchId),
    )
  }

  private fun queryTags(): List<LibraryOrganizationTag> = database.readable.rawQuery(
    "SELECT tag_id, name, normalized_name FROM $TAG_TABLE ORDER BY name COLLATE NOCASE",
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(
          LibraryOrganizationTag(
            id = cursor.getString(0),
            name = cursor.getString(1),
            normalizedName = cursor.getString(2),
          ),
        )
      }
    }
  }

  private fun queryCollections(): List<LibraryCollection> = database.readable.rawQuery(
    "SELECT collection_id, name, normalized_name FROM $COLLECTION_TABLE ORDER BY name COLLATE NOCASE",
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(
          LibraryCollection(
            id = cursor.getString(0),
            name = cursor.getString(1),
            normalizedName = cursor.getString(2),
          ),
        )
      }
    }
  }

  private fun queryItemTags(
    tagsById: Map<String, LibraryOrganizationTag>,
  ): Map<LibraryBookKey, List<LibraryOrganizationTag>> = database.readable.rawQuery(
    "SELECT source, source_id, tag_id FROM $ITEM_TAG_TABLE ORDER BY created_at, tag_id",
    null,
  ).use { cursor ->
    buildMap<LibraryBookKey, MutableList<LibraryOrganizationTag>> {
      while (cursor.moveToNext()) {
        val key = LibraryBookKey(LibrarySource.valueOf(cursor.getString(0)), cursor.getString(1))
        tagsById[cursor.getString(2)]?.let { tag -> getOrPut(key) { mutableListOf() }.add(tag) }
      }
    }
  }

  private fun queryItemCollections(
    collectionsById: Map<String, LibraryCollection>,
  ): Map<LibraryBookKey, List<LibraryCollection>> = database.readable.rawQuery(
    "SELECT source, source_id, collection_id FROM $ITEM_COLLECTION_TABLE ORDER BY created_at, collection_id",
    null,
  ).use { cursor ->
    buildMap<LibraryBookKey, MutableList<LibraryCollection>> {
      while (cursor.moveToNext()) {
        val key = LibraryBookKey(LibrarySource.valueOf(cursor.getString(0)), cursor.getString(1))
        collectionsById[cursor.getString(2)]?.let { collection ->
          getOrPut(key) { mutableListOf() }.add(collection)
        }
      }
    }
  }

  private fun queryReadingStatuses(): Map<LibraryBookKey, LibraryReadingStatus> =
    database.readable.rawQuery(
      "SELECT source, source_id, status FROM $READING_STATUS_TABLE",
      null,
    ).use { cursor ->
      buildMap {
        while (cursor.moveToNext()) {
          val status = runCatching { LibraryReadingStatus.valueOf(cursor.getString(2)) }.getOrNull()
            ?: continue
          put(
            LibraryBookKey(LibrarySource.valueOf(cursor.getString(0)), cursor.getString(1)),
            status,
          )
        }
      }
    }

  private fun SQLiteDatabase.resolveTaxonomyId(
    table: String,
    idColumn: String,
    name: String,
    idPrefix: String,
    now: Long,
  ): String {
    val normalized = normalizeLibraryOrganizationName(name)
    rawQuery(
      "SELECT $idColumn FROM $table WHERE normalized_name = ?",
      arrayOf(normalized),
    ).use { cursor ->
      if (cursor.moveToFirst()) return cursor.getString(0)
    }
    val id = "$idPrefix-${UUID.randomUUID()}"
    insertOrThrow(
      table,
      null,
      ContentValues().apply {
        put(idColumn, id)
        put("name", name)
        put("normalized_name", normalized)
        put("created_at", now)
      },
    )
    return id
  }
}

internal data class ClaimedLibraryOrganizationBatchItem(
  val batchId: String,
  val key: LibraryBookKey,
)

private data class BatchHeader(
  val batchId: String,
  val status: LibraryOrganizationBatchStatus,
  val createdAt: Long,
  val updatedAt: Long,
)

private data class SanitizedOrganizationUpdate(
  val key: LibraryBookKey,
  val tagNames: List<String>,
  val collectionNames: List<String>,
  val readingStatus: LibraryReadingStatus?,
)

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

private fun cleanLibraryOrganizationName(value: String): String =
  value.trim().replace(Regex("\\s+"), " ")

private fun sanitizeNames(
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

private fun namesToJson(values: List<String>): String = JSONArray(values).toString()

private fun jsonToNames(raw: String): List<String> = runCatching {
  val json = JSONArray(raw)
  buildList {
    for (index in 0 until json.length()) {
      json.optString(index).trim().takeIf(String::isNotEmpty)?.let(::add)
    }
  }
}.getOrDefault(emptyList())

private fun addDistinctNames(destination: MutableList<String>, values: List<String>) {
  val known = destination.mapTo(linkedSetOf(), ::normalizeLibraryOrganizationName)
  values.forEach { value ->
    if (known.add(normalizeLibraryOrganizationName(value))) destination += value
  }
}

private val ACTIVE_OR_REVIEW_CANDIDATE_STATUSES = setOf(
  LibraryOrganizationCandidateStatus.QUEUED,
  LibraryOrganizationCandidateStatus.PROCESSING,
  LibraryOrganizationCandidateStatus.PENDING_REVIEW,
  LibraryOrganizationCandidateStatus.DEFERRED,
  LibraryOrganizationCandidateStatus.FAILED,
  LibraryOrganizationCandidateStatus.SKIPPED,
)

private const val TAG_TABLE = "library_organization_tags"
private const val COLLECTION_TABLE = "library_organization_collections"
private const val ITEM_TAG_TABLE = "library_item_organization_tags"
private const val ITEM_COLLECTION_TABLE = "library_item_organization_collections"
private const val READING_STATUS_TABLE = "library_item_reading_status"
private const val BATCH_TABLE = "library_organization_batches"
private const val BATCH_ITEM_TABLE = "library_organization_batch_items"
private const val MAX_TAGS_PER_BOOK = 20
private const val MAX_COLLECTIONS_PER_BOOK = 10
private const val MAX_NAME_LENGTH = 80
private const val MAX_ERROR_LENGTH = 400
private const val MAX_TAXONOMY_CONTEXT = 100
