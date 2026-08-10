package dev.terashima.yomitorirss.feature.rss
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedModelsTest {
  @Test
  fun `フィード検査結果は未指定なら候補なしになる`() {
    val inspection = FeedInspection()

    assertNull(inspection.directFeedUrl)
    assertTrue(inspection.candidates.isEmpty())
  }
}
