package dev.terashima.yomitorirss.core.network

import org.junit.Assert.assertSame
import org.junit.Test

class HttpClientTest {
  @Test
  fun `create returns the process wide transport`() {
    assertSame(HttpClient.create(), HttpClient.create())
  }
}
