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
internal fun XViewerDvhRecoveryButton(
  modifier: Modifier = Modifier,
) {
  val rootView = LocalView.current.rootView

  TextButton(
    modifier = modifier,
    onClick = {
      rootView.findWebViewForDvhRecovery()
        ?.evaluateJavascript(MEDIA_DVH_RECOVERY_SCRIPT, null)
    },
  ) {
    Text("高さ補正")
  }
}

private fun View.findWebViewForDvhRecovery(): WebView? {
  if (this is WebView) return this
  if (this !is ViewGroup) return null
  for (index in 0 until childCount) {
    getChildAt(index).findWebViewForDvhRecovery()?.let { return it }
  }
  return null
}

private val MEDIA_DVH_RECOVERY_SCRIPT =
  """
    (() => {
      const viewportHeight =
        window.visualViewport?.height ||
        window.innerHeight ||
        document.documentElement.clientHeight;
      if (!Number.isFinite(viewportHeight) || viewportHeight <= 1) {
        return { repaired: 0, reason: 'invalid-viewport-height' };
      }

      const parseDvh = (value) => {
        const match = /^([0-9]+(?:\.[0-9]+)?)dvh$/i.exec((value || '').trim());
        if (!match) return null;
        const amount = Number(match[1]);
        if (!Number.isFinite(amount)) return null;
        return viewportHeight * amount / 100;
      };

      let repaired = 0;
      const results = [];
      for (const element of document.querySelectorAll('*')) {
        if (!(element instanceof HTMLElement)) continue;

        const inlineHeight = element.style.height;
        const inlineMaxHeight = element.style.maxHeight;
        const heightPixels = parseDvh(inlineHeight);
        const maxHeightPixels = parseDvh(inlineMaxHeight);
        if (heightPixels == null && maxHeightPixels == null) continue;

        const beforeRect = element.getBoundingClientRect();
        const beforeStyle = getComputedStyle(element);
        const collapsedHeight = beforeRect.height <= 1;
        const collapsedMaxHeight = beforeStyle.maxHeight === '0px';
        if (!collapsedHeight && !collapsedMaxHeight) continue;

        if (heightPixels != null && collapsedHeight) {
          element.style.setProperty('height', heightPixels + 'px', 'important');
        }
        if (maxHeightPixels != null && (collapsedHeight || collapsedMaxHeight)) {
          element.style.setProperty('max-height', maxHeightPixels + 'px', 'important');
        }

        const afterRect = element.getBoundingClientRect();
        const afterStyle = getComputedStyle(element);
        repaired += 1;
        results.push({
          tag: element.tagName.toLowerCase(),
          inlineHeight,
          inlineMaxHeight,
          beforeHeight: beforeRect.height,
          beforeMaxHeight: beforeStyle.maxHeight,
          afterHeight: afterRect.height,
          afterMaxHeight: afterStyle.maxHeight,
        });
        if (results.length >= 8) break;
      }

      return {
        repaired,
        viewportHeight,
        results,
      };
    })();
  """.trimIndent()
