package dev.terashima.yomitorirss.feature.x

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView

@Composable
internal fun XViewerOutlineClipDiagnosticButton(
  modifier: Modifier = Modifier,
) {
  val rootView = LocalView.current.rootView

  TextButton(
    modifier = modifier,
    onClick = {
      rootView.findWebViewForOutlineClipDiagnostic()?.let { webView ->
        webView.clipToOutline = false
        webView.invalidate()
      }
    },
  ) {
    Text("クリップ解除")
  }
}

private fun View.findWebViewForOutlineClipDiagnostic(): WebView? {
  if (this is WebView) return this
  if (this !is ViewGroup) return null
  for (index in 0 until childCount) {
    getChildAt(index).findWebViewForOutlineClipDiagnostic()?.let { return it }
  }
  return null
}
