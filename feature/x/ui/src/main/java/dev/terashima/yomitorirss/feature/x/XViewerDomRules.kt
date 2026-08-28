package dev.terashima.yomitorirss.feature.x

import android.webkit.WebView
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

private const val X_ELEMENT_PICKER_STATE_KEY = "__yomitoriElementPicker"
private const val X_DOM_RULE_STATE_KEY = "__yomitoriDomRules"
private const val X_DOM_RULE_STYLE_ID = "yomitori-x-dom-rule-style"
private const val X_DOM_RULE_HIDDEN_ATTRIBUTE = "data-yomitori-dom-rule-hidden"
internal const val X_HOME_STANDARD_TIMELINE_TAB_COUNT = 2

internal fun isXCustomTimelineTabIndex(index: Int): Boolean =
  index >= X_HOME_STANDARD_TIMELINE_TAB_COUNT

internal fun decodeElementPickerDomRuleResult(result: String?): XViewerDomRule? {
  if (result == null || result == "null" || result == "undefined") return null
  val encoded = result.removeSurrounding("\"")
  if (encoded.isBlank()) return null

  return runCatching {
    val decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8.toString())
    val json = JSONObject(decoded)
    XViewerDomRule(
      kind = XViewerDomRuleKind.valueOf(json.getString("kind")),
      pagePath = json.getString("pagePath"),
      containerSelector = json.getString("containerSelector"),
      itemSelector = json.getString("itemSelector"),
      targetKind = XViewerDomTargetKind.valueOf(json.getString("targetKind")),
      targetValue = json.getString("targetValue"),
    )
  }.getOrNull()
}

internal fun domRulesJson(rules: List<XViewerDomRule>): String = JSONArray().apply {
  rules.forEach { rule ->
    put(
      JSONObject()
        .put("kind", rule.kind.name)
        .put("pagePath", rule.pagePath)
        .put("containerSelector", rule.containerSelector)
        .put("itemSelector", rule.itemSelector)
        .put("targetKind", rule.targetKind.name)
        .put("targetValue", rule.targetValue),
    )
  }
}.toString()

internal fun WebView.injectDomRules(rules: List<XViewerDomRule>) {
  val rulesLiteral = domRulesJson(rules)
  evaluateJavascript(
    """
      (() => {
        const stateKey = '$X_DOM_RULE_STATE_KEY';
        const styleId = '$X_DOM_RULE_STYLE_ID';
        const hiddenAttribute = '$X_DOM_RULE_HIDDEN_ATTRIBUTE';
        const rules = $rulesLiteral;
        const existingState = window[stateKey];
        if (existingState && existingState.observer) existingState.observer.disconnect();

        const clearHidden = () => {
          document.querySelectorAll('[' + hiddenAttribute + ']').forEach((element) => {
            element.removeAttribute(hiddenAttribute);
          });
        };

        clearHidden();
        if (!Array.isArray(rules) || rules.length === 0) {
          document.getElementById(styleId)?.remove();
          window[stateKey] = null;
          return;
        }

        let style = document.getElementById(styleId);
        if (!style) {
          style = document.createElement('style');
          style.id = styleId;
          document.head.appendChild(style);
        }
        style.textContent =
          '[' + hiddenAttribute + '="true"] {' +
          ' display: none !important;' +
          '}';

        const normalizeText = (value) => String(value || '').replace(/\s+/g, ' ').trim();
        const hrefFor = (item) => {
          if (item.matches('a[href]')) return item.getAttribute('href') || '';
          return item.querySelector('a[href]')?.getAttribute('href') || '';
        };
        const hrefPathFor = (item) => {
          const href = hrefFor(item);
          if (!href) return '';
          try {
            return new URL(href, location.origin).pathname;
          } catch (_) {
            return '';
          }
        };
        const ariaLabelFor = (item) => {
          const direct = item.getAttribute('aria-label') || '';
          if (direct) return direct;
          return item.querySelector('[role="tab"][aria-label]')?.getAttribute('aria-label') || '';
        };
        const matchesFingerprint = (item, target) => {
          switch (target.kind) {
            case 'HREF_PATH':
              return hrefPathFor(item) === target.value;
            case 'HREF':
              return hrefFor(item) === target.value;
            case 'ARIA_LABEL':
              return ariaLabelFor(item) === target.value;
            case 'TEXT':
              return normalizeText(item.textContent) === target.value;
            default:
              return false;
          }
        };
        const resolveFingerprintSet = (items, rule) => {
          let fingerprints;
          try {
            fingerprints = JSON.parse(rule.targetValue);
          } catch (_) {
            return null;
          }
          if (!Array.isArray(fingerprints) || fingerprints.length === 0) return null;

          const targets = [];
          const seen = new Set();
          for (const fingerprint of fingerprints) {
            if (!fingerprint || typeof fingerprint.kind !== 'string' ||
                typeof fingerprint.value !== 'string' || !fingerprint.value) {
              return null;
            }
            const matches = items.filter((item) => matchesFingerprint(item, fingerprint));
            if (matches.length !== 1 || seen.has(matches[0])) return null;
            seen.add(matches[0]);
            targets.push(matches[0]);
          }
          return targets;
        };
        const resolveTargets = (items, rule) => {
          if (rule.targetKind === 'FINGERPRINT_SET') {
            return resolveFingerprintSet(items, rule);
          }
          const target = { kind: rule.targetKind, value: rule.targetValue };
          const matches = items.filter((item) => matchesFingerprint(item, target));
          return matches.length > 0 ? matches : null;
        };

        const applyRule = (rule) => {
          if (rule.kind !== 'KEEP_MATCHING_ITEMS') return;
          if (rule.pagePath !== location.pathname) return;

          let containers;
          try {
            containers = Array.from(document.querySelectorAll(rule.containerSelector));
          } catch (_) {
            return;
          }
          if (containers.length === 0) return;

          const applicable = [];
          for (const container of containers) {
            let items;
            try {
              items = Array.from(container.querySelectorAll(rule.itemSelector));
            } catch (_) {
              continue;
            }
            if (items.length < 2) continue;

            const targets = resolveTargets(items, rule);
            if (!targets || targets.length === 0) continue;
            applicable.push({ items, targets });
          }
          if (applicable.length !== 1) return;

          const { items, targets } = applicable[0];
          const targetSet = new Set(targets);
          items.forEach((item) => {
            if (targetSet.has(item)) {
              item.removeAttribute(hiddenAttribute);
            } else {
              item.setAttribute(hiddenAttribute, 'true');
            }
          });
        };

        let scheduled = false;
        const applyAll = () => {
          scheduled = false;
          clearHidden();
          rules.forEach(applyRule);
        };
        const scheduleApply = () => {
          if (scheduled) return;
          scheduled = true;
          requestAnimationFrame(applyAll);
        };

        applyAll();
        const observer = new MutationObserver(scheduleApply);
        observer.observe(document.documentElement, {
          childList: true,
          subtree: true,
          characterData: true,
        });
        window[stateKey] = { observer };
      })();
    """.trimIndent(),
    null,
  )
}

internal fun WebView.takeSelectedElementListGroupRule(onResult: (XViewerDomRule?) -> Unit) {
  evaluateJavascript(
    """
      (() => {
        const stateKey = '$X_ELEMENT_PICKER_STATE_KEY';
        const state = window[stateKey];
        if (!state || !state.selected) return null;

        const selected = state.selected;
        const closestSelectedTab = selected.closest('[role="tab"]');
        const descendantTabs = closestSelectedTab
          ? []
          : Array.from(selected.querySelectorAll('[role="tab"]'));
        const selectedTab = closestSelectedTab ||
          (descendantTabs.length === 1 ? descendantTabs[0] : null);
        const standardTabCount = $X_HOME_STANDARD_TIMELINE_TAB_COUNT;
        const pagePath = location.pathname || '/';
        let rule = null;

        const escapeAttributeValue = (value) => String(value)
          .replace(/\\/g, '\\\\')
          .replace(/"/g, '\\"');
        const attributeSelector = (element, name) => {
          const value = element.getAttribute(name);
          if (!value) return null;
          return '[' + name + '="' + escapeAttributeValue(value) + '"]';
        };
        const normalizeText = (value) => String(value || '').replace(/\s+/g, ' ').trim();
        const hrefFor = (candidate) => {
          if (candidate.matches('a[href]')) return candidate.getAttribute('href') || '';
          return candidate.querySelector('a[href]')?.getAttribute('href') || '';
        };
        const hrefPathFor = (candidate) => {
          const href = hrefFor(candidate);
          if (!href) return '';
          try {
            return new URL(href, location.origin).pathname;
          } catch (_) {
            return '';
          }
        };
        const ariaLabelFor = (candidate) => {
          const direct = candidate.getAttribute('aria-label') || '';
          if (direct) return direct;
          return candidate.querySelector('[role="tab"][aria-label]')?.getAttribute('aria-label') || '';
        };
        const matchesFingerprint = (candidate, target) => {
          if (target.kind === 'HREF_PATH') return hrefPathFor(candidate) === target.value;
          if (target.kind === 'HREF') return hrefFor(candidate) === target.value;
          if (target.kind === 'ARIA_LABEL') return ariaLabelFor(candidate) === target.value;
          if (target.kind === 'TEXT') return normalizeText(candidate.textContent) === target.value;
          return false;
        };
        const uniqueFingerprintFor = (candidate, items) => {
          const candidates = [];
          const hrefPath = hrefPathFor(candidate);
          if (hrefPath) candidates.push({ kind: 'HREF_PATH', value: hrefPath });
          const href = hrefFor(candidate);
          if (href) candidates.push({ kind: 'HREF', value: href });
          const ariaLabel = ariaLabelFor(candidate);
          if (ariaLabel) candidates.push({ kind: 'ARIA_LABEL', value: ariaLabel });
          const text = normalizeText(candidate.textContent);
          if (text) candidates.push({ kind: 'TEXT', value: text });

          return candidates.find(
            (target) => items.filter((item) => matchesFingerprint(item, target)).length === 1
          ) || null;
        };
        const selectorMatchesContainer = (selector, container) => {
          try {
            return Array.from(document.querySelectorAll(selector)).includes(container);
          } catch (_) {
            return false;
          }
        };
        const uniquelySelects = (selector, element) => {
          try {
            const matches = document.querySelectorAll(selector);
            return matches.length === 1 && matches[0] === element;
          } catch (_) {
            return false;
          }
        };
        const containerSelectorFor = (container) => {
          const candidates = [];
          if (container.matches('[data-testid="ScrollSnap-List"]')) {
            candidates.push('[data-testid="primaryColumn"] [data-testid="ScrollSnap-List"]');
          }
          if (container.matches('[role="tablist"]')) {
            candidates.push('[data-testid="primaryColumn"] [role="tablist"]');
          }

          const tag = container.tagName.toLowerCase();
          const testId = attributeSelector(container, 'data-testid');
          const role = attributeSelector(container, 'role');
          if (testId && role) candidates.push(tag + testId + role);
          if (testId) candidates.push(tag + testId);
          if (role) candidates.push(tag + role);

          const uniqueCandidates = Array.from(new Set(candidates));
          return uniqueCandidates.find(
            (selector) => uniquelySelects(selector, container)
          ) || uniqueCandidates.find(
            (selector) => selectorMatchesContainer(selector, container)
          ) || null;
        };
        const directPresentationItemFor = (tab, container) => {
          let current = tab;
          while (current && current.parentElement !== container) current = current.parentElement;
          if (!current || current.parentElement !== container) return null;
          return current.matches('[role="presentation"]') ? current : null;
        };

        if (selectedTab && (pagePath === '/home' || pagePath === '/')) {
          const container = selectedTab.closest('[data-testid="ScrollSnap-List"], [role="tablist"]');
          if (container) {
            const containerSelector = containerSelectorFor(container);
            const directItems = Array.from(container.querySelectorAll(':scope > [role="presentation"]'));
            const timelinePresentationItems = directItems.filter(
              (item) => item.querySelector('[role="tab"]')
            );
            const selectedPresentationItem = directPresentationItemFor(selectedTab, container);

            let items;
            let timelineItems;
            let selectedItem;
            let itemSelector;
            if (selectedPresentationItem && timelinePresentationItems.length > standardTabCount) {
              items = directItems;
              timelineItems = timelinePresentationItems;
              selectedItem = selectedPresentationItem;
              itemSelector = ':scope > [role="presentation"]';
            } else {
              timelineItems = Array.from(
                container.querySelectorAll('[role="tab"]')
              );
              items = timelineItems;
              selectedItem = selectedTab;
              itemSelector = '[role="tab"]';
            }

            const selectedIndex = timelineItems.indexOf(selectedItem);
            if (containerSelector && selectedIndex >= standardTabCount) {
              const keptItems = timelineItems.slice(standardTabCount);
              const fingerprints = keptItems.map(
                (item) => uniqueFingerprintFor(item, items)
              );
              if (keptItems.length > 0 && fingerprints.every(Boolean)) {
                rule = {
                  kind: 'KEEP_MATCHING_ITEMS',
                  pagePath,
                  containerSelector,
                  itemSelector,
                  targetKind: 'FINGERPRINT_SET',
                  targetValue: JSON.stringify(fingerprints),
                };
              }
            }
          }
        }

        if (typeof state.stop === 'function') state.stop();
        window[stateKey] = null;
        return rule ? encodeURIComponent(JSON.stringify(rule)) : null;
      })();
    """.trimIndent(),
  ) { result ->
    onResult(decodeElementPickerDomRuleResult(result))
  }
}
