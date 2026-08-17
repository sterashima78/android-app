package dev.terashima.yomitorirss

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityComposeE2ETest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `ドロワーからRSSへ移動してフィード管理を開ける`() {
    composeRule.onNodeWithContentDescription("メニュー").assertIsDisplayed()

    composeRule.onNodeWithContentDescription("メニュー").performClick()
    composeRule.onNodeWithText("RSS").performClick()

    composeRule.onNodeWithContentDescription("未読").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("あとで読む").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("フィード管理").performClick()

    composeRule.onNodeWithContentDescription("フィードを追加").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("OPMLからインポート").assertIsDisplayed()
  }

  @Test
  fun `ドロワーからブックマークへ移動して下部タブを表示できる`() {
    composeRule.onNodeWithContentDescription("メニュー").performClick()
    composeRule.onNodeWithText("ブックマーク").performClick()

    composeRule.onNodeWithContentDescription("ブックマーク").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("フォルダ").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("タグ").assertIsDisplayed()
    composeRule.onNodeWithContentDescription("履歴").assertIsDisplayed()
  }

  @Test
  fun `戻る操作でドロワーを表示できる`() {
    pressBack()

    composeRule.onNodeWithText("Yomitori").assertIsDisplayed()
    composeRule.onNodeWithText("RSS").assertIsDisplayed()
  }

  @Test
  fun `ドロワー表示中の戻る操作でActivityを終了する`() {
    pressBack()
    composeRule.onNodeWithText("Yomitori").assertIsDisplayed()

    composeRule.runOnUiThread {
      composeRule.activity.onBackPressedDispatcher.onBackPressed()
    }

    assertTrue(composeRule.activity.isFinishing)
  }
}
