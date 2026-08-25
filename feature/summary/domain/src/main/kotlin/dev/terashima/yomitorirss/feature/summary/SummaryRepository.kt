package dev.terashima.yomitorirss.feature.summary

sealed interface SummaryRequestResult {
  data class Cached(val summary: String) : SummaryRequestResult
  data object Processing : SummaryRequestResult
  data class PreviousFailure(val error: String) : SummaryRequestResult
  data class Enqueued(val accepted: Boolean, val forceRefresh: Boolean) : SummaryRequestResult
}

interface SummaryRequester {
  suspend fun request(
    articleId: String,
    forceRefresh: Boolean,
    replaceBookmarkTags: Boolean = false,
  ): SummaryRequestResult
}

interface BookmarkEnrichmentRequester {
  /**
   * ブックマーク追加を起点に、要約とAIタグの準備をバックグラウンドで要求する。
   * 既存要約がある場合は再生成せず、その要約をタグ生成へ再利用する。
   */
  suspend fun requestBookmarkEnrichment(articleId: String): SummaryRequestResult
}

interface BookmarkEnrichmentBatchRequester {
  /**
   * 自動補完対象のブックマークをまとめて要約キューへ追加する。
   * 既存要約または既存タスクがある記事は変更せず、新規タスクだけを1回のworker起動で処理する。
   */
  suspend fun enqueueMissingBookmarkEnrichment(articleIds: List<String>): Int

  /**
   * 自動補完対象のブックマークについて、保存済み要約を再生成し、その後のAIタグ生成も再実行する。
   * queued/running の既存タスクは重複投入せず、それ以外はタグ置換指定付きで再キューする。
   */
  suspend fun enqueueBookmarkEnrichmentRefresh(articleIds: List<String>): Int
}

interface SummaryReader {
  /** 保存済みの要約を返す。AIチャット等の読み取り用途向け。 */
  suspend fun findSummary(articleId: String): String?
}

interface SummaryRepository :
  SummaryRequester,
  BookmarkEnrichmentRequester,
  BookmarkEnrichmentBatchRequester,
  SummaryReader
