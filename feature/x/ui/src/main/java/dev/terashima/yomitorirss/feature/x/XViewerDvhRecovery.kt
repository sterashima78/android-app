package dev.terashima.yomitorirss.feature.x

import android.webkit.WebView

private const val X_MEDIA_VIEWPORT_RECOVERY_STATE_KEY = "__yomitoriMediaViewportRecovery"

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
            let saved = originals.get(element);
            const inlineHeight = element.style.height;
            const inlineMaxHeight = element.style.maxHeight;
            const inlineHeightPixels = parseDvh(inlineHeight, height);
            const inlineMaxHeightPixels = parseDvh(inlineMaxHeight, height);

            if (inlineHeightPixels != null || inlineMaxHeightPixels != null) {
              if (!saved) {
                saved = {
                  height: inlineHeight,
                  maxHeight: inlineMaxHeight,
                  appliedHeight: null,
                  appliedMaxHeight: null,
                };
                originals.set(element, saved);
              } else {
                if (inlineHeightPixels != null) saved.height = inlineHeight;
                if (inlineMaxHeightPixels != null) saved.maxHeight = inlineMaxHeight;
              }
            }

            if (!saved) continue;

            const heightPixels = parseDvh(saved.height, height);
            const maxHeightPixels = parseDvh(saved.maxHeight, height);
            if (heightPixels == null && maxHeightPixels == null) continue;

            const rect = element.getBoundingClientRect();
            const style = getComputedStyle(element);
            const collapsedHeight = rect.height <= 1;
            const collapsedMaxHeight = style.maxHeight === '0px';

            if (heightPixels != null) {
              const target = heightPixels + 'px';
              const stillOwned = saved.appliedHeight != null && inlineHeight === saved.appliedHeight;
              const pageStillRequestsDvh = inlineHeightPixels != null;
              if (
                (pageStillRequestsDvh && collapsedHeight) ||
                (stillOwned && saved.appliedHeight !== target)
              ) {
                if (inlineHeight !== target || element.style.getPropertyPriority('height') !== 'important') {
                  element.style.setProperty('height', target, 'important');
                }
                saved.appliedHeight = target;
              }
            }

            if (maxHeightPixels != null) {
              const target = maxHeightPixels + 'px';
              const stillOwned = saved.appliedMaxHeight != null && inlineMaxHeight === saved.appliedMaxHeight;
              const pageStillRequestsDvh = inlineMaxHeightPixels != null;
              if (
                (pageStillRequestsDvh && (collapsedHeight || collapsedMaxHeight)) ||
                (stillOwned && saved.appliedMaxHeight !== target)
              ) {
                if (
                  inlineMaxHeight !== target ||
                  element.style.getPropertyPriority('max-height') !== 'important'
                ) {
                  element.style.setProperty('max-height', target, 'important');
                }
                saved.appliedMaxHeight = target;
              }
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
