package dev.terashima.yomitorirss.feature.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioPlaybackTest {
  @Test
  fun `再生キューは同じコンテンツを重複させず最初の順序を維持する`() {
    val first = AudioQueueItem("a", "A", "Source A")
    val duplicate = AudioQueueItem("a", "A duplicate", "Other")
    val second = AudioQueueItem("b", "B", "Source B")

    assertEquals(listOf(first, second), normalizeAudioQueue(listOf(first, duplicate, second)))
  }

  @Test
  fun `現在位置が範囲外なら現在項目を返さない`() {
    val item = AudioQueueItem("a", "A", null)

    assertNull(AudioPlaybackState(items = listOf(item), currentIndex = -1).currentItem)
    assertNull(AudioPlaybackState(items = listOf(item), currentIndex = 1).currentItem)
  }

  @Test
  fun `現在位置に対応する項目を返す`() {
    val first = AudioQueueItem("a", "A", null)
    val second = AudioQueueItem("b", "B", null)

    assertEquals(
      second,
      AudioPlaybackState(items = listOf(first, second), currentIndex = 1).currentItem,
    )
  }
}
