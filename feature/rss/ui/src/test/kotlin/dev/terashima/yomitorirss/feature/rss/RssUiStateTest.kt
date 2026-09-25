package dev.terashima.yomitorirss.feature.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RssUiStateTest {
  @Test
  fun `初期状態には記事もメッセージもない`() {
    val state = RssUiState()

    assertFalse(state.initialized)
    assertTrue(state.unread.isEmpty())
    assertTrue(state.readLater.isEmpty())
    assertTrue(state.hiddenArticleIds.isEmpty())
    assertNull(state.message)
  }

  @Test
  fun `推薦スコアは数値として表示する`() {
    val annotation = recommendationAnnotation(
      RssRecommendationAssessment.Scored(
        score = 7,
        revision = 1L,
        assessedAt = 10L,
      ),
    )

    assertEquals("推薦スコア: 7", annotation)
  }

  @Test
  fun `タイトルだけで判断できない評価は数値にせず理由を表示する`() {
    val annotation = recommendationAnnotation(
      RssRecommendationAssessment.Unscored(
        reason = RssRecommendationUnscoredReason.INSUFFICIENT_INFORMATION,
        revision = 1L,
        assessedAt = 10L,
      ),
    )

    assertEquals("推薦: 未評価（タイトルだけでは判断できません）", annotation)
  }

  @Test
  fun `推論失敗は数値にせず失敗理由を表示する`() {
    val annotation = recommendationAnnotation(
      RssRecommendationAssessment.Unscored(
        reason = RssRecommendationUnscoredReason.INFERENCE_FAILED,
        revision = 1L,
        assessedAt = 10L,
      ),
    )

    assertEquals("推薦: 未評価（判定に失敗しました）", annotation)
  }

  @Test
  fun `推薦条件が有効で評価結果がない記事は評価待ちと表示する`() {
    val state = RssUiState(
      recommendationPolicy = RssRecommendationPolicy(manualCondition = "広告記事を低くする"),
    )

    assertEquals("推薦: 評価待ち", state.recommendationAnnotationFor("pending"))
  }

  @Test
  fun `推薦条件が無効なら評価結果がない記事に注記を表示しない`() {
    val state = RssUiState()

    assertNull(state.recommendationAnnotationFor("pending"))
  }

  @Test
  fun `フィード追加中は更新件数より追加進捗を優先表示する`() {
    val state = FeedUiState(
      refreshing = true,
      refreshStatus = "3 / 10",
      addFeedProgress = "フィード情報を確認中…",
    )

    assertEquals("フィード情報を確認中…", state.refreshProgress)
  }
}
