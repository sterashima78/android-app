package dev.terashima.yomitorirss.feature.x

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView

@Composable
internal fun XViewerMediaHost(
  repository: XViewerCssRepository,
  onFullscreenChanged: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  val rootView = LocalView.current.rootView
  var fullscreenView by remember { mutableStateOf<View?>(null) }
  var fullscreenCallback by remember {
    mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
  }
  var boundWebView by remember { mutableStateOf<WebView?>(null) }

  fun hideFullscreenMedia() {
    val callback = fullscreenCallback
    fullscreenView = null
    fullscreenCallback = null
    onFullscreenChanged(false)
    callback?.onCustomViewHidden()
  }

  val chromeClient = remember {
    XMediaWebChromeClient(
      onShowFullscreenView = { view, callback ->
        if (fullscreenView != null) {
          callback.onCustomViewHidden()
        } else {
          fullscreenView = view
          fullscreenCallback = callback
          onFullscreenChanged(true)
        }
      },
      onHideFullscreenView = ::hideFullscreenMedia,
    )
  }

  LaunchedEffect(rootView, chromeClient) {
    repeat(60) {
      withFrameNanos { }
      val webView = rootView.findDescendantWebView()
      if (webView != null) {
        webView.webChromeClient = chromeClient
        boundWebView = webView
        return@LaunchedEffect
      }
    }
  }

  DisposableEffect(boundWebView) {
    onDispose {
      boundWebView?.webChromeClient = WebChromeClient()
      fullscreenCallback?.onCustomViewHidden()
      fullscreenCallback = null
      fullscreenView = null
      onFullscreenChanged(false)
    }
  }

  Box(modifier = modifier) {
    XViewerScreen(
      repository = repository,
      modifier = Modifier.fillMaxSize(),
    )

    fullscreenView?.let { mediaView ->
      AndroidView(
        factory = {
          (mediaView.parent as? ViewGroup)?.removeView(mediaView)
          mediaView
        },
        modifier = Modifier.fillMaxSize(),
      )
    }
  }
}

private fun View.findDescendantWebView(): WebView? {
  if (this is WebView) return this
  if (this !is ViewGroup) return null
  for (index in 0 until childCount) {
    getChildAt(index).findDescendantWebView()?.let { return it }
  }
  return null
}
