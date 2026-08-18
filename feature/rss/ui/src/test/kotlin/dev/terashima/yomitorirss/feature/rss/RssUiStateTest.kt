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
  fun `フィード追加中は更新件数より追加進捗を優先表示する`() {
    val state = FeedUiState(
      refreshing = true,
      refreshStatus = "3 / 10",
      addFeedProgress = "フィード情報を確認中…",
    )

    assertEquals("フィード情報を確認中…", state.refreshProgress)
  }
}
