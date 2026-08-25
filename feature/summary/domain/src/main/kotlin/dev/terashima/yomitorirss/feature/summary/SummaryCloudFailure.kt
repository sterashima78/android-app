package dev.terashima.yomitorirss.feature.summary

enum class SummaryCloudFailureKind {
  AUTHENTICATION,
  RATE_LIMITED,
  TRANSIENT,
  REQUEST_REJECTED,
  UNKNOWN,
}

class SummaryCloudInferenceException(
  val kind: SummaryCloudFailureKind,
  val retryable: Boolean,
  message: String,
  cause: Throwable? = null,
) : RuntimeException(message, cause)
