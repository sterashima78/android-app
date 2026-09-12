package dev.terashima.yomitorirss.feature.x

import android.view.View
import android.webkit.WebChromeClient
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class XMediaWebChromeClientTest {
  @Test
  fun `全画面メディア表示要求を表示側へ委譲する`() {
    val view = View(RuntimeEnvironment.getApplication())
    val callback = WebChromeClient.CustomViewCallback { }
    var receivedView: View? = null
    var receivedCallback: WebChromeClient.CustomViewCallback? = null
    val client = XMediaWebChromeClient(
      onShowFullscreenView = { shownView, shownCallback ->
        receivedView = shownView
        receivedCallback = shownCallback
      },
      onHideFullscreenView = {},
    )

    client.onShowCustomView(view, callback)

    assertSame(view, receivedView)
    assertSame(callback, receivedCallback)
  }

  @Test
  fun `全画面メディア終了要求を表示側へ委譲する`() {
    var hidden = false
    val client = XMediaWebChromeClient(
      onShowFullscreenView = { _, _ -> },
      onHideFullscreenView = { hidden = true },
    )

    client.onHideCustomView()

    assertTrue(hidden)
  }
}
