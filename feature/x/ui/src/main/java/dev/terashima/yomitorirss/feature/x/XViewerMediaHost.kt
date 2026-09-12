package dev.terashima.yomitorirss.feature.x

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
  val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  var fullscreenView by remember { mutableStateOf<View?>(null) }
  var fullscreenCallback by remember {
    mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
  }
  var boundWebView by remember { mutableStateOf<WebView?>(null) }
  var passBackToParent by remember { mutableStateOf(false) }

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
        // Keep the hosted WebView on a hardware-backed layer explicitly. The app
        // is hardware accelerated already, but inline video uses a distinct
        // compositing path that can fail when hosted through AndroidView.
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.webChromeClient = chromeClient
        boundWebView = webView
        return@LaunchedEffect
      }
    }
  }

  BackHandler(enabled = boundWebView != null && !passBackToParent) {
    when {
      fullscreenView != null -> hideFullscreenMedia()
      boundWebView?.canGoBack() == true -> boundWebView?.goBack()
      else -> passBackToParent = true
    }
  }

  LaunchedEffect(passBackToParent, backDispatcher) {
    if (!passBackToParent) return@LaunchedEffect

    // Let Compose disable this inner handler before forwarding the same Back
    // action so the application-level handler can apply its normal behavior.
    withFrameNanos { }
    backDispatcher?.onBackPressed()
    passBackToParent = false
  }

  // Keep cleanup tied to the host lifecycle. Keying this effect by boundWebView
  // disposed the previous effect immediately after binding and reset the exact
  // WebChromeClient that had just been installed.
  DisposableEffect(Unit) {
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
