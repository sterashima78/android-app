package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmazonWebLibraryImportDialogTest {
  @Test
  fun `Amazon と Audible の HTTPS ナビゲーションだけを WebView 内で許可する`() {
    assertTrue(isTrustedAmazonImportNavigation("https://www.amazon.co.jp/ap/signin"))
    assertTrue(isTrustedAmazonImportNavigation("https://read.amazon.co.jp/kindle-library"))
    assertTrue(isTrustedAmazonImportNavigation("https://www.audible.co.jp/library/titles"))
    assertTrue(isTrustedAmazonImportNavigation("https://api.audible.co.jp/1.0/catalog/products"))
    assertFalse(isTrustedAmazonImportNavigation("http://www.amazon.co.jp/ap/signin"))
    assertFalse(isTrustedAmazonImportNavigation("https://amazon.co.jp.evil.example/"))
    assertFalse(isTrustedAmazonImportNavigation("https://example.com/"))
  }

  @Test
  fun `収集スクリプトを実行できるページを厳密に判定する`() {
    assertTrue(isKindleWebLibraryPage("https://read.amazon.co.jp/kindle-library?resourceType=COMICS"))
    assertFalse(isKindleWebLibraryPage("https://www.amazon.co.jp/kindle-library"))
    assertTrue(isAudibleLibraryPage("https://www.audible.co.jp/library/titles?page=2"))
    assertFalse(isAudibleLibraryPage("https://api.audible.co.jp/library/titles"))
    assertTrue(isAudibleCatalogApiPage("https://api.audible.co.jp/1.0/catalog/products?asins=ABCDEFGHIJ"))
    assertFalse(isAudibleCatalogApiPage("https://www.audible.co.jp/1.0/catalog/products"))
  }

  @Test
  fun `分割された JSON を順番に復元する`() {
    val accumulator = ImportChunkAccumulator(maxBytes = 1024)
    val json = "{\"title\":\"日本語\"}"
    val first = json.substring(0, 8)
    val second = json.substring(8)

    accumulator.start("session", 2, json.toByteArray(Charsets.UTF_8).size)
    accumulator.add("session", 0, 2, first)
    accumulator.add("session", 1, 2, second)

    assertEquals(json, accumulator.finish("session"))
  }

  @Test(expected = IllegalArgumentException::class)
  fun `宣言サイズを超えるデータは拒否する`() {
    val accumulator = ImportChunkAccumulator(maxBytes = 4)
    accumulator.start("session", 1, 5)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `別セッションのチャンクは拒否する`() {
    val accumulator = ImportChunkAccumulator(maxBytes = 1024)
    accumulator.start("session-a", 1, 2)
    accumulator.add("session-b", 0, 1, "{}")
  }
}
