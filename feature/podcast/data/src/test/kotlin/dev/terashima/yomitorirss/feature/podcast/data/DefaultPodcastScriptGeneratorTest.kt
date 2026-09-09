package dev.terashima.yomitorirss.feature.podcast.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultPodcastScriptGeneratorTest {
  @Test
  fun `promptはmodelの小さい入力上限に収める`() {
    val prompt = "abcdefghijklmnopqrstuvwxyz"

    val bounded = limitPodcastPrompt(prompt, 12)

    assertTrue(bounded.length <= 12)
    assertEquals(prompt.take(12), bounded)
  }

  @Test
  fun `promptが上限内なら変更しない`() {
    val prompt = "short prompt"

    assertEquals(prompt, limitPodcastPrompt(prompt, 100))
  }
}
