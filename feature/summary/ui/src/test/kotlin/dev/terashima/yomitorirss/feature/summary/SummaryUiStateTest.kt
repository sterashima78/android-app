package dev.terashima.yomitorirss.feature.summary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SummaryUiStateTest {
  @Test
  fun `初期状態は要約対象も結果も持たない`() {
    val state = SummaryUiState()

    assertNull(state.article)
    assertNull(state.text)
    assertFalse(state.loading)
    assertNull(state.message)
    assertNull(state.review.articleId)
    assertNull(state.review.text)
    assertFalse(state.review.loading)
    assertNull(state.review.error)
  }
}
