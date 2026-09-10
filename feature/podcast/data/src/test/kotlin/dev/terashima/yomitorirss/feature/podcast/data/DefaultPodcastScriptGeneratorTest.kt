package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.feature.podcast.PodcastEpisodeArticle
import dev.terashima.yomitorirss.feature.podcast.buildPodcastChapterPrompt
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
  fun `1記事のPodcast promptが入力上限を超えても記事metadataと本文先頭を残す`() {
    val article = PodcastEpisodeArticle(
      articleId = "article-1",
      feedId = "feed-1",
      title = "記事タイトル",
      sourceTitle = "情報源",
      publishedAtEpochMillis = 100L,
      articleUrl = null,
      feedContent = "本文。".repeat(3_000),
    )
    val prompt = buildPodcastChapterPrompt("朝のニュース", article, chapterNumber = 1, totalChapters = 1)
    assertTrue(prompt.length > 2_500)

    val bounded = limitPodcastPrompt(prompt, 2_500)

    assertTrue(bounded.length <= 2_500)
    assertTrue(bounded.contains("記事タイトル"))
    assertTrue(bounded.contains("情報源"))
    assertTrue(bounded.contains("本文。"))
  }
}
