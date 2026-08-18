package dev.terashima.yomitorirss.core.webcollector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureWebCollectorDialogTest {
  @Test
  fun `許可した HTTPS ホストとサブドメインだけを WebView 内で開く`() {
    val hosts = setOf("amazon.co.jp", "audible.co.jp")

    assertTrue(isAllowedNavigation("https://www.amazon.co.jp/ap/signin", hosts))
    assertTrue(isAllowedNavigation("https://read.amazon.co.jp/kindle-library", hosts))
    assertTrue(isAllowedNavigation("https://api.audible.co.jp/1.0/catalog/products", hosts))
    assertFalse(isAllowedNavigation("http://www.amazon.co.jp/ap/signin", hosts))
    assertFalse(isAllowedNavigation("https://amazon.co.jp.evil.example/", hosts))
    assertFalse(isAllowedNavigation("https://example.com/", hosts))
    assertFalse(isAllowedNavigation("https://www.amazon.co.jp:444/ap/signin", hosts))
  }

  @Test
  fun `Web message origin は明示した HTTPS origin だけを許可する`() {
    val origins = setOf("https://www.amazon.co.jp", "https://api.audible.co.jp")

    assertTrue(isAllowedBridgeOrigin("https://www.amazon.co.jp", origins))
    assertTrue(isAllowedBridgeOrigin("https://api.audible.co.jp/", origins))
    assertFalse(isAllowedBridgeOrigin("http://www.amazon.co.jp", origins))
    assertFalse(isAllowedBridgeOrigin("https://read.amazon.co.jp", origins))
    assertFalse(isAllowedBridgeOrigin("https://www.amazon.co.jp.evil.example", origins))
  }

  @Test
  fun `分割された JSON を順番に復元する`() {
    val accumulator = WebCollectorChunkAccumulator(maxBytes = 1024, maxChunks = 8)
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
    WebCollectorChunkAccumulator(maxBytes = 4, maxChunks = 8).start("session", 1, 5)
  }

  @Test(expected = IllegalArgumentException::class)
  fun `別セッションのチャンクは拒否する`() {
    val accumulator = WebCollectorChunkAccumulator(maxBytes = 1024, maxChunks = 8)
    accumulator.start("session-a", 1, 2)
    accumulator.add("session-b", 0, 1, "{}")
  }
}
