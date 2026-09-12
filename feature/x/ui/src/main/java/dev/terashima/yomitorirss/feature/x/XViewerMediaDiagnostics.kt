package dev.terashima.yomitorirss.feature.x

import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun XViewerMediaDiagnosticsButton(
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
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
      dismissButton = {
        Row {
          TextButton(
            onClick = {
              val webView = rootView.findHostedWebView() ?: return@TextButton
              webView.evaluateJavascript(MEDIA_LAYOUT_RECOVERY_SCRIPT) {
                webView.evaluateJavascript(MEDIA_DIAGNOSTICS_SCRIPT) { result ->
                  diagnostics = prettyMediaDiagnostics(result)
                }
              }
            },
          ) {
            Text("表示を補正")
          }
          TextButton(
            onClick = {
              context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("media diagnostics", text))
            },
          ) {
            Text("コピー")
          }
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

private val MEDIA_LAYOUT_RECOVERY_SCRIPT =
  """
    (() => {
      const viewportWidth = window.visualViewport?.width || window.innerWidth;
      const viewportHeight = window.visualViewport?.height || window.innerHeight;
      const minHeightByElement = new Map();
      let candidateVideos = 0;

      for (const video of document.querySelectorAll('video')) {
        const rect = video.getBoundingClientRect();
        if (
          video.readyState < 2 ||
          video.videoWidth <= 0 ||
          video.videoHeight <= 0 ||
          rect.height > 1 ||
          rect.width < viewportWidth * 0.8 ||
          Math.abs(rect.left) > viewportWidth * 0.1
        ) {
          continue;
        }

        candidateVideos += 1;
        const targetHeight = Math.min(
          viewportHeight,
          rect.width * video.videoHeight / video.videoWidth,
        );

        let current = video;
        for (let depth = 0; current instanceof Element && depth < 12; depth += 1) {
          const currentRect = current.getBoundingClientRect();
          if (currentRect.height > 1 || currentRect.width < viewportWidth * 0.7) break;
          const previous = minHeightByElement.get(current) || 0;
          minHeightByElement.set(current, Math.max(previous, targetHeight));
          current = current.parentElement;
        }
      }

      for (const [element, targetHeight] of minHeightByElement) {
        element.style.setProperty('min-height', targetHeight + 'px', 'important');
        if (element.tagName === 'VIDEO') {
          element.style.setProperty('object-fit', 'contain', 'important');
        }
      }

      return {
        candidateVideos,
        repairedElements: minHeightByElement.size,
      };
    })();
  """.trimIndent()

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

      const describeLayoutNode = (element, depth) => {
        const rect = element.getBoundingClientRect();
        const style = getComputedStyle(element);
        return {
          depth,
          tag: element.tagName.toLowerCase(),
          rect: {
            width: rect.width,
            height: rect.height,
            top: rect.top,
            left: rect.left,
          },
          clientWidth: element.clientWidth,
          clientHeight: element.clientHeight,
          scrollWidth: element.scrollWidth,
          scrollHeight: element.scrollHeight,
          style: {
            display: style.display,
            position: style.position,
            width: style.width,
            height: style.height,
            minHeight: style.minHeight,
            maxHeight: style.maxHeight,
            aspectRatio: style.aspectRatio,
            overflow: style.overflow,
            overflowX: style.overflowX,
            overflowY: style.overflowY,
            paddingTop: style.paddingTop,
            paddingBottom: style.paddingBottom,
            flex: style.flex,
            flexBasis: style.flexBasis,
            alignSelf: style.alignSelf,
          },
        };
      };

      const describeLayoutChain = (video) => {
        const chain = [];
        let current = video;
        for (let depth = 0; current && depth < 8; depth += 1) {
          if (!(current instanceof Element)) break;
          chain.push(describeLayoutNode(current, depth));
          current = current.parentElement;
        }
        return chain;
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
            layoutChain: describeLayoutChain(video),
          };
        });

      return {
        documentVisibility: document.visibilityState,
        viewport: {
          innerWidth: window.innerWidth,
          innerHeight: window.innerHeight,
          visualViewportWidth: window.visualViewport?.width ?? null,
          visualViewportHeight: window.visualViewport?.height ?? null,
        },
        videoCount: document.querySelectorAll('video').length,
        videos,
      };
    })();
  """.trimIndent()
