package dev.terashima.yomitorirss.feature.x

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XViewerScreenTest {
  @Test
  fun `WebView 固有の UA トークンを除去する`() {
    val webViewUserAgent =
      "Mozilla/5.0 (Linux; Android 17; K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/140.0.0.0 Mobile Safari/537.36"

    assertEquals(
      "Mozilla/5.0 (Linux; Android 17; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36",
      webViewUserAgent.toBrowserCompatibleUserAgent(),
    )
  }

  @Test
  fun `通常のブラウザー UA は変更しない`() {
    val browserUserAgent =
      "Mozilla/5.0 (Linux; Android 17; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"

    assertEquals(browserUserAgent, browserUserAgent.toBrowserCompatibleUserAgent())
  }

  @Test
  fun `X ドメインのメインフレーム遷移は WebView 内に残す`() {
    assertFalse(shouldOpenXNavigationExternally("https://x.com/home", isForMainFrame = true))
    assertFalse(shouldOpenXNavigationExternally("https://mobile.x.com/home", isForMainFrame = true))
    assertFalse(shouldOpenXNavigationExternally("https://twitter.com/home", isForMainFrame = true))
    assertFalse(shouldOpenXNavigationExternally("https://mobile.twitter.com/home", isForMainFrame = true))
  }

  @Test
  fun `X 外のメインフレーム遷移は外部ブラウザーで開く`() {
    assertTrue(shouldOpenXNavigationExternally("https://example.com/article", isForMainFrame = true))
    assertTrue(shouldOpenXNavigationExternally("https://t.co/example", isForMainFrame = true))
  }

  @Test
  fun `X に似た外部ドメインも外部ブラウザーで開く`() {
    assertTrue(shouldOpenXNavigationExternally("https://x.com.example.com/", isForMainFrame = true))
    assertTrue(shouldOpenXNavigationExternally("https://twitter.com.example.com/", isForMainFrame = true))
  }

  @Test
  fun `サブフレームの外部 URL はブラウザー起動対象にしない`() {
    assertFalse(shouldOpenXNavigationExternally("https://example.com/embed", isForMainFrame = false))
  }

  @Test
  fun `HTTP 以外の URL はブラウザー起動対象にしない`() {
    assertFalse(shouldOpenXNavigationExternally("about:blank", isForMainFrame = true))
    assertFalse(shouldOpenXNavigationExternally("javascript:void(0)", isForMainFrame = true))
  }

  @Test
  fun `CSS が有効なら保存された CSS を注入する`() {
    val settings = XViewerCssSettings(enabled = true, css = "body { display: block; }")

    assertEquals("body { display: block; }", settings.cssForInjection())
  }

  @Test
  fun `CSS が無効なら空の CSS を注入する`() {
    val settings = XViewerCssSettings(enabled = false, css = "body { display: none; }")

    assertEquals("", settings.cssForInjection())
  }

  @Test
  fun `要素選択で生成した非表示ルールを既存 CSS の末尾へ追加する`() {
    assertEquals(
      "body { color: black; }\n\n" +
        "/* Added from X element picker */\n" +
        "[data-testid=\"GrokDrawer\"] {\n" +
        "  display: none !important;\n" +
        "}\n",
      appendHiddenElementRule(
        css = "body { color: black; }\n",
        selector = "[data-testid=\"GrokDrawer\"]",
      ),
    )
  }

  @Test
  fun `同じ非表示ルールは重複して追加しない`() {
    val css =
      "[data-testid=\"GrokDrawer\"] {\n" +
        "  display: none !important;\n" +
        "}\n"

    assertEquals(css, appendHiddenElementRule(css, "[data-testid=\"GrokDrawer\"]"))
  }

  @Test
  fun `空のセレクタでは CSS を変更しない`() {
    val css = "body { color: black; }"

    assertEquals(css, appendHiddenElementRule(css, "   "))
  }

  @Test
  fun `JavaScript の要素セレクタ結果を復号する`() {
    assertEquals(
      "[data-testid=\"sidebarColumn\"] > div:nth-of-type(2)",
      decodeElementPickerSelectorResult(
        "\"%5Bdata-testid%3D%22sidebarColumn%22%5D%20%3E%20div%3Anth-of-type(2)\"",
      ),
    )
    assertNull(decodeElementPickerSelectorResult("null"))
  }

  @Test
  fun `touch 開始時は親のジェスチャーインターセプトを禁止する`() {
    assertEquals(true, parentTouchInterceptionRequest(MotionEvent.ACTION_DOWN))
  }

  @Test
  fun `touch 終了時は親のジェスチャーインターセプト禁止を解除する`() {
    assertEquals(false, parentTouchInterceptionRequest(MotionEvent.ACTION_UP))
    assertEquals(false, parentTouchInterceptionRequest(MotionEvent.ACTION_CANCEL))
  }

  @Test
  fun `touch 継続中は親のジェスチャーインターセプト状態を変更しない`() {
    assertNull(parentTouchInterceptionRequest(MotionEvent.ACTION_MOVE))
  }
}
