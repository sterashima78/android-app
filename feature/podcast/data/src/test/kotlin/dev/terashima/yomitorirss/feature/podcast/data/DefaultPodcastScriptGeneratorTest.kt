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

  @Test
  fun `入力上限を超えても後半の記事を落とさない`() {
    val prompt = """
      原稿生成の指示です。

      入力記事:
      ---
      記事番号: 1
      タイトル: 一件目
      本文:
      ${"一".repeat(80)}

      ---
      記事番号: 2
      タイトル: 二件目
      本文:
      ${"二".repeat(80)}

      ---
      記事番号: 3
      タイトル: 三件目
      本文:
      ${"三".repeat(80)}
    """.trimIndent()

    val bounded = limitPodcastPrompt(prompt, 180)

    assertTrue(bounded.length <= 180)
    assertTrue(bounded.contains("記事番号: 1"))
    assertTrue(bounded.contains("記事番号: 2"))
    assertTrue(bounded.contains("記事番号: 3"))
    assertTrue(bounded.contains("一件目"))
    assertTrue(bounded.contains("二件目"))
    assertTrue(bounded.contains("三件目"))
  }
}
