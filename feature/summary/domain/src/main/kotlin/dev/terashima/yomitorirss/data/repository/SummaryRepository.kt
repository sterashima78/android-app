package dev.terashima.yomitorirss.feature.summary

sealed interface SummaryRequestResult {
  data class Cached(val summary: String) : SummaryRequestResult
  data object Processing : SummaryRequestResult
  data class PreviousFailure(val error: String) : SummaryRequestResult
  data class Enqueued(val accepted: Boolean, val forceRefresh: Boolean) : SummaryRequestResult
}

interface SummaryRepository {
  suspend fun request(articleId: String, forceRefresh: Boolean): SummaryRequestResult

  /**
   * ブックマーク追加を起点に、要約とAIタグの準備をバックグラウンドで要求する。
   * 既存要約がある場合は再生成せず、その要約をタグ生成へ再利用する。
   */
  suspend fun requestBookmarkEnrichment(articleId: String): SummaryRequestResult

  /** 保存済みの要約を返す。AIチャット等の読み取り用途向け。 */
  suspend fun findSummary(articleId: String): String?
}
