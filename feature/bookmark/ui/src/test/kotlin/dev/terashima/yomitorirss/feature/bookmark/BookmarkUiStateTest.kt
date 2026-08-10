package dev.terashima.yomitorirss.feature.bookmark

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkUiStateTest {
  @Test
  fun `初期状態は未選択かつ空の一覧になる`() {
    val state = BookmarkUiState()

    assertFalse(state.initialized)
    assertTrue(state.saved.isEmpty())
    assertTrue(state.history.isEmpty())
    assertTrue(state.folders.isEmpty())
    assertTrue(state.tags.isEmpty())
    assertNull(state.selectedFolderId)
    assertNull(state.selectedTagId)
    assertFalse(state.importCompleted)
  }
}
