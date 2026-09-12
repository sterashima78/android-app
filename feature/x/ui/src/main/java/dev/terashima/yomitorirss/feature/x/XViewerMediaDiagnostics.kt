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
        TextButton(
          onClick = {
            context.getSystemService(ClipboardManager::class.java)
              ?.setPrimaryClip(ClipData.newPlainText("media diagnostics", text))
          },
        ) {
          Text("コピー")
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

      const describePaintStyle = (style) => ({
        display: style.display,
        visibility: style.visibility,
        opacity: style.opacity,
        position: style.position,
        zIndex: style.zIndex,
        transform: style.transform,
        transformStyle: style.transformStyle,
        backfaceVisibility: style.backfaceVisibility,
        filter: style.filter,
        clipPath: style.clipPath,
        mixBlendMode: style.mixBlendMode,
        isolation: style.isolation,
        backgroundColor: style.backgroundColor,
        pointerEvents: style.pointerEvents,
        objectFit: style.objectFit,
        objectPosition: style.objectPosition,
      });

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
            ...describePaintStyle(style),
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
        for (let depth = 0; current && depth < 12; depth += 1) {
          if (!(current instanceof Element)) break;
          chain.push(describeLayoutNode(current, depth));
          current = current.parentElement;
        }
        return chain;
      };

      const sampleFrame = (video) => {
        if (video.readyState < 2 || video.videoWidth <= 0 || video.videoHeight <= 0) {
          return { available: false, reason: 'not-ready' };
        }
        try {
          const canvas = document.createElement('canvas');
          canvas.width = 8;
          canvas.height = 8;
          const context = canvas.getContext('2d', { willReadFrequently: true });
          if (!context) return { available: false, reason: 'no-context' };
          context.drawImage(video, 0, 0, 8, 8);
          const pixels = context.getImageData(0, 0, 8, 8).data;
          let red = 0;
          let green = 0;
          let blue = 0;
          let luma = 0;
          let blackPixels = 0;
          const count = pixels.length / 4;
          for (let index = 0; index < pixels.length; index += 4) {
            const r = pixels[index];
            const g = pixels[index + 1];
            const b = pixels[index + 2];
            red += r;
            green += g;
            blue += b;
            const value = 0.2126 * r + 0.7152 * g + 0.0722 * b;
            luma += value;
            if (value < 8) blackPixels += 1;
          }
          return {
            available: true,
            averageRgb: {
              red: red / count,
              green: green / count,
              blue: blue / count,
            },
            averageLuma: luma / count,
            blackPixelRatio: blackPixels / count,
          };
        } catch (error) {
          return {
            available: false,
            reason: error?.name || 'sampling-failed',
          };
        }
      };

      const describeHitTest = (video) => {
        const rect = video.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0) return [];
        const x = Math.max(0, Math.min(window.innerWidth - 1, rect.left + rect.width / 2));
        const y = Math.max(0, Math.min(window.innerHeight - 1, rect.top + rect.height / 2));
        return document.elementsFromPoint(x, y)
          .slice(0, 8)
          .map((element, index) => {
            const style = getComputedStyle(element);
            const elementRect = element.getBoundingClientRect();
            return {
              index,
              tag: element.tagName.toLowerCase(),
              isVideo: element === video,
              containsVideo: element.contains(video),
              rect: {
                width: elementRect.width,
                height: elementRect.height,
                top: elementRect.top,
                left: elementRect.left,
              },
              style: describePaintStyle(style),
            };
          });
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
            style: describePaintStyle(style),
            source: describeSource(video.currentSrc || video.src),
            frameSample: sampleFrame(video),
            hitTest: describeHitTest(video),
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
