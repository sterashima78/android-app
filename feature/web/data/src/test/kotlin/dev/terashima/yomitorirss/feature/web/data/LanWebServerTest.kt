package dev.terashima.yomitorirss.feature.web.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.net.URI

class LanWebServerTest {
  @Test
  fun `HTMLで特別な意味を持つ文字をエスケープする`() {
    assertEquals(
      "&lt;a href=&quot;x&quot;&gt;Tom &amp; Jerry&#39;s&lt;/a&gt;",
      escapeHtml("<a href=\"x\">Tom & Jerry's</a>"),
    )
  }

  @Test
  fun `bootstrap tokenは一度だけ使用でき認証後はsession tokenを使う`() {
    val authentication = LanWebAuthentication("bootstrap") { "session" }

    assertEquals(AuthenticationResult.Bootstrapped, authentication.authenticate("bootstrap", null))
    assertEquals(AuthenticationResult.Rejected, authentication.authenticate("bootstrap", null))
    assertEquals(AuthenticationResult.Rejected, authentication.authenticate("bootstrap", "session"))
    assertEquals(AuthenticationResult.Authenticated, authentication.authenticate(null, "session"))
  }

  @Test
  fun `停止するとbootstrap tokenとsession tokenを失効する`() {
    val authentication = LanWebAuthentication("bootstrap") { "session" }
    authentication.authenticate("bootstrap", null)
    authentication.invalidate()

    assertEquals(AuthenticationResult.Rejected, authentication.authenticate("bootstrap", null))
    assertEquals(AuthenticationResult.Rejected, authentication.authenticate(null, "session"))
  }

  @Test
  fun `再起動時の旧tokenを拒否する`() {
    val first = LanWebAuthentication("first") { "first-session" }
    first.authenticate("first", null)
    first.invalidate()
    val restarted = LanWebAuthentication("second") { "second-session" }

    assertEquals(AuthenticationResult.Rejected, restarted.authenticate("first", null))
    assertEquals(AuthenticationResult.Rejected, restarted.authenticate(null, "first-session"))
    assertNotEquals("first-session", restarted.apply { authenticate("second", null) }.sessionToken())
  }

  @Test
  fun `認証後URLからtokenだけを除去する`() {
    assertEquals("/?view=unread", URI("/?token=secret&view=unread").withoutBootstrapToken())
    assertEquals("/index.html", URI("/index.html?token=secret").withoutBootstrapToken())
  }
}
