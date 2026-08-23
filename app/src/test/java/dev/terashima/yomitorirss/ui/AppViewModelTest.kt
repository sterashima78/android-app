package dev.terashima.yomitorirss.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppViewModelTest {
  @Test
  fun `初期表示は統合ビューになる`() {
    val viewModel = AppViewModel()

    assertEquals(MainTab.INTEGRATED, viewModel.state.value.selectedTab)
  }

  @Test
  fun `タブを選択すると選択状態が更新される`() {
    val viewModel = AppViewModel()

    viewModel.selectTab(MainTab.SAVED)

    assertEquals(MainTab.SAVED, viewModel.state.value.selectedTab)
  }

  @Test
  fun `メッセージを閉じると状態から削除される`() {
    val viewModel = AppViewModel()

    viewModel.showMessage("test")
    viewModel.dismissMessage()

    assertNull(viewModel.state.value.message)
  }
}
