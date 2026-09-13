package dev.terashima.yomitorirss.feature.x

import android.webkit.WebView

private const val MEDIA_VIEWPORT_RECOVERY_STATE_KEY = "__yomitoriMediaViewportRecovery"

internal fun WebView.installMediaViewportHeightRecovery() {
  evaluateJavascript(MEDIA_VIEWPORT_HEIGHT_RECOVERY_SCRIPT, null)
}

private val MEDIA_VIEWPORT_HEIGHT_RECOVERY_SCRIPT =
  """
    (() => {
      const stateKey = '$MEDIA_VIEWPORT_RECOVERY_STATE_KEY';
      const existing = window[stateKey];
      if (existing && typeof existing.schedule === 'function') {
        existing.schedule();
        return;
      }

      const tracked = new WeakMap();
      let scheduled = false;
      let applying = false;

      const currentViewportHeight = () =>
        window.visualViewport?.height ||
        window.innerHeight ||
        document.documentElement.clientHeight;

      const dvhAmount = (value) => {
        const match = /^([0-9]+(?:\.[0-9]+)?)dvh$/i.exec((value || '').trim());
        if (!match) return null;
        const amount = Number(match[1]);
        return Number.isFinite(amount) ? amount : null;
      };

      const resolveDvh = (value, viewportHeight) => {
        const amount = dvhAmount(value);
        return amount == null ? null : viewportHeight * amount / 100;
      };

      const trackPageValue = (state, key, appliedKey, currentValue) => {
        if (dvhAmount(currentValue) != null) {
          state[key] = currentValue;
          return;
        }
        if (state[appliedKey] == null || currentValue !== state[appliedKey]) {
          state[key] = null;
          state[appliedKey] = null;
        }
      };

      const repair = () => {
        scheduled = false;
        if (applying) return;

        const viewportHeight = currentViewportHeight();
        if (!Number.isFinite(viewportHeight) || viewportHeight <= 1) return;

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
            const inlineHeight = element.style.height;
            const inlineMaxHeight = element.style.maxHeight;
            const inlineHeightPixels = resolveDvh(inlineHeight, viewportHeight);
            const inlineMaxHeightPixels = resolveDvh(inlineMaxHeight, viewportHeight);
            let state = tracked.get(element);
            if (!state && inlineHeightPixels == null && inlineMaxHeightPixels == null) continue;

            if (!state) {
              state = {
                requestedHeight: null,
                requestedMaxHeight: null,
                appliedHeight: null,
                appliedMaxHeight: null,
              };
              tracked.set(element, state);
            }

            trackPageValue(state, 'requestedHeight', 'appliedHeight', inlineHeight);
            trackPageValue(state, 'requestedMaxHeight', 'appliedMaxHeight', inlineMaxHeight);

            const heightPixels = resolveDvh(state.requestedHeight, viewportHeight);
            const maxHeightPixels = resolveDvh(state.requestedMaxHeight, viewportHeight);
            if (heightPixels == null && maxHeightPixels == null) continue;

            const rect = element.getBoundingClientRect();
            const computed = getComputedStyle(element);
            const collapsedHeight = rect.height <= 1;
            const collapsedMaxHeight = computed.maxHeight === '0px';

            if (heightPixels != null) {
              const target = heightPixels + 'px';
              const pageStillRequestsDvh = inlineHeightPixels != null;
              const recoveryOwnsValue = state.appliedHeight != null && inlineHeight === state.appliedHeight;
              if ((pageStillRequestsDvh && collapsedHeight) || (recoveryOwnsValue && state.appliedHeight !== target)) {
                element.style.setProperty('height', target, 'important');
                state.appliedHeight = target;
              }
            }

            if (maxHeightPixels != null) {
              const target = maxHeightPixels + 'px';
              const pageStillRequestsDvh = inlineMaxHeightPixels != null;
              const recoveryOwnsValue = state.appliedMaxHeight != null && inlineMaxHeight === state.appliedMaxHeight;
              if (
                (pageStillRequestsDvh && (collapsedHeight || collapsedMaxHeight)) ||
                (recoveryOwnsValue && state.appliedMaxHeight !== target)
              ) {
                element.style.setProperty('max-height', target, 'important');
                state.appliedMaxHeight = target;
              }
            }
          }
        } finally {
          applying = false;
        }
      };

      const schedule = () => {
        if (scheduled || applying) return;
        scheduled = true;
        requestAnimationFrame(repair);
      };

      const containsVideo = (node) =>
        node instanceof HTMLVideoElement ||
        (node instanceof Element && node.querySelector('video') != null);

      const mutationNeedsRepair = (mutation) => {
        if (mutation.type === 'childList') {
          return Array.from(mutation.addedNodes).some(containsVideo);
        }
        if (mutation.type !== 'attributes' || !(mutation.target instanceof HTMLElement)) {
          return false;
        }

        const element = mutation.target;
        return tracked.has(element) ||
          dvhAmount(element.style.height) != null ||
          dvhAmount(element.style.maxHeight) != null ||
          element.querySelector('video') != null;
      };

      const observer = new MutationObserver((mutations) => {
        if (mutations.some(mutationNeedsRepair)) schedule();
      });
      observer.observe(document.documentElement, {
        subtree: true,
        childList: true,
        attributes: true,
        attributeFilter: ['style'],
      });
      window.visualViewport?.addEventListener('resize', schedule);
      window.addEventListener('resize', schedule);
      window[stateKey] = { schedule };

      schedule();
    })();
  """.trimIndent()
