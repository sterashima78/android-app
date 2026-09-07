package dev.terashima.yomitorirss.feature.audio.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

  @Test
  fun `最初の音声準備完了を後続音声の準備完了より先に通知する`() = runBlocking {
    val allowSecondPreparation = CompletableDeferred<Unit>()
    val firstPrepared = CompletableDeferred<Unit>()
    val preparedItems = mutableListOf<Int>()
    var preparedCount = 0

    val job = launch {
      preparedCount = prepareProgressively(
        items = listOf(1, 2),
        prepare = { item ->
          if (item == 2) allowSecondPreparation.await()
          "audio-$item"
        },
        onPrepared = { item, _, count ->
          preparedItems += item
          if (count == 1) firstPrepared.complete(Unit)
        },
      )
    }

    firstPrepared.await()
    assertEquals(listOf(1), preparedItems)

    allowSecondPreparation.complete(Unit)
    job.join()

    assertEquals(listOf(1, 2), preparedItems)
    assertEquals(2, preparedCount)
  }
}
