package dev.terashima.yomitorirss.feature.audio.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultAudioPlaybackControllerTest {
  @Test
  fun `インストール済みのオフライン日本語音声だけを利用する`() {
    assertTrue(
      isInstalledOfflineJapaneseVoice(
        language = "ja",
        isNetworkConnectionRequired = false,
        features = emptySet(),
      ),
    )

    assertFalse(
      isInstalledOfflineJapaneseVoice(
        language = "ja",
        isNetworkConnectionRequired = false,
        features = setOf("notInstalled"),
      ),
    )
  }

  @Test
  fun `ネットワーク音声と日本語以外の音声は利用しない`() {
    assertFalse(
      isInstalledOfflineJapaneseVoice(
        language = "ja",
        isNetworkConnectionRequired = true,
        features = emptySet(),
      ),
    )

    assertFalse(
      isInstalledOfflineJapaneseVoice(
        language = "en",
        isNetworkConnectionRequired = false,
        features = emptySet(),
      ),
    )
  }
}
