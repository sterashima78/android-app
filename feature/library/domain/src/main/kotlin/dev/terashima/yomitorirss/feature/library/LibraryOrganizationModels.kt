package dev.terashima.yomitorirss.feature.library

enum class LibraryReadingStatus(val label: String) {
  UNREAD("未読"),
  READING("読書中"),
  FINISHED("読了"),
  PAUSED("中断中"),
  ABANDONED("中止"),
}

data class LibraryBookKey(
  val source: LibrarySource,
  val sourceId: String,
)

data class LibraryOrganizationTag(
  val id: String,
  val name: String,
  val normalizedName: String,
)

data class LibraryCollection(
  val id: String,
  val name: String,
  val normalizedName: String,
)

data class LibraryItemOrganization(
  val key: LibraryBookKey,
  val tags: List<LibraryOrganizationTag> = emptyList(),
  val collections: List<LibraryCollection> = emptyList(),
  val readingStatus: LibraryReadingStatus? = null,
)

data class LibraryOrganizationSnapshot(
  val tags: List<LibraryOrganizationTag> = emptyList(),
  val collections: List<LibraryCollection> = emptyList(),
  val items: Map<LibraryBookKey, LibraryItemOrganization> = emptyMap(),
) {
  fun organizationFor(book: LibraryBook): LibraryItemOrganization =
    items[book.organizationKey()] ?: LibraryItemOrganization(book.organizationKey())
}

data class LibraryOrganizationDraft(
  val tagNames: List<String>,
  val collectionNames: List<String>,
  val readingStatus: LibraryReadingStatus?,
)

data class LibraryOrganizationUpdate(
  val book: LibraryBook,
  val draft: LibraryOrganizationDraft,
)

data class LibraryOrganizationSuggestion(
  val tagNames: List<String>,
  val collectionNames: List<String>,
  val reason: String?,
)

data class LibraryOrganizationSeriesContext(
  val tagNames: List<String> = emptyList(),
  val collectionNames: List<String> = emptyList(),
)

enum class LibraryOrganizationBatchStatus(val label: String) {
  RUNNING("解析中"),
  PAUSED("一時停止"),
  COMPLETED("解析完了"),
}

enum class LibraryOrganizationCandidateStatus(val label: String) {
  QUEUED("解析待ち"),
  PROCESSING("解析中"),
  PENDING_REVIEW("未確認"),
  DEFERRED("保留"),
  APPLIED("採用済み"),
  REJECTED("却下"),
  FAILED("失敗"),
  SKIPPED("スキップ"),
}

data class LibraryOrganizationCandidate(
  val batchId: String,
  val key: LibraryBookKey,
  val status: LibraryOrganizationCandidateStatus,
  val tagNames: List<String> = emptyList(),
  val collectionNames: List<String> = emptyList(),
  val reason: String? = null,
  val error: String? = null,
  val updatedAt: Long,
)

data class LibraryOrganizationBatchSnapshot(
  val batchId: String,
  val status: LibraryOrganizationBatchStatus,
  val candidates: List<LibraryOrganizationCandidate>,
  val createdAt: Long,
  val updatedAt: Long,
) {
  val total: Int get() = candidates.size
  val processed: Int get() = candidates.count {
    it.status != LibraryOrganizationCandidateStatus.QUEUED &&
      it.status != LibraryOrganizationCandidateStatus.PROCESSING
  }
  val pendingReview: Int get() = candidates.count {
    it.status == LibraryOrganizationCandidateStatus.PENDING_REVIEW
  }
  val deferred: Int get() = candidates.count {
    it.status == LibraryOrganizationCandidateStatus.DEFERRED
  }
}

fun LibraryBook.organizationKey(): LibraryBookKey = LibraryBookKey(source, sourceId)

interface LibraryOrganizationRepository {
  suspend fun snapshot(): LibraryOrganizationSnapshot

  suspend fun save(
    book: LibraryBook,
    draft: LibraryOrganizationDraft,
  )

  suspend fun saveAll(updates: List<LibraryOrganizationUpdate>) {
    updates.forEach { update -> save(update.book, update.draft) }
  }

  suspend fun batchSnapshot(): LibraryOrganizationBatchSnapshot?

  suspend fun startBatch(books: List<LibraryBook>): String

  suspend fun pauseBatch()

  suspend fun resumeBatch()

  suspend fun updateCandidate(
    key: LibraryBookKey,
    draft: LibraryOrganizationDraft,
  )

  suspend fun acceptCandidate(
    book: LibraryBook,
    draft: LibraryOrganizationDraft,
  )

  suspend fun deferCandidate(key: LibraryBookKey)

  suspend fun rejectCandidate(key: LibraryBookKey)

  suspend fun reopenCandidate(key: LibraryBookKey)

  suspend fun retryCandidate(key: LibraryBookKey)
}

interface LibraryOrganizationBatchScheduler {
  fun kick()

  suspend fun cancel()

  fun setResumeOnChargingScheduled(enabled: Boolean)
}

interface LibraryOrganizationSuggester {
  suspend fun suggest(
    book: LibraryBook,
    existingTags: List<String>,
    existingCollections: List<String>,
    seriesContext: LibraryOrganizationSeriesContext? = null,
  ): LibraryOrganizationSuggestion
}
