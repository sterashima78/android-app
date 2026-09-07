package dev.terashima.yomitorirss.feature.library.data

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
}
