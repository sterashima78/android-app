package dev.terashima.yomitorirss.feature.x

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import org.json.JSONObject

@Composable
internal fun XViewerLayoutBoundaryDiagnosticsButton(
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val rootView = LocalView.current.rootView
  var diagnostics by remember { mutableStateOf<String?>(null) }

  TextButton(
    modifier = modifier,
    onClick = {
      val webView = rootView.findWebViewForLayoutBoundaryDiagnostics()
      if (webView == null) {
        diagnostics = "WebView not found"
      } else {
        webView.evaluateJavascript(MEDIA_LAYOUT_BOUNDARY_DIAGNOSTICS_SCRIPT) { result ->
          diagnostics = prettyLayoutBoundaryDiagnostics(result)
        }
      }
    },
  ) {
    Text("境界診断")
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
        TextButton(
          onClick = {
            context.getSystemService(ClipboardManager::class.java)
              ?.setPrimaryClip(ClipData.newPlainText("layout boundary diagnostics", text))
          },
        ) {
          Text("コピー")
        }
      },
      title = { Text("境界診断") },
      text = {
        SelectionContainer {
          Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = text, modifier = Modifier.padding(vertical = 4.dp))
          }
        }
      },
    )
  }
}

private fun View.findWebViewForLayoutBoundaryDiagnostics(): WebView? {
  if (this is WebView) return this
  if (this !is ViewGroup) return null
  for (index in 0 until childCount) {
    getChildAt(index).findWebViewForLayoutBoundaryDiagnostics()?.let { return it }
  }
  return null
}

internal fun prettyLayoutBoundaryDiagnostics(result: String?): String {
  if (result.isNullOrBlank() || result == "null" || result == "undefined") {
    return "No diagnostic result"
  }
  return runCatching { JSONObject(result).toString(2) }.getOrElse { result }
}

private val MEDIA_LAYOUT_BOUNDARY_DIAGNOSTICS_SCRIPT =
  """
    (() => {
      const rectOf = (element) => {
        const rect = element.getBoundingClientRect();
        return {
          width: rect.width,
          height: rect.height,
          top: rect.top,
          left: rect.left,
        };
      };

      const describeNode = (element, depth) => {
        const style = getComputedStyle(element);
        const inlineStyle = element.style;
        return {
          depth,
          tag: element.tagName.toLowerCase(),
          rect: rectOf(element),
          clientWidth: element.clientWidth,
          clientHeight: element.clientHeight,
          scrollWidth: element.scrollWidth,
          scrollHeight: element.scrollHeight,
          hidden: Boolean(element.hidden),
          inert: Boolean(element.inert),
          ariaHidden: element.getAttribute('aria-hidden') === 'true',
          offsetParentTag: element.offsetParent?.tagName?.toLowerCase() ?? null,
          style: {
            display: style.display,
            visibility: style.visibility,
            opacity: style.opacity,
            position: style.position,
            width: style.width,
            height: style.height,
            minHeight: style.minHeight,
            maxHeight: style.maxHeight,
            overflow: style.overflow,
            overflowX: style.overflowX,
            overflowY: style.overflowY,
            contain: style.contain,
            contentVisibility: style.contentVisibility,
            transform: style.transform,
            transitionProperty: style.transitionProperty,
            transitionDuration: style.transitionDuration,
            animationName: style.animationName,
            animationDuration: style.animationDuration,
            flex: style.flex,
            flexBasis: style.flexBasis,
            flexDirection: style.flexDirection,
            alignSelf: style.alignSelf,
            top: style.top,
            right: style.right,
            bottom: style.bottom,
            left: style.left,
          },
          inlineStyle: {
            height: inlineStyle.height || null,
            minHeight: inlineStyle.minHeight || null,
            maxHeight: inlineStyle.maxHeight || null,
            overflow: inlineStyle.overflow || null,
            transform: inlineStyle.transform || null,
          },
        };
      };

      const chainFor = (video) => {
        const chain = [];
        let current = video;
        for (let depth = 0; current && depth < 32; depth += 1) {
          if (!(current instanceof Element)) break;
          chain.push(describeNode(current, depth));
          if (current === document.documentElement) break;
          current = current.parentElement;
        }
        return chain;
      };

      const describeRoot = (element) => element ? {
        tag: element.tagName.toLowerCase(),
        rect: rectOf(element),
        clientWidth: element.clientWidth,
        clientHeight: element.clientHeight,
        scrollWidth: element.scrollWidth,
        scrollHeight: element.scrollHeight,
        style: {
          display: getComputedStyle(element).display,
          height: getComputedStyle(element).height,
          minHeight: getComputedStyle(element).minHeight,
          maxHeight: getComputedStyle(element).maxHeight,
          overflow: getComputedStyle(element).overflow,
        },
      } : null;

      const videos = Array.from(document.querySelectorAll('video'))
        .slice(0, 6)
        .map((video, index) => ({
          index,
          paused: video.paused,
          currentTime: Number.isFinite(video.currentTime) ? video.currentTime : null,
          readyState: video.readyState,
          videoWidth: video.videoWidth,
          videoHeight: video.videoHeight,
          chain: chainFor(video),
        }));

      return {
        documentVisibility: document.visibilityState,
        viewport: {
          innerWidth: window.innerWidth,
          innerHeight: window.innerHeight,
          visualViewportWidth: window.visualViewport?.width ?? null,
          visualViewportHeight: window.visualViewport?.height ?? null,
        },
        documentElement: describeRoot(document.documentElement),
        body: describeRoot(document.body),
        videoCount: document.querySelectorAll('video').length,
        videos,
      };
    })();
  """.trimIndent()
