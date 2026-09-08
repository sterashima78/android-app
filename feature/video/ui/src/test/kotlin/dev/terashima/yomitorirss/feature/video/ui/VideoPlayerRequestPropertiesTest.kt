package dev.terashima.yomitorirss.feature.video.ui

import androidx.media3.common.Player
import dev.terashima.yomitorirss.feature.video.VideoPlaybackCookieProvider
import dev.terashima.yomitorirss.feature.video.VideoPlaybackTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerRequestPropertiesTest {
  @Test
  fun `Webストリームは元ページoriginをRefererとOriginとして送る`() {
    val target = VideoPlaybackTarget.Stream(
      url = "https://cdn.example.com/video/master.m3u8",
      mimeType = "application/x-mpegURL",
      referrerUrl = "https://example.com/",
    )

    assertEquals(
      mapOf(
        "Referer" to "https://example.com/",
        "Origin" to "https://example.com",
      ),
      webStreamRequestProperties(target),
    )
  }

  @Test
  fun `Originはpathやqueryを含めずportを保持する`() {
    assertEquals(
      "https://example.com:8443",
      webStreamOriginHeaderValue("https://example.com:8443/watch/1?x=1#section"),
    )
  }

  @Test
  fun `参照元がないWebストリームは追加headerを送らない`() {
    val target = VideoPlaybackTarget.Stream(
      url = "https://cdn.example.com/video/master.m3u8",
    )

    assertTrue(webStreamRequestProperties(target).isEmpty())
  }

  @Test
  fun `CookieはMedia3のrequest URLごとにproviderへ問い合わせる`() {
    val requested = mutableListOf<String>()
    val provider = VideoPlaybackCookieProvider { url ->
      requested += url
      "a=b"
    }
    val requestUrl = "https://media.example.com/video/segment-1.ts"

    assertEquals(
      mapOf("Cookie" to "a=b"),
      webStreamCookieRequestProperties(requestUrl, provider),
    )
    assertEquals(listOf(requestUrl), requested)
  }

  @Test
  fun `redirect先を含む各request URLでCookieを引き直す`() {
    val requested = mutableListOf<String>()
    val provider = VideoPlaybackCookieProvider { url ->
      requested += url
      when {
        "edge.example.com" in url -> "edge=1"
        "media.example.com" in url -> "media=2"
        else -> null
      }
    }

    val first = webVideoRequestProperties(
      requestUrl = "https://edge.example.com/master.m3u8",
      defaultRequestProperties = mapOf("Referer" to "https://example.com/"),
      dataSpecRequestProperties = emptyMap(),
      cookieProvider = provider,
    )
    val redirected = webVideoRequestProperties(
      requestUrl = "https://media.example.com/master.m3u8",
      defaultRequestProperties = mapOf("Referer" to "https://example.com/"),
      dataSpecRequestProperties = emptyMap(),
      cookieProvider = provider,
    )

    assertEquals("edge=1", first["Cookie"])
    assertEquals("media=2", redirected["Cookie"])
    assertEquals(
      listOf(
        "https://edge.example.com/master.m3u8",
        "https://media.example.com/master.m3u8",
      ),
      requested,
    )
  }

  @Test
  fun `CookieがないrequestにはCookie headerを追加しない`() {
    val provider = VideoPlaybackCookieProvider { null }

    assertTrue(
      webStreamCookieRequestProperties(
        "https://media.example.com/video/segment-1.ts",
        provider,
      ).isEmpty(),
    )
  }

  @Test
  fun `Cookie取得失敗時はcredentialを付けず再生requestを継続できる`() {
    val provider = VideoPlaybackCookieProvider { error("lookup failed") }

    assertTrue(
      webStreamCookieRequestProperties(
        "https://media.example.com/video/segment-1.ts",
        provider,
      ).isEmpty(),
    )
  }

  @Test
  fun `再生準備中は準備状態を表示する`() {
    val status = videoPlayerStatusUi(
      playbackState = Player.STATE_IDLE,
      loadingElapsedMs = 0L,
      errorCodeName = null,
    )

    assertEquals("再生を準備しています…", status?.message)
    assertTrue(status?.showProgress == true)
    assertFalse(status?.canRetry == true)
  }

  @Test
  fun `読み込み開始直後は進行中として表示する`() {
    val status = videoPlayerStatusUi(
      playbackState = Player.STATE_BUFFERING,
      loadingElapsedMs = 3_000L,
      errorCodeName = null,
    )

    assertEquals("動画を読み込んでいます…", status?.message)
    assertTrue(status?.showProgress == true)
    assertFalse(status?.canRetry == true)
  }

  @Test
  fun `10秒以上の読み込みは時間がかかっていることを表示する`() {
    val status = videoPlayerStatusUi(
      playbackState = Player.STATE_BUFFERING,
      loadingElapsedMs = 12_000L,
      errorCodeName = null,
    )

    assertEquals("再生開始を待っています（12秒）。通常より時間がかかっています。", status?.message)
    assertFalse(status?.canRetry == true)
  }

  @Test
  fun `30秒以上の読み込みはエラー未検出として再試行を出す`() {
    val status = videoPlayerStatusUi(
      playbackState = Player.STATE_BUFFERING,
      loadingElapsedMs = 30_000L,
      errorCodeName = null,
    )

    assertEquals("30秒以上読み込みが続いています。再生エラーはまだ検出されていません。", status?.message)
    assertTrue(status?.showProgress == true)
    assertTrue(status?.canRetry == true)
  }

  @Test
  fun `HTTP Playerエラーはステータス番号も表示状態へ渡す`() {
    val status = videoPlayerStatusUi(
      playbackState = Player.STATE_IDLE,
      loadingElapsedMs = 0L,
      errorCodeName = "ERROR_CODE_IO_BAD_HTTP_STATUS",
      httpStatusCode = 403,
    )

    assertEquals("再生できません。", status?.message)
    assertEquals("ERROR_CODE_IO_BAD_HTTP_STATUS", status?.errorCodeName)
    assertEquals(403, status?.httpStatusCode)
    assertFalse(status?.showProgress == true)
    assertTrue(status?.canRetry == true)
  }

  @Test
  fun `HTTP番号がないPlayerエラーも従来どおり表示する`() {
    val status = videoPlayerStatusUi(
      playbackState = Player.STATE_IDLE,
      loadingElapsedMs = 0L,
      errorCodeName = "ERROR_CODE_IO_NETWORK_CONNECTION_FAILED",
    )

    assertEquals("再生できません。", status?.message)
    assertNull(status?.httpStatusCode)
    assertTrue(status?.canRetry == true)
  }

  @Test
  fun `再生可能状態では状態オーバーレイを表示しない`() {
    assertNull(
      videoPlayerStatusUi(
        playbackState = Player.STATE_READY,
        loadingElapsedMs = 0L,
        errorCodeName = null,
      ),
    )
  }
}
