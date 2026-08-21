package dev.terashima.yomitorirss.feature.library

enum class SmbMetadataNormalizationBatchStatus(val label: String) {
  RUNNING("解析中"),
  COMPLETED("解析完了"),
}

enum class SmbMetadataNormalizationStatus(val label: String) {
  WAITING_FOR_COVER("表紙待ち"),
  QUEUED("解析待ち"),
  PROCESSING("解析中"),
  PENDING_REVIEW("未確認"),
  DEFERRED("保留"),
  APPLIED("反映済み"),
  REJECTED("却下"),
  FAILED("失敗"),
  SKIPPED("対象外"),
}

data class SmbBookMetadataProposal(
  val title: String,
  val authors: List<String> = emptyList(),
  val publisher: String? = null,
  val publishedDate: String? = null,
  val isbn10: String? = null,
  val isbn13: String? = null,
  val seriesName: String? = null,
  val seriesPosition: Int? = null,
  val confidence: Float? = null,
  val reason: String? = null,
)

data class SmbMetadataNormalizationItem(
  val batchId: String,
  val sourceId: String,
  val originalFileName: String,
  val inputSize: Long,
  val inputModifiedAt: Long,
  val status: SmbMetadataNormalizationStatus,
  val proposedFileName: String? = null,
  val proposal: SmbBookMetadataProposal? = null,
  val coverUrl: String? = null,
  val error: String? = null,
  val updatedAtEpochMillis: Long,
)

data class SmbMetadataNormalizationBatchSnapshot(
  val batchId: String,
  val status: SmbMetadataNormalizationBatchStatus,
  val items: List<SmbMetadataNormalizationItem>,
  val createdAtEpochMillis: Long,
  val updatedAtEpochMillis: Long,
) {
  val total: Int get() = items.size
  val waitingForCover: Int get() = items.count { it.status == SmbMetadataNormalizationStatus.WAITING_FOR_COVER }
  val queued: Int get() = items.count { it.status == SmbMetadataNormalizationStatus.QUEUED }
  val processing: Int get() = items.count { it.status == SmbMetadataNormalizationStatus.PROCESSING }
  val pendingReview: Int get() = items.count { it.status == SmbMetadataNormalizationStatus.PENDING_REVIEW }
  val deferred: Int get() = items.count { it.status == SmbMetadataNormalizationStatus.DEFERRED }
  val applied: Int get() = items.count { it.status == SmbMetadataNormalizationStatus.APPLIED }
  val rejected: Int get() = items.count { it.status == SmbMetadataNormalizationStatus.REJECTED }
  val failed: Int get() = items.count {
    it.status == SmbMetadataNormalizationStatus.FAILED || it.status == SmbMetadataNormalizationStatus.SKIPPED
  }
  val hasActiveWork: Boolean get() = waitingForCover > 0 || queued > 0 || processing > 0
  val hasUnresolvedReview: Boolean get() = pendingReview > 0 || deferred > 0 || failed > 0
}

interface SmbMetadataNormalizationRepository {
  suspend fun batchSnapshot(): SmbMetadataNormalizationBatchSnapshot?

  suspend fun startBatch(books: List<LibraryBook>): Int

  suspend fun applyCandidate(
    sourceId: String,
    proposedFileName: String,
    proposal: SmbBookMetadataProposal,
  )

  suspend fun deferCandidate(sourceId: String)

  suspend fun rejectCandidate(sourceId: String)

  suspend fun reopenCandidate(sourceId: String)

  suspend fun retryCandidate(sourceId: String)
}

interface SmbMetadataNormalizationScheduler {
  fun kick()

  suspend fun cancel()

  fun setResumeOnChargingScheduled(enabled: Boolean)
}
