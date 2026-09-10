package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.feature.podcast.PodcastEpisodeArticle
import dev.terashima.yomitorirss.feature.podcast.buildPodcastPrompt
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
  fun `実際のPodcast promptが入力上限を超えても後半の記事を落とさない`() {
    val articles = (1..5).map { number ->
      PodcastEpisodeArticle(
        articleId = "article-$number",
        feedId = "feed-1",
        title = "記事タイトル $number",
        sourceTitle = "情報源 $number",
        publishedAtEpochMillis = 100L + number,
        articleUrl = null,
        feedContent = "本文${number}。".repeat(300),
      )
    }
    val prompt = buildPodcastPrompt("朝のニュース", articles)
    assertTrue(prompt.length > 2_500)

    val bounded = limitPodcastPrompt(prompt, 2_500)

    assertTrue(bounded.length <= 2_500)
    articles.indices.forEach { index ->
      val number = index + 1
      assertTrue(bounded.contains("記事番号: $number"))
      assertTrue(bounded.contains("記事タイトル $number"))
      assertTrue(bounded.contains("情報源 $number"))
    }
  }
}
