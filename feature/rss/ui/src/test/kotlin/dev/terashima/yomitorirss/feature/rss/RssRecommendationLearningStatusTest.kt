package dev.terashima.yomitorirss.feature.rss

import org.junit.Assert.assertTrue
import org.junit.Test

class RssRecommendationLearningStatusTest {
  @Test
  fun `未処理feedbackがある場合は件数と再試行を表示する`() {
    val text = recommendationFeedbackStatusText(2)

    assertTrue(text.contains("2件"))
    assertTrue(text.contains("再試行"))
  }

  @Test
  fun `未処理feedbackがない場合は処理済みの意味を説明する`() {
    val text = recommendationFeedbackStatusText(0)

    assertTrue(text.contains("未処理の除外参考はありません"))
    assertTrue(text.contains("カバー済み"))
    assertTrue(text.contains("処理済み"))
  }
}
