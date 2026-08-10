package dev.terashima.yomitorirss.feature.rss

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
}
