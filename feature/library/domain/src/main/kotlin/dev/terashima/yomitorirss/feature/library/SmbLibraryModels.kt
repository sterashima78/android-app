package dev.terashima.yomitorirss.feature.library

data class SmbServerSettings(
  val id: String,
  val name: String,
  val host: String,
  val port: Int = 445,
  val share: String,
  val rootPath: String = "",
  val username: String,
  val domain: String = "",
  val credentialConfigured: Boolean = false,
)

data class PreparedLibraryBook(
  val sourceId: String,
  val title: String,
  val localPath: String,
  val format: SmbBookFormat,
)

enum class SmbBookFormat {
  ZIP,
  PDF,
}

enum class SmbCoverPrefetchStatus {
  PENDING,
  RUNNING,
  FAILED,
  COMPLETED,
  SKIPPED,
}

enum class SmbCoverPrefetchWorkerState {
  IDLE,
  ENQUEUED,
  RUNNING,
  BLOCKED,
  FAILED,
  CANCELLED,
  UNKNOWN,
}

enum class SmbCoverPrefetchWaitReason {
  WIFI,
  BATTERY,
  SCHEDULER,
}

data class SmbCoverPrefetchRuntimeSnapshot(
  val state: SmbCoverPrefetchWorkerState = SmbCoverPrefetchWorkerState.IDLE,
  val waitReason: SmbCoverPrefetchWaitReason? = null,
)

data class SmbCoverPrefetchItem(
  val sourceId: String,
  val title: String,
  val status: SmbCoverPrefetchStatus,
  val downloadedBytes: Long,
  val totalBytes: Long,
  val message: String?,
  val updatedAtEpochMillis: Long,
)

data class SmbCoverPrefetchSnapshot(
  val items: List<SmbCoverPrefetchItem> = emptyList(),
  val pendingCount: Int = 0,
  val runningCount: Int = 0,
  val failedCount: Int = 0,
  val completedCount: Int = 0,
  val skippedCount: Int = 0,
  val runtime: SmbCoverPrefetchRuntimeSnapshot = SmbCoverPrefetchRuntimeSnapshot(),
) {
  val hasActiveWork: Boolean get() = pendingCount > 0 || runningCount > 0
}

interface SmbCoverPrefetchScheduler {
  fun enqueue()

  fun reschedule()
}

interface SmbLibraryRepository {
  suspend fun servers(): List<SmbServerSettings>

  suspend fun saveServer(
    settings: SmbServerSettings,
    password: String?,
  ): SmbServerSettings

  suspend fun deleteServer(serverId: String)

  suspend fun sync(): LibrarySyncResult

  suspend fun renameBook(
    book: LibraryBook,
    newFileName: String,
  ): LibraryBook

  suspend fun deleteBook(book: LibraryBook)

  suspend fun prepareBook(
    book: LibraryBook,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
  ): PreparedLibraryBook

  suspend fun coverPrefetchSnapshot(): SmbCoverPrefetchSnapshot = SmbCoverPrefetchSnapshot()

  suspend fun enqueueMissingCoverPrefetch(): Int = 0

  suspend fun retryFailedCoverPrefetch(): Int = 0
}
