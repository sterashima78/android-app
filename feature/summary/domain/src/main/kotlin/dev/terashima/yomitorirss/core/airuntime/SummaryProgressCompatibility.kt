package dev.terashima.yomitorirss.core.airuntime

/**
 * Source-compatibility shim for the legacy app-shell progress-label helper.
 *
 * The summary runtime state is owned by feature modules now. New code must use
 * feature-specific progress models instead of this legacy type.
 */
@Deprecated("Use the summary/settings feature progress model")
data class SummaryProgress(
  val stage: String,
  val modelName: String? = null,
  val estimatedStageDurationMillis: Long? = null,
)
