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
    assertTrue(prompt.length > 1_200)

    val bounded = limitPodcastPrompt(prompt, 1_200)

    assertTrue(bounded.length <= 1_200)
    assertTrue(bounded.contains("記事タイトル"))
    assertTrue(bounded.contains("情報源"))
    assertTrue(bounded.contains("[入力上限に合わせ、記事本文を抜粋しています]"))
    assertTrue(bounded.contains("本文。"))
  }

  @Test
  fun `複数記事のPodcast promptを短縮しても全記事metadataと本文を残す`() {
    val articles = listOf(
      PodcastEpisodeArticle(
        articleId = "article-1",
        feedId = "feed-1",
        title = "一つ目の記事",
        sourceTitle = "情報源A",
        publishedAtEpochMillis = 100L,
        articleUrl = null,
        feedContent = "一つ目本文。".repeat(2_000),
      ),
      PodcastEpisodeArticle(
        articleId = "article-2",
        feedId = "feed-2",
        title = "二つ目の記事",
        sourceTitle = "情報源B",
        publishedAtEpochMillis = 200L,
        articleUrl = null,
        feedContent = "二つ目本文。".repeat(2_000),
      ),
    )
    val prompt = buildPodcastChapterPrompt("朝のニュース", articles, chapterNumber = 1, totalChapters = 1)

    val bounded = limitPodcastPrompt(prompt, 1_600)

    assertTrue(bounded.length <= 1_600)
    assertTrue(bounded.contains("[入力上限に合わせ、全記事を残したまま各本文を均等に抜粋しています]"))
    assertTrue(bounded.contains("一つ目の記事"))
    assertTrue(bounded.contains("情報源A"))
    assertTrue(bounded.contains("一つ目本文。"))
    assertTrue(bounded.contains("二つ目の記事"))
    assertTrue(bounded.contains("情報源B"))
    assertTrue(bounded.contains("二つ目本文。"))
  }
}
