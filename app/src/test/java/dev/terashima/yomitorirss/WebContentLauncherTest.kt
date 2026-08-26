package dev.terashima.yomitorirss

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebContentLauncherTest {
  @Test
  fun `https URL は package を固定しない Custom Tab request にする`() {
    val request = requireNotNull(buildWebCustomTabRequest("https://example.com/article"))

    assertEquals(Intent.ACTION_VIEW, request.customTabsIntent.intent.action)
    assertEquals("https://example.com/article", request.uri.toString())
    assertNull(request.customTabsIntent.intent.`package`)
  }

  @Test
  fun `http URL も Custom Tab request にできる`() {
    assertNotNull(buildWebCustomTabRequest("http://example.com/article"))
  }

  @Test
  fun `Web 以外の scheme は Custom Tab request にしない`() {
    assertNull(buildWebCustomTabRequest("mailto:test@example.com"))
    assertNull(buildWebCustomTabRequest("kindle://book/?action=open&asin=B000000000"))
    assertNull(buildWebCustomTabRequest(""))
  }
}
