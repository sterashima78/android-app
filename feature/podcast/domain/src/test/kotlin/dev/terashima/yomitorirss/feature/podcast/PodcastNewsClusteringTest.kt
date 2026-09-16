package dev.terashima.yomitorirss.feature.podcast

import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
  fun `分類結果は全記事を1回ずつ含む場合だけ受理する`() {
    assertEquals(
      listOf(listOf(0, 2), listOf(1)),
      parsePodcastClusters("GROUP: 1,3\nGROUP: 2", 3),
    )

    assertThrows(IllegalArgumentException::class.java) {
      parsePodcastClusters("GROUP: 1,2\nGROUP: 2", 3)
    }
    assertThrows(IllegalArgumentException::class.java) {
      parsePodcastClusters("説明です\nGROUP: 1,2,3", 3)
    }
  }

  @Test
  fun `AI分類失敗時は1記事1ニュースへフォールバックする`() = runSuspendForClustering {
    val clusterer = AiPodcastNewsClusterer(
      scriptGenerator = object : PodcastScriptGenerator {
        override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String = "invalid"
      },
    )

    assertEquals(
      listOf(listOf(0), listOf(1)),
      clusterer.cluster(PodcastGenerationProvider.LOCAL, listOf(feedEntry("a1"), feedEntry("a2"))),
    )
  }

  @Test
  fun `AI分類のキャンセルはフォールバックせず伝播する`() {
    val clusterer = AiPodcastNewsClusterer(
      scriptGenerator = object : PodcastScriptGenerator {
        override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String {
          throw CancellationException("cancelled")
        }
      },
    )

    assertThrows(CancellationException::class.java) {
      runSuspendForClustering {
        clusterer.cluster(PodcastGenerationProvider.LOCAL, listOf(feedEntry("a1"), feedEntry("a2")))
      }
    }
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

  private fun feedEntry(id: String) = PodcastFeedEntry(
    articleId = id,
    feedId = "source-$id",
    title = "記事 $id",
    sourceTitle = "情報源 $id",
    publishedAtEpochMillis = 100L,
    articleUrl = "https://example.invalid/$id",
    feedContent = "本文 $id",
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

private fun <T> runSuspendForClustering(block: suspend () -> T): T {
  var value: Result<T>? = null
  block.startCoroutine(
    object : Continuation<T> {
      override val context = EmptyCoroutineContext
      override fun resumeWith(result: Result<T>) {
        value = result
      }
    },
  )
  return requireNotNull(value) { "suspend block did not complete synchronously" }.getOrThrow()
}
