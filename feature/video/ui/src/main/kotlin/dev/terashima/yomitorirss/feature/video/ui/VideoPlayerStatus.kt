package dev.terashima.yomitorirss.feature.video.ui

import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import dev.terashima.yomitorirss.feature.video.WebVideoPlaybackDiagnostics
import dev.terashima.yomitorirss.feature.video.WebVideoPlaybackReferrerSource
import dev.terashima.yomitorirss.feature.video.WebVideoSecFetchSite

internal const val VIDEO_PLAYER_SLOW_LOADING_MS = 10_000L
internal const val VIDEO_PLAYER_STALLED_LOADING_MS = 30_000L

internal data class VideoPlayerStatusUi(
  val message: String,
  val showProgress: Boolean,
  val canRetry: Boolean,
  val errorCodeName: String? = null,
  val httpStatusCode: Int? = null,
)

internal fun videoPlayerStatusUi(
  playbackState: Int,
  loadingElapsedMs: Long,
  errorCodeName: String?,
  httpStatusCode: Int? = null,
): VideoPlayerStatusUi? {
  if (!errorCodeName.isNullOrBlank()) {
    return VideoPlayerStatusUi(
      message = "再生できません。",
      showProgress = false,
      canRetry = true,
      errorCodeName = errorCodeName,
      httpStatusCode = httpStatusCode,
    )
  }

  val isLoading = playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING
  if (!isLoading) return null

  return when {
    loadingElapsedMs >= VIDEO_PLAYER_STALLED_LOADING_MS -> VideoPlayerStatusUi(
      message = "30秒以上読み込みが続いています。再生エラーはまだ検出されていません。",
      showProgress = true,
      canRetry = true,
    )
    loadingElapsedMs >= VIDEO_PLAYER_SLOW_LOADING_MS -> VideoPlayerStatusUi(
      message = "再生開始を待っています（${loadingElapsedMs / 1_000}秒）。通常より時間がかかっています。",
      showProgress = true,
      canRetry = false,
    )
    playbackState == Player.STATE_IDLE -> VideoPlayerStatusUi(
      message = "再生を準備しています…",
      showProgress = true,
      canRetry = false,
    )
    else -> VideoPlayerStatusUi(
      message = "動画を読み込んでいます…",
      showProgress = true,
      canRetry = false,
    )
  }
}

internal fun webVideoPlaybackDiagnosticLines(
  diagnostics: WebVideoPlaybackDiagnostics?,
  nativeRequestProperties: Map<String, String> = emptyMap(),
): List<String> {
  if (diagnostics == null) return listOf("Web診断: 情報なし")

  val cookieSource = when {
    !diagnostics.cookieSharingEnabled -> "共有OFF"
    diagnostics.streamRequestCookieObserved -> "実request"
    diagnostics.profileCookieAvailable -> "profile fallback"
    else -> "なし"
  }
  val referrer = when {
    !diagnostics.streamRequestRefererObserved -> "なし"
    diagnostics.streamRequestRefererHasPathOrQuery -> "あり（path/queryあり）"
    else -> "あり（originのみ）"
  }
  val origin = when {
    !diagnostics.streamRequestOriginObserved -> "なし"
    diagnostics.streamRequestOriginMatchesReferrerOrigin == true -> "あり（Referer originと一致）"
    diagnostics.streamRequestOriginMatchesReferrerOrigin == false -> "あり（Referer originと不一致）"
    else -> "あり（比較不可）"
  }
  val secFetchSite = when (diagnostics.secFetchSite) {
    WebVideoSecFetchSite.SAME_ORIGIN -> "same-origin"
    WebVideoSecFetchSite.SAME_SITE -> "same-site"
    WebVideoSecFetchSite.CROSS_SITE -> "cross-site"
    WebVideoSecFetchSite.NONE -> "none"
    WebVideoSecFetchSite.OTHER -> "other"
    null -> "なし"
  }
  val playbackReferrerSource = when (diagnostics.playbackReferrerSource) {
    WebVideoPlaybackReferrerSource.OBSERVED_REQUEST -> "WebView実request"
    WebVideoPlaybackReferrerSource.EXTRACTOR -> "extractor指定"
    WebVideoPlaybackReferrerSource.PAGE -> "元ページ"
    null -> "不明"
  }

  return listOf(
    "WebView stream request: ${if (diagnostics.streamRequestObserved) "観測" else "未観測"}",
    "Cookie intercept: ${if (diagnostics.cookieInterceptSupported) "対応" else "非対応"}",
    "Cookie: $cookieSource",
    "WebView Referer: $referrer",
    "WebView Origin: $origin",
    "Sec-Fetch-Site: $secFetchSite",
    "再生参照元: $playbackReferrerSource",
    "Native Referer: ${if (nativeRequestProperties.containsKey("Referer")) "あり" else "なし"}",
    "Native Origin: ${if (nativeRequestProperties.containsKey("Origin")) "あり" else "なし"}",
  )
}

internal fun findHttpStatusCode(error: Throwable?): Int? {
  var current = error
  while (current != null) {
    if (current is HttpDataSource.InvalidResponseCodeException) return current.responseCode
    current = current.cause
  }
  return null
}
