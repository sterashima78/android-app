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
        const matchesTarget = (item, rule) => {
          switch (rule.targetKind) {
            case 'HREF':
              return hrefFor(item) === rule.targetValue;
            case 'ARIA_LABEL':
              return (item.getAttribute('aria-label') || '') === rule.targetValue;
            case 'TEXT':
              return normalizeText(item.textContent) === rule.targetValue;
            default:
              return false;
          }
        };

        const applyRule = (rule) => {
          if (rule.kind !== 'KEEP_ONLY_MATCHING_ITEM') return;
          if (rule.pagePath !== location.pathname) return;

          let containers;
          try {
            containers = document.querySelectorAll(rule.containerSelector);
          } catch (_) {
            return;
          }

          containers.forEach((container) => {
            let items;
            try {
              items = Array.from(container.querySelectorAll(rule.itemSelector));
            } catch (_) {
              return;
            }
            if (items.length < 2) return;

            const targets = items.filter((item) => matchesTarget(item, rule));
            if (targets.length !== 1) return;
            const target = targets[0];
            items.forEach((item) => {
              if (item === target) {
                item.removeAttribute(hiddenAttribute);
              } else {
                item.setAttribute(hiddenAttribute, 'true');
              }
            });
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

internal fun WebView.takeSelectedElementKeepOnlyRule(onResult: (XViewerDomRule?) -> Unit) {
  evaluateJavascript(
    """
      (() => {
        const stateKey = '$X_ELEMENT_PICKER_STATE_KEY';
        const state = window[stateKey];
        if (!state || !state.selected) return null;

        const selected = state.selected;
        const item = selected.closest('[role="tab"]');
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

        if (item) {
          const container = item.closest('[role="tablist"], [data-testid="ScrollSnap-List"]');
          if (container) {
            const tag = container.tagName.toLowerCase();
            const testId = attributeSelector(container, 'data-testid');
            const role = attributeSelector(container, 'role');
            const containerSelector = testId && role
              ? tag + testId + role
              : testId
                ? tag + testId
                : role
                  ? tag + role
                  : null;
            const itemSelector = '[role="tab"]';
            const items = Array.from(container.querySelectorAll(itemSelector));

            const targetCandidates = [];
            const href = hrefFor(item);
            if (href) targetCandidates.push({ kind: 'HREF', value: href });
            const ariaLabel = item.getAttribute('aria-label') || '';
            if (ariaLabel) targetCandidates.push({ kind: 'ARIA_LABEL', value: ariaLabel });
            const text = normalizeText(item.textContent);
            if (text) targetCandidates.push({ kind: 'TEXT', value: text });

            const matches = (candidate, target) => {
              if (target.kind === 'HREF') return hrefFor(candidate) === target.value;
              if (target.kind === 'ARIA_LABEL') {
                return (candidate.getAttribute('aria-label') || '') === target.value;
              }
              if (target.kind === 'TEXT') return normalizeText(candidate.textContent) === target.value;
              return false;
            };

            if (containerSelector && items.length >= 2) {
              const target = targetCandidates.find(
                (candidate) => items.filter((entry) => matches(entry, candidate)).length === 1
              );
              if (target) {
                rule = {
                  kind: 'KEEP_ONLY_MATCHING_ITEM',
                  pagePath: location.pathname || '/',
                  containerSelector,
                  itemSelector,
                  targetKind: target.kind,
                  targetValue: target.value,
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
