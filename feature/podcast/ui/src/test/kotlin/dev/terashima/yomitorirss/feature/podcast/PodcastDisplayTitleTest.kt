package dev.terashima.yomitorirss.feature.podcast

import org.junit.Assert.assertEquals
import org.junit.Test

class PodcastDisplayTitleTest {
  @Test
  fun `ニュース間の接続語はタイトル表示から除外する`() {
    assertEquals("次のニュース", podcastDisplayTitle("続いて。次のニュース"))
  }

  @Test
  fun `通常のタイトルはそのまま表示する`() {
    assertEquals("最初のニュース", podcastDisplayTitle("最初のニュース"))
  }

  @Test
  fun `分類成功時は記事数とニュース数を表示する`() {
    val episode = episode(
      status = PodcastClusteringStatus.SUCCESS,
      chapterPositions = listOf(0, 0, 1),
    )

    assertEquals("ニュース分類: 成功 ・ 3記事 → 2ニュース", podcastClusteringSummary(episode))
  }

  @Test
  fun `分類処理失敗時はフォールバック理由を表示する`() {
    val episode = episode(
      status = PodcastClusteringStatus.FALLBACK_INFERENCE_ERROR,
      chapterPositions = listOf(0, 1),
    )

    assertEquals(
      "ニュース分類: フォールバック（分類処理に失敗） ・ 2記事 → 2ニュース",
      podcastClusteringSummary(episode),
    )
  }

  @Test
  fun `旧エピソードは分類記録なしと表示する`() {
    val episode = episode(status = null, chapterPositions = listOf(0))

    assertEquals("ニュース分類: 記録なし ・ 1記事 → 1ニュース", podcastClusteringSummary(episode))
  }

  private fun episode(
    status: PodcastClusteringStatus?,
    chapterPositions: List<Int>,
  ): PodcastEpisode = PodcastEpisode(
    id = "episode",
    programId = "program",
    title = "ニュース",
    createdAtEpochMillis = 0L,
    status = PodcastEpisodeStatus.READY,
    articles = chapterPositions.mapIndexed { index, chapterPosition ->
      PodcastEpisodeArticle(
        articleId = "article-$index",
        feedId = "source-$index",
        title = "記事$index",
        sourceTitle = "ニュース",
        publishedAtEpochMillis = null,
        articleUrl = null,
        feedContent = "本文",
        chapterPosition = chapterPosition,
      )
    },
    clusteringStatus = status,
  )
}
