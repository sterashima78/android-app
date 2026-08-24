package dev.terashima.yomitorirss.core.network

import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

class HttpClientTest {
  @Test
  fun `create reuses the wrapper for the same User-Agent`() {
    assertSame(HttpClient.create(), HttpClient.create())
    assertSame(
      HttpClient.create("Mosaic/1.2.3 (Android)"),
      HttpClient.create("Mosaic/1.2.3 (Android)"),
    )
  }

  @Test
  fun `different User-Agent uses a different wrapper`() {
    assertNotSame(
      HttpClient.create("Mosaic/1.2.3 (Android)"),
      HttpClient.create("Mosaic/9.9.9 (Android)"),
    )
  }
}
