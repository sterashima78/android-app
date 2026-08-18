package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmazonWebLibraryImportDialogTest {
  @Test
  fun `Kindle collector は Web message bridge へ正規形式を返す`() {
    assertTrue(KINDLE_WEBVIEW_COLLECT_SCRIPT.contains("window.YomitoriLibraryBridge"))
    assertTrue(KINDLE_WEBVIEW_COLLECT_SCRIPT.contains("format:'kindle-library-export'"))
    assertTrue(KINDLE_WEBVIEW_COLLECT_SCRIPT.contains("result-chunk"))
  }

  @Test
  fun `Personal Document collector は認証情報を JSON へ含めない`() {
    assertTrue(KINDLE_PERSONAL_DOCUMENT_WEBVIEW_COLLECT_SCRIPT.contains("window.YomitoriLibraryBridge"))
    assertTrue(
      KINDLE_PERSONAL_DOCUMENT_WEBVIEW_COLLECT_SCRIPT.contains(
        "format:'kindle-personal-library-export'",
      ),
    )
    assertTrue(KINDLE_PERSONAL_DOCUMENT_WEBVIEW_COLLECT_SCRIPT.contains("csrfToken:token"))
    assertFalse(KINDLE_PERSONAL_DOCUMENT_WEBVIEW_COLLECT_SCRIPT.contains("CookieManager"))
    assertFalse(KINDLE_PERSONAL_DOCUMENT_WEBVIEW_COLLECT_SCRIPT.contains("csrfToken:token," + "books"))
  }

  @Test
  fun `Audible collector は catalog への二段階取得を維持する`() {
    assertTrue(AUDIBLE_WEBVIEW_COLLECT_SCRIPT.contains("https://api.audible.co.jp/1.0/catalog/products"))
    assertTrue(AUDIBLE_WEBVIEW_EXPORT_SCRIPT.contains("window.YomitoriLibraryBridge"))
    assertTrue(AUDIBLE_WEBVIEW_EXPORT_SCRIPT.contains("format:'audible-library-export'"))
    assertTrue(AUDIBLE_WEBVIEW_EXPORT_SCRIPT.contains("result-chunk"))
  }
}
