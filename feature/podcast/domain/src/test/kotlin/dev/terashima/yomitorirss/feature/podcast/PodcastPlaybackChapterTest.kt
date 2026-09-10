package dev.terashima.yomitorirss.feature.podcast

import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastPlaybackChapterTest {
  @Test
  fun `重要度順に並び替えられたチャプターを元記事へ対応付ける`() {
    val first = article("a1")
    val second = article("a2")
    val episode = PodcastEpisode(
      id = "episode-1",
      programId = "program-1",
      title = "ニュース",
      createdAtEpochMillis = 100L,
      status = PodcastEpisodeStatus.READY,
      articles = listOf(first, second),
      script = """
        [[CHAPTER:2]]
        重要度の高い2件目。
        [[CHAPTER:1]]
        続いて1件目。
      """.trimIndent(),
    )

    val chapters = episode.playbackChapters()

    assertEquals(listOf(1, 2), chapters.map { it.number })
    assertEquals(listOf("a2", "a1"), chapters.map { it.article?.articleId })
    assertEquals(listOf("重要度の高い2件目。", "続いて1件目。"), chapters.map { it.speechText })
  }

  @Test
  fun `重複した記事番号のチャプターは全文再生へフォールバックする`() {
    val episode = PodcastEpisode(
      id = "episode-1",
      programId = "program-1",
      title = "ニュース",
      createdAtEpochMillis = 100L,
      status = PodcastEpisodeStatus.READY,
      articles = listOf(article("a1"), article("a2")),
      script = """
        [[CHAPTER:1]]
        1件目。
        [[CHAPTER:1]]
        重複。
      """.trimIndent(),
    )

    val chapters = episode.playbackChapters()

    assertEquals(1, chapters.size)
    assertEquals(null, chapters.single().article)
    assertEquals(episode.script, chapters.single().speechText)
  }
}

private fun article(id: String) = PodcastEpisodeArticle(
  articleId = id,
  feedId = "source-1",
  title = "記事 $id",
  sourceTitle = "情報源",
  publishedAtEpochMillis = 100L,
  articleUrl = "https://example.invalid/$id",
  feedContent = "本文 $id",
)
