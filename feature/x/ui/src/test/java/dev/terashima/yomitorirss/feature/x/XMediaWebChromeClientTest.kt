package dev.terashima.yomitorirss.feature.x

import android.view.View
import android.webkit.WebChromeClient
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.mockito.kotlin.mock

class XMediaWebChromeClientTest {
  @Test
  fun `全画面表示要求を表示側へ委譲する`() {
    val view = mock<View>()
    val callback = mock<WebChromeClient.CustomViewCallback>()
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
  fun `全画面終了要求を表示側へ委譲する`() {
    var hidden = false
    val client = XMediaWebChromeClient(
      onShowFullscreenView = { _, _ -> },
      onHideFullscreenView = { hidden = true },
    )

    client.onHideCustomView()

    assertTrue(hidden)
  }
}
