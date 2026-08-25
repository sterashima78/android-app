package dev.terashima.yomitorirss.feature.web.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LanWebServerTest {
  @Test
  fun `HTMLで特別な意味を持つ文字をエスケープする`() {
    assertEquals(
      "&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&#39;s&lt;/a&gt;",
      escapeHtml("<a href=\"x\">Tom & Jerry's</a>"),
    )
  }
}
