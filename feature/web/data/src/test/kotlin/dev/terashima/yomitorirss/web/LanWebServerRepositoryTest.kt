package dev.terashima.yomitorirss.feature.web.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LanWebServerRepositoryTest {
  @Test
  fun `HTML escapeの既存仕様を維持する`() {
    assertEquals("&lt;&amp;&gt;&quot;&#39;", escapeHtml("<&>\"'"))
  }
}
