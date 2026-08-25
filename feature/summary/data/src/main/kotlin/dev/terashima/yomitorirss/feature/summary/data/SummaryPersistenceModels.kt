package dev.terashima.yomitorirss.feature.summary.data

internal data class SummaryRecord(
  val articleId: String,
  val summary: String,
  val modelId: String,
  val createdAt: String,
)

internal data class SummaryTaskRecord(
  val articleId: String,
  val state: String,
  val forceRefresh: Boolean,
  val replaceBookmarkTags: Boolean = false,
  val queuedAt: String,
  val startedAt: String?,
  val finishedAt: String?,
  val error: String?,
  val progressStage: String?,
  val progressCurrent: Int?,
  val progressTotal: Int?,
)

internal data class PreparedSummaryArticleContent(
  val articleId: String,
  val content: String,
  val fetchedAt: String,
)

internal const val SUMMARY_QUEUED = "queued"
internal const val SUMMARY_RUNNING = "running"
internal const val SUMMARY_COMPLETED = "completed"
internal const val SUMMARY_FAILED = "failed"
internal const val SUMMARY_STOPPED = "stopped"
internal const val SUMMARY_CANCELLED = "cancelled"

internal const val SUMMARY_PROGRESS_FETCHING_ARTICLE = "fetching_article"
internal const val SUMMARY_PROGRESS_PREPARING_MODEL = "preparing_model"
internal const val SUMMARY_PROGRESS_GENERATING_SUMMARY = "generating_summary"
internal const val SUMMARY_PROGRESS_SUMMARIZING_CHUNK = "summarizing_chunk"
internal const val SUMMARY_PROGRESS_REDUCING_SUMMARY = "reducing_summary"
internal const val SUMMARY_PROGRESS_FINALIZING_SUMMARY = "finalizing_summary"
internal const val SUMMARY_PROGRESS_CLOUD_GENERATING_SUMMARY = "cloud_generating_summary"
internal const val SUMMARY_PROGRESS_CLOUD_GENERATING_METADATA = "cloud_generating_metadata"
