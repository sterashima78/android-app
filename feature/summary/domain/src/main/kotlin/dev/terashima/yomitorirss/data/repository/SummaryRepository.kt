package dev.terashima.yomitorirss.feature.summary

sealed interface SummaryRequestResult {
  data class Cached(val summary: String) : SummaryRequestResult
  data object Processing : SummaryRequestResult
  data class PreviousFailure(val error: String) : SummaryRequestResult
  data class Enqueued(val accepted: Boolean, val forceRefresh: Boolean) : SummaryRequestResult
}

interface SummaryRepository {
  suspend fun request(articleId: String, forceRefresh: Boolean): SummaryRequestResult
}
