package dev.terashima.yomitorirss

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class MainActivityComposeE2ETest {
  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `ドロワーからRSSへ移動してフィード管理を開ける`() {
    composeRule.onNodeWithText("統合ビュー").assertIsDisplayed()

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
}
