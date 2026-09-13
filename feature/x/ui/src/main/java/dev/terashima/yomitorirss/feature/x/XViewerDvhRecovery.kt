package dev.terashima.yomitorirss.feature.x

import android.webkit.WebView

private const val X_MEDIA_VIEWPORT_RECOVERY_STATE_KEY = "__yomitoriMediaViewportRecovery"
private const val X_MEDIA_VIEWPORT_RECOVERY_RETRY_DELAY_MS = 250L
private const val X_MEDIA_VIEWPORT_RECOVERY_MAX_RETRIES = 80

internal fun WebView.installMediaViewportHeightRecoveryWhenReady(retryCount: Int = 0) {
  evaluateJavascript("document.readyState") { readyState ->
    if (readyState == "\"interactive\"" || readyState == "\"complete\"") {
      installMediaViewportHeightRecovery()
      return@evaluateJavascript
    }
    if (retryCount >= X_MEDIA_VIEWPORT_RECOVERY_MAX_RETRIES) return@evaluateJavascript
    postDelayed(
      { installMediaViewportHeightRecoveryWhenReady(retryCount + 1) },
      X_MEDIA_VIEWPORT_RECOVERY_RETRY_DELAY_MS,
    )
  }
}

internal fun WebView.installMediaViewportHeightRecovery() {
  evaluateJavascript(MEDIA_DVH_RECOVERY_SCRIPT, null)
}

private val MEDIA_DVH_RECOVERY_SCRIPT =
  """
    (() => {
      const stateKey = '$X_MEDIA_VIEWPORT_RECOVERY_STATE_KEY';
      const existing = window[stateKey];
      if (existing && typeof existing.repair === 'function') {
        existing.repair();
        return;
      }

      const originals = new WeakMap();
      let scheduled = false;
      let applying = false;

      const viewportHeight = () =>
        window.visualViewport?.height ||
        window.innerHeight ||
        document.documentElement.clientHeight;

      const parseDvh = (value, height) => {
        const match = /^([0-9]+(?:\.[0-9]+)?)dvh$/i.exec((value || '').trim());
        if (!match) return null;
        const amount = Number(match[1]);
        if (!Number.isFinite(amount)) return null;
        return height * amount / 100;
      };

      const repair = () => {
        scheduled = false;
        if (applying) return;

        const height = viewportHeight();
        if (!Number.isFinite(height) || height <= 1) return;

        applying = true;
        try {
          const candidates = new Set();
          for (const video of document.querySelectorAll('video')) {
            let current = video.parentElement;
            while (current && current instanceof HTMLElement) {
              candidates.add(current);
              if (current === document.body) break;
              current = current.parentElement;
            }
          }

          for (const element of candidates) {
            const saved = originals.get(element);
            const sourceHeight = saved?.height ?? element.style.height;
            const sourceMaxHeight = saved?.maxHeight ?? element.style.maxHeight;
            const heightPixels = parseDvh(sourceHeight, height);
            const maxHeightPixels = parseDvh(sourceMaxHeight, height);
            if (heightPixels == null && maxHeightPixels == null) continue;

            const rect = element.getBoundingClientRect();
            const style = getComputedStyle(element);
            const collapsedHeight = rect.height <= 1;
            const collapsedMaxHeight = style.maxHeight === '0px';
            if (!collapsedHeight && !collapsedMaxHeight && !saved) continue;

            if (!saved) {
              originals.set(element, {
                height: element.style.height,
                maxHeight: element.style.maxHeight,
              });
            }

            if (heightPixels != null && (collapsedHeight || saved)) {
              element.style.setProperty('height', heightPixels + 'px', 'important');
            }
            if (maxHeightPixels != null && (collapsedHeight || collapsedMaxHeight || saved)) {
              element.style.setProperty('max-height', maxHeightPixels + 'px', 'important');
            }
          }
        } finally {
          applying = false;
        }
      };

      const scheduleRepair = () => {
        if (scheduled || applying) return;
        scheduled = true;
        requestAnimationFrame(repair);
      };

      const observer = new MutationObserver(scheduleRepair);
      observer.observe(document.documentElement, {
        subtree: true,
        childList: true,
        attributes: true,
        attributeFilter: ['style'],
      });
      window.visualViewport?.addEventListener('resize', scheduleRepair);
      window.addEventListener('resize', scheduleRepair);

      window[stateKey] = {
        repair: scheduleRepair,
        stop: () => {
          observer.disconnect();
          window.visualViewport?.removeEventListener('resize', scheduleRepair);
          window.removeEventListener('resize', scheduleRepair);
        },
      };

      scheduleRepair();
    })();
  """.trimIndent()
