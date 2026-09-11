package dev.terashima.yomitorirss.feature.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastSpokenHeadlineTest {
  @Test
  fun `記事単位promptは日本語の読み上げ見出しを要求する`() {
    val prompt = buildPodcastChapterPrompt(
      programName = "朝のニュース",
      article = article(title = "New on-device AI APIs released"),
      chapterNumber = 1,
      totalChapters = 2,
    )

    assertTrue(prompt.contains("[[TITLE:日本語の見出し]]"))
    assertTrue(prompt.contains("自然な日本語へ翻訳・言い換える"))
    assertTrue(prompt.contains("元記事のタイトルをそのまま複製せず"))
  }

  @Test
  fun `生成した日本語見出しを読み上げタイトルへ使い二件目以降に切替キューを付ける`() {
    val episode = PodcastEpisode(
      id = "episode-1",
      programId = "program-1",
      title = "朝のニュース",
      createdAtEpochMillis = 1L,
      status = PodcastEpisodeStatus.READY,
      articles = listOf(
        article(title = "First original title"),
        article(id = "article-2", title = "Second original title"),
      ),
      script = """
        [[CHAPTER:1]]
        朝のニュースです。今回のニュースをお伝えします。 [[TITLE:端末内AIの新APIを公開]]
        1件目の本文です。

        [[CHAPTER:2]]
        [[TITLE:新しい開発ツールが登場]]
        2件目の本文です。 以上、今回のニュースでした。
      """.trimIndent(),
    )

    val chapters = episode.playbackChapters()

    assertEquals("端末内AIの新APIを公開", chapters[0].article?.title)
    assertEquals("続いて。新しい開発ツールが登場", chapters[1].article?.title)
    assertFalse(chapters[0].speechText.contains("[[TITLE:"))
    assertFalse(chapters[1].speechText.contains("[[TITLE:"))
    assertTrue(chapters[0].speechText.contains("1件目の本文です。"))
    assertTrue(chapters[1].speechText.contains("2件目の本文です。"))
  }

  @Test
  fun `旧形式チャプターは元記事タイトルのまま再生できる`() {
    val episode = PodcastEpisode(
      id = "episode-old",
      programId = "program-1",
      title = "過去のニュース",
      createdAtEpochMillis = 1L,
      status = PodcastEpisodeStatus.READY,
      articles = listOf(article(title = "保存済みの元記事タイトル")),
      script = """
        [[CHAPTER:1]]
        保存済みの旧形式本文です。
      """.trimIndent(),
    )

    val chapter = episode.playbackChapters().single()

    assertEquals("保存済みの元記事タイトル", chapter.article?.title)
    assertEquals("保存済みの旧形式本文です。", chapter.speechText)
  }

  private fun article(
    id: String = "article-1",
    title: String,
  ) = PodcastEpisodeArticle(
    articleId = id,
    feedId = "source-1",
    title = title,
    sourceTitle = "source",
    publishedAtEpochMillis = null,
    articleUrl = "https://example.invalid/$id",
    feedContent = "本文",
  )
}
