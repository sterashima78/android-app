package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.SmbMediaLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SmbMediaPathTest {
  @Test
  fun `SMB media pathは区切りとdotを正規化する`() {
    assertEquals(
      "videos\\movie.mp4",
      normalizeMediaSmbPath("videos/./movie.mp4"),
    )
  }

  @Test
  fun `SMB media pathは親ディレクトリ移動を拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      normalizeMediaSmbPath("videos\\..\\private\\movie.mp4")
    }
  }

  @Test
  fun `SMB一覧取得失敗は接続名とshare pathと内側の原因を含める`() {
    val message = smbMediaListFailureMessage(
      profileName = "家庭内サーバー",
      location = SmbMediaLocation("server-1", "media", "videos\\movies"),
      error = IllegalStateException(null, IllegalArgumentException("接続処理に失敗しました")),
    )

    assertEquals(
      "家庭内サーバー (media/videos/movies) のSMB動画一覧を取得できませんでした: 接続処理に失敗しました",
      message,
    )
  }

  @Test
  fun `SMB一覧取得失敗のmessageが空なら例外種別を含める`() {
    val message = smbMediaListFailureMessage(
      profileName = "家庭内サーバー",
      location = SmbMediaLocation("server-1", "media", ""),
      error = IllegalStateException(),
    )

    assertEquals(
      "家庭内サーバー (media) のSMB動画一覧を取得できませんでした: IllegalStateException",
      message,
    )
  }
}
