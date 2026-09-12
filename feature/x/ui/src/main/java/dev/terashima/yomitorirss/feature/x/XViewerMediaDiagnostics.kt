package dev.terashima.yomitorirss.feature.x

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun XViewerMediaDiagnosticsButton(
  modifier: Modifier = Modifier,
) {
  val rootView = LocalView.current.rootView
  var diagnostics by remember { mutableStateOf<String?>(null) }

  TextButton(
    modifier = modifier,
    onClick = {
      val webView = rootView.findHostedWebView()
      if (webView == null) {
        diagnostics = "WebView not found"
      } else {
        webView.evaluateJavascript(MEDIA_DIAGNOSTICS_SCRIPT) { result ->
          diagnostics = prettyMediaDiagnostics(result)
        }
      }
    },
  ) {
    Text("動画診断")
  }

  diagnostics?.let { text ->
    AlertDialog(
      onDismissRequest = { diagnostics = null },
      confirmButton = {
        TextButton(onClick = { diagnostics = null }) {
          Text("閉じる")
        }
      },
      title = { Text("動画診断") },
      text = {
        SelectionContainer {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(
              text = text,
              modifier = Modifier.padding(vertical = 4.dp),
            )
          }
        }
      },
    )
  }
}

private fun View.findHostedWebView(): WebView? {
  if (this is WebView) return this
  if (this !is ViewGroup) return null
  for (index in 0 until childCount) {
    getChildAt(index).findHostedWebView()?.let { return it }
  }
  return null
}

internal fun prettyMediaDiagnostics(result: String?): String {
  if (result.isNullOrBlank() || result == "null" || result == "undefined") {
    return "No diagnostic result"
  }
  return runCatching { JSONObject(result).toString(2) }.getOrElse { result }
}

private val MEDIA_DIAGNOSTICS_SCRIPT =
  """
    (() => {
      const describeSource = (src) => {
        if (!src) return null;
        try {
          const parsed = new URL(src, document.baseURI);
          return {
            scheme: parsed.protocol,
            host: parsed.host || null,
            hasQuery: Boolean(parsed.search),
          };
        } catch (_) {
          return { scheme: 'unparsed', host: null, hasQuery: false };
        }
      };

      const videos = Array.from(document.querySelectorAll('video'))
        .slice(0, 6)
        .map((video, index) => {
          const rect = video.getBoundingClientRect();
          const style = getComputedStyle(video);
          return {
            index,
            readyState: video.readyState,
            networkState: video.networkState,
            error: video.error ? {
              code: video.error.code,
              message: video.error.message || null,
            } : null,
            paused: video.paused,
            muted: video.muted,
            volume: video.volume,
            ended: video.ended,
            currentTime: Number.isFinite(video.currentTime) ? video.currentTime : null,
            duration: Number.isFinite(video.duration) ? video.duration : null,
            videoWidth: video.videoWidth,
            videoHeight: video.videoHeight,
            clientWidth: video.clientWidth,
            clientHeight: video.clientHeight,
            rect: {
              width: rect.width,
              height: rect.height,
              top: rect.top,
              left: rect.left,
            },
            style: {
              display: style.display,
              visibility: style.visibility,
              opacity: style.opacity,
            },
            source: describeSource(video.currentSrc || video.src),
          };
        });

      return {
        documentVisibility: document.visibilityState,
        videoCount: document.querySelectorAll('video').length,
        videos,
      };
    })();
  """.trimIndent()
