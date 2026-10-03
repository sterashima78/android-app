package dev.terashima.yomitorirss.feature.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastNewsClusteringTest {
  @Test
  fun `同じchapterPositionの記事は1つの再生チャプターへまとまる`() {
    val first = article("a1", chapterPosition = 0, script = "[[TITLE:統合ニュース]]\n統合した本文です。")
    val second = article("a2", chapterPosition = 0, script = "[[TITLE:統合ニュース]]\n統合した本文です。")
    val third = article("a3", chapterPosition = 1, script = "[[TITLE:別ニュース]]\n別の本文です。")
    val episode = PodcastEpisode(
      id = "episode-1",
      programId = "program-1",
      title = "ニュース",
      createdAtEpochMillis = 100L,
      status = PodcastEpisodeStatus.READY,
      articles = listOf(first, second, third),
      script = """
        [[CHAPTER:1]]
        [[TITLE:統合ニュース]]
        統合した本文です。
        [[CHAPTER:2]]
        [[TITLE:別ニュース]]
        別の本文です。
      """.trimIndent(),
    )

    val chapters = episode.playbackChapters()

    assertEquals(2, chapters.size)
    assertEquals(listOf("a1", "a2"), chapters[0].articles.map { it.articleId })
    assertEquals(listOf("a3"), chapters[1].articles.map { it.articleId })
    assertEquals("統合ニュース", chapters[0].article?.title)
  }

  @Test
  fun `ニュース全体で出現が少ないフィードカテゴリを代表カテゴリにする`() {
    val news = listOf(
      listOf(feedEntry("a1", listOf("World", "World/Diplomacy", "Diplomacy"))),
      listOf(feedEntry("a2", listOf("World", "World/Middle East", "Middle East"))),
      listOf(feedEntry("a3", listOf("World", "World/Middle East", "Middle East"))),
    )

    val categories = selectPodcastFeedCategories(news)

    assertEquals(
      listOf("World/Diplomacy", "World/Middle East", "World/Middle East"),
      categories,
    )
  }

  @Test
  fun `AI補完カテゴリも全ニュースの出現数へ含めて代表カテゴリを選ぶ`() {
    val news = listOf(
      listOf(feedEntry("a1", listOf("A", "B"))),
      listOf(feedEntry("a2", listOf("B"))),
      listOf(feedEntry("a3")),
      listOf(feedEntry("a4")),
    )

    val categories = selectPodcastFeedCategories(
      news = news,
      supplementalCategories = mapOf(2 to "A", 3 to "A"),
    )

    assertEquals(listOf("B", "B", "A", "A"), categories)
  }

  @Test
  fun `同じニュース内の複数記事はカテゴリ出現数を1ニュースとして数える`() {
    val news = listOf(
      listOf(
        feedEntry("a1", listOf("World", "World/Diplomacy")),
        feedEntry("a2", listOf("World", "World/Diplomacy")),
      ),
      listOf(feedEntry("a3", listOf("World"))),
    )

    val categories = selectPodcastFeedCategories(news)

    assertEquals(listOf("World/Diplomacy", "World"), categories)
  }

  @Test
  fun `代表カテゴリが同じニュースを連続する順序へまとめる`() {
    val first = listOf(feedEntry("a1"))
    val second = listOf(feedEntry("a2"))
    val third = listOf(feedEntry("a3"))

    val ordered = orderPodcastNewsByCategory(
      news = listOf(first, second, third),
      categories = listOf("Diplomacy", "Middle East", "Diplomacy"),
    )

    assertEquals(listOf("a1", "a3", "a2"), ordered.map { it.single().articleId })
  }

  @Test
  fun `複数記事の原稿promptは重複を統合する指示と全記事本文を含む`() {
    val prompt = buildPodcastChapterPrompt(
      programName = "朝のニュース",
      articles = listOf(article("a1", 0), article("a2", 0)),
      chapterNumber = 1,
      totalChapters = 1,
    )

    assertTrue(prompt.contains("重複内容を繰り返さず1つ"))
    assertTrue(prompt.contains("本文 a1"))
    assertTrue(prompt.contains("本文 a2"))
  }

  private fun feedEntry(id: String, categories: List<String> = emptyList()) = PodcastFeedEntry(
    articleId = id,
    feedId = "source-$id",
    title = "記事 $id",
    sourceTitle = "情報源 $id",
    publishedAtEpochMillis = 100L,
    articleUrl = "https://example.invalid/$id",
    feedContent = "本文 $id",
    categories = categories,
  )

  private fun article(id: String, chapterPosition: Int, script: String? = null) = PodcastEpisodeArticle(
    articleId = id,
    feedId = "source-$id",
    title = "記事 $id",
    sourceTitle = "情報源 $id",
    publishedAtEpochMillis = 100L,
    articleUrl = "https://example.invalid/$id",
    feedContent = "本文 $id",
    chapterPosition = chapterPosition,
    chapterStatus = if (script == null) PodcastChapterGenerationStatus.PENDING else PodcastChapterGenerationStatus.READY,
    chapterScript = script,
  )
}
