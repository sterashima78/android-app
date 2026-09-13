package dev.terashima.yomitorirss.feature.x

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView

internal class XMediaWebChromeClient(
  private val onShowFullscreenView: (View, CustomViewCallback) -> Unit,
  private val onHideFullscreenView: () -> Unit,
  private val onPageReady: (WebView) -> Unit,
) : WebChromeClient() {
  override fun onShowCustomView(view: View, callback: CustomViewCallback) {
    onShowFullscreenView(view, callback)
  }

  override fun onHideCustomView() {
    onHideFullscreenView()
  }

  override fun onProgressChanged(view: WebView, newProgress: Int) {
    super.onProgressChanged(view, newProgress)
    if (newProgress == 100) onPageReady(view)
  }
}
