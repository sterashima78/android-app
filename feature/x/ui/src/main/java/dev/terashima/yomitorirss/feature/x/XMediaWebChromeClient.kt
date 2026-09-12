package dev.terashima.yomitorirss.feature.x

import android.view.View
import android.webkit.WebChromeClient

internal class XMediaWebChromeClient(
  private val onShowFullscreenView: (View, CustomViewCallback) -> Unit,
  private val onHideFullscreenView: () -> Unit,
) : WebChromeClient() {
  override fun onShowCustomView(view: View, callback: CustomViewCallback) {
    onShowFullscreenView(view, callback)
  }

  override fun onHideCustomView() {
    onHideFullscreenView()
  }
}
