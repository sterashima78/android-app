package dev.terashima.yomitorirss.feature.x

import android.view.MotionEvent
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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
  fun `nth-of-type を含む不安定なセレクタは保存しない`() {
    val css = "body { color: black; }"
    val selector = "article[data-testid=\"tweet\"] > div:nth-of-type(2)"

    assertFalse(isPersistableElementPickerSelector(selector))
    assertEquals(css, appendHiddenElementRule(css, selector))
  }

  @Test
  fun `href と semantic boundary を使うセレクタは保存できる`() {
    val selector =
      "article[data-testid=\"tweet\"]:has(a[href=\"/user/status/123\"]) [data-testid=\"caret\"]"

    assertTrue(isPersistableElementPickerSelector(selector))
  }

  @Test
  fun `JavaScript の要素セレクタ結果を復号する`() {
    assertEquals(
      "article[data-testid=\"tweet\"]:has(a[href=\"/user/status/123\"]) [data-testid=\"caret\"]",
      decodeElementPickerSelectorResult(
        "\"article%5Bdata-testid%3D%22tweet%22%5D%3Ahas%28a%5Bhref%3D%22%2Fuser%2Fstatus%2F123%22%5D%29%20%5Bdata-testid%3D%22caret%22%5D\"",
      ),
    )
    assertNull(decodeElementPickerSelectorResult("null"))
  }

  @Test
  fun `JavaScript から返された nth-of-type セレクタも拒否する`() {
    assertNull(
      decodeElementPickerSelectorResult(
        "\"%5Bdata-testid%3D%22sidebarColumn%22%5D%20%3E%20div%3Anth-of-type%282%29\"",
      ),
    )
  }

  @Test
  fun `X のリスト path を判定する`() {
    assertTrue(isXListPath("/i/lists/123456"))
    assertTrue(isXListPath("/i/lists/example"))
    assertFalse(isXListPath("/home"))
    assertFalse(isXListPath("/i/lists"))
  }

  @Test
  fun `リストタブ群表示ルールをJavaScript結果から復号する`() {
    val expected = XViewerDomRule(
      kind = XViewerDomRuleKind.KEEP_MATCHING_ITEMS,
      pagePath = "/home",
      containerSelector = "div[role=\"tablist\"]",
      itemSelector = "[role=\"tab\"]",
      targetKind = XViewerDomTargetKind.HREF_PATH_PREFIX,
      targetValue = "/i/lists/",
    )
    val json =
      "{\"kind\":\"KEEP_MATCHING_ITEMS\",\"pagePath\":\"/home\"," +
        "\"containerSelector\":\"div[role=\\\"tablist\\\"]\"," +
        "\"itemSelector\":\"[role=\\\"tab\\\"]\"," +
        "\"targetKind\":\"HREF_PATH_PREFIX\",\"targetValue\":\"/i/lists/\"}"
    val encoded = URLEncoder.encode(json, StandardCharsets.UTF_8.toString())

    assertEquals(expected, decodeElementPickerDomRuleResult("\"$encoded\""))
  }

  @Test
  fun `不正なリストタブ群表示ルールは無視する`() {
    assertNull(decodeElementPickerDomRuleResult("\"not-json\""))
  }

  @Test
  fun `DOM表示ルールを安全なJSONに変換する`() {
    val rule = XViewerDomRule(
      kind = XViewerDomRuleKind.KEEP_MATCHING_ITEMS,
      pagePath = "/home",
      containerSelector = "[role=\"tablist\"]",
      itemSelector = "[role=\"tab\"]",
      targetKind = XViewerDomTargetKind.HREF_PATH_PREFIX,
      targetValue = "/i/lists/",
    )

    val json = JSONArray(domRulesJson(listOf(rule))).getJSONObject(0)

    assertEquals("/i/lists/", json.getString("targetValue"))
    assertEquals("HREF_PATH_PREFIX", json.getString("targetKind"))
    assertEquals("/home", json.getString("pagePath"))
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
