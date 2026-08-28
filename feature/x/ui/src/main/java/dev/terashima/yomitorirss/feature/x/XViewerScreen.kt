package dev.terashima.yomitorirss.feature.x

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val X_HOME_URL = "https://x.com/"
private const val X_CUSTOM_CSS_STYLE_ID = "yomitori-x-custom-css"
private const val X_ELEMENT_PICKER_STATE_KEY = "__yomitoriElementPicker"
private const val X_ELEMENT_PICKER_STYLE_ID = "yomitori-x-element-picker-style"
private const val X_ELEMENT_PICKER_SELECTED_ATTRIBUTE = "data-yomitori-element-picker-selected"
private val WEBVIEW_USER_AGENT_MARKER = Regex(";\\s*wv(?=\\))")
private val WEBVIEW_VERSION_TOKEN = Regex("\\bVersion/4\\.0\\s+")

@SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
@Composable
fun XViewerScreen(
  repository: XViewerCssRepository,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val defaultCss = remember(repository) { repository.defaultCss() }
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  var pickerActive by remember { mutableStateOf(false) }
  var currentUrl by remember { mutableStateOf(X_HOME_URL) }
  var rendererGeneration by remember { mutableIntStateOf(0) }
  var rendererCrashed by remember { mutableStateOf(false) }

  if (rendererCrashed) {
    Box(modifier = modifier.fillMaxSize()) {
      Surface(
        modifier = Modifier
          .align(Alignment.Center)
          .padding(24.dp)
          .widthIn(max = 520.dp),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 6.dp,
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text("X の Web 表示処理が異常終了しました", style = MaterialTheme.typography.titleMedium)
          Text("同じページを自動で再読込せず、X のホームから再試行します。")
          TextButton(
            onClick = {
              currentUrl = X_HOME_URL
              rendererCrashed = false
            },
          ) {
            Text("X を再読み込み")
          }
        }
      }
    }
    return
  }

  val rendererLifecycle = remember(context, defaultCss, repository, rendererGeneration) {
    XWebViewRendererLifecycle()
  }
  val initialUrl = currentUrl.takeIf { it.isXUrl() } ?: X_HOME_URL
  val webView = remember(context, defaultCss, repository, rendererGeneration, rendererLifecycle) {
    WebView(context).apply {
      settings.javaScriptEnabled = true
      settings.domStorageEnabled = true
      settings.allowFileAccess = false
      settings.allowContentAccess = false

      // Match normal mobile-browser viewport behavior. Android WebView does not
      // use a wide viewport by default, while X's responsive layout relies on
      // its viewport meta tag.
      settings.useWideViewPort = true
      settings.loadWithOverviewMode = false

      settings.userAgentString = settings.userAgentString.toBrowserCompatibleUserAgent()

      // AndroidView does not clip the hosted View to Compose layout bounds by default.
      outlineProvider = ViewOutlineProvider.BOUNDS
      clipToOutline = true

      // Keep the complete gesture stream inside the WebView. Without this, the
      // surrounding Compose navigation drawer can intercept a drag that contains
      // a small horizontal component and cancel X's vertical scroll. X keeps an
      // explicit menu button for opening the app drawer, so swipe-to-open is not
      // required while the gesture starts on the WebView.
      setOnTouchListener { view, event ->
        parentTouchInterceptionRequest(event.actionMasked)?.let { disallowIntercept ->
          view.parent?.requestDisallowInterceptTouchEvent(disallowIntercept)
        }
        false
      }

      webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
          val url = request.url.toString()
          if (!shouldOpenXNavigationExternally(url, request.isForMainFrame)) return false

          runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
          }.onFailure {
            scope.launch {
              snackbarHostState.showSnackbar("外部リンクをブラウザで開けませんでした")
            }
          }
          return true
        }

        override fun onPageFinished(view: WebView, url: String) {
          super.onPageFinished(view, url)
          if (url.isXUrl()) {
            currentUrl = url
            val customization = repository.load()
            view.injectCss(customization.cssForInjection())
            view.injectJavaScript(customization.javaScriptForInjection())
          } else {
            pickerActive = false
          }
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
          rendererLifecycle.gone = true
          pickerActive = false
          if (detail.didCrash()) {
            currentUrl = X_HOME_URL
            rendererCrashed = true
          } else {
            rendererGeneration += 1
            scope.launch {
              snackbarHostState.showSnackbar("Web 表示プロセスがメモリ不足で終了したため再読み込みしました")
            }
          }
          return true
        }
      }
    }.also { view ->
      // Android WebView disables third-party cookies by default for modern target SDKs.
      // X's login flow can cross origins, so enable them only for this dedicated WebView.
      CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)

      // Do not start X while the WebView still has a zero or provisional size.
      view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
        override fun onLayoutChange(
          changedView: View,
          left: Int,
          top: Int,
          right: Int,
          bottom: Int,
          oldLeft: Int,
          oldTop: Int,
          oldRight: Int,
          oldBottom: Int,
        ) {
          if (right <= left || bottom <= top) return
          changedView.removeOnLayoutChangeListener(this)
          if (view.url == null) {
            view.loadUrl(initialUrl)
          }
        }
      })
    }
  }

  DisposableEffect(webView) {
    onDispose {
      if (!rendererLifecycle.gone) {
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
      }
      webView.destroy()
    }
  }

  Box(modifier = modifier) {
    AndroidView(
      factory = { webView },
      modifier = Modifier
        .fillMaxSize()
        .padding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Top).asPaddingValues(),
        ),
    )

    Surface(
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.End),
        )
        .padding(end = 12.dp, bottom = 76.dp)
        .widthIn(max = 520.dp),
      shape = MaterialTheme.shapes.large,
      color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
      tonalElevation = 6.dp,
    ) {
      if (pickerActive) {
        Row(
          modifier = Modifier.padding(horizontal = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          TextButton(
            onClick = {
              webView.cancelElementPicker()
              pickerActive = false
            },
          ) {
            Text("キャンセル")
          }
          TextButton(
            onClick = {
              webView.takeSelectedElementSelector { selector ->
                pickerActive = false
                if (selector == null) {
                  scope.launch {
                    snackbarHostState.showSnackbar("要素を安全に一意識別できませんでした。別の要素を選択してください")
                  }
                  return@takeSelectedElementSelector
                }

                val savedSettings = repository.load()
                if (!savedSettings.enabled) {
                  scope.launch {
                    snackbarHostState.showSnackbar("カスタム CSS が無効です。設定から有効にしてください")
                  }
                  return@takeSelectedElementSelector
                }

                val updatedSettings = savedSettings.copy(
                  css = appendHiddenElementRule(savedSettings.css, selector),
                )
                repository.save(updatedSettings)
                webView.injectCss(updatedSettings.cssForInjection())
                scope.launch {
                  snackbarHostState.showSnackbar("選択した要素を非表示にしました")
                }
              }
            },
          ) {
            Text("選択した要素を非表示")
          }
        }
      } else {
        TextButton(
          onClick = {
            val settings = repository.load()
            when {
              !settings.enabled -> scope.launch {
                snackbarHostState.showSnackbar("カスタム CSS が無効です。設定から有効にしてください")
              }

              webView.url?.isXUrl() != true -> scope.launch {
                snackbarHostState.showSnackbar("X のページを表示しているときだけ利用できます")
              }

              else -> webView.startElementPicker { started ->
                if (started) {
                  pickerActive = true
                  scope.launch {
                    snackbarHostState.showSnackbar("非表示にする要素をタップし、右下のボタンで確定してください")
                  }
                } else {
                  scope.launch {
                    snackbarHostState.showSnackbar("要素選択モードを開始できませんでした")
                  }
                }
              }
            }
          },
        ) {
          Text("要素を非表示")
        }
      }
    }

    SnackbarHost(
      hostState = snackbarHostState,
      modifier = Modifier
        .align(Alignment.TopCenter)
        .windowInsetsPadding(
          WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        )
        .padding(horizontal = 12.dp, vertical = 8.dp),
    )
  }
}

private class XWebViewRendererLifecycle {
  var gone: Boolean = false
}

internal fun String.toBrowserCompatibleUserAgent(): String =
  replace(WEBVIEW_USER_AGENT_MARKER, "")
    .replace(WEBVIEW_VERSION_TOKEN, "")

internal fun parentTouchInterceptionRequest(actionMasked: Int): Boolean? = when (actionMasked) {
  MotionEvent.ACTION_DOWN -> true
  MotionEvent.ACTION_UP,
  MotionEvent.ACTION_CANCEL -> false
  else -> null
}

internal fun shouldOpenXNavigationExternally(url: String, isForMainFrame: Boolean): Boolean {
  if (!isForMainFrame) return false
  val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
  val scheme = uri.scheme?.lowercase() ?: return false
  if (scheme != "http" && scheme != "https") return false
  return !url.isXUrl()
}

internal fun isPersistableElementPickerSelector(selector: String): Boolean {
  val normalizedSelector = selector.trim()
  return normalizedSelector.isNotEmpty() &&
    !normalizedSelector.contains(":nth-of-type(", ignoreCase = true)
}

internal fun appendHiddenElementRule(css: String, selector: String): String {
  val normalizedSelector = selector.trim()
  if (!isPersistableElementPickerSelector(normalizedSelector)) return css

  val rule = "$normalizedSelector {\n  display: none !important;\n}"
  if (css.contains(rule)) return css

  return buildString {
    val existingCss = css.trimEnd()
    if (existingCss.isNotEmpty()) {
      append(existingCss)
      append("\n\n")
    }
    append("/* Added from X element picker */\n")
    append(rule)
    append('\n')
  }
}

internal fun decodeElementPickerSelectorResult(result: String?): String? {
  if (result == null || result == "null" || result == "undefined") return null
  val encoded = result.removeSurrounding("\"")
  if (encoded.isBlank()) return null
  return URLDecoder.decode(encoded, StandardCharsets.UTF_8.toString())
    .takeIf(::isPersistableElementPickerSelector)
}

private fun String.isXUrl(): Boolean {
  val host = runCatching { Uri.parse(this).host }.getOrNull()?.lowercase() ?: return false
  return host == "x.com" || host.endsWith(".x.com") || host == "twitter.com" || host.endsWith(".twitter.com")
}

private fun WebView.injectCss(css: String) {
  val cssLiteral = JSONObject.quote(css)
  evaluateJavascript(
    """
      (() => {
        const styleId = '$X_CUSTOM_CSS_STYLE_ID';
        let style = document.getElementById(styleId);
        if (!style) {
          style = document.createElement('style');
          style.id = styleId;
          document.head.appendChild(style);
        }
        style.textContent = $cssLiteral;
      })();
    """.trimIndent(),
    null,
  )
}

private fun WebView.injectJavaScript(javaScript: String) {
  if (javaScript.isBlank()) return
  evaluateJavascript(javaScript, null)
}

private fun WebView.startElementPicker(onResult: (Boolean) -> Unit) {
  if (url?.isXUrl() != true) {
    onResult(false)
    return
  }

  evaluateJavascript(
    """
      (() => {
        const stateKey = '$X_ELEMENT_PICKER_STATE_KEY';
        const styleId = '$X_ELEMENT_PICKER_STYLE_ID';
        const selectedAttribute = '$X_ELEMENT_PICKER_SELECTED_ATTRIBUTE';
        const existingState = window[stateKey];
        if (existingState && existingState.active) return true;
        if (existingState && typeof existingState.stop === 'function') existingState.stop();

        const pickerStyle = document.createElement('style');
        pickerStyle.id = styleId;
        pickerStyle.textContent =
          '[' + selectedAttribute + '="true"] {' +
          ' outline: 3px solid #ff9800 !important;' +
          ' outline-offset: 2px !important;' +
          '}';
        document.getElementById(styleId)?.remove();
        document.head.appendChild(pickerStyle);

        const state = {
          active: true,
          selected: null,
          selector: null,
          handler: null,
          stop: null,
        };

        const escapeAttributeValue = (value) => String(value)
          .replace(/\\/g, '\\\\')
          .replace(/"/g, '\\"');

        const attributeSelector = (element, name) => {
          const value = element.getAttribute(name);
          if (!value) return null;
          return '[' + name + '="' + escapeAttributeValue(value) + '"]';
        };

        const directCandidates = (element) => {
          const candidates = [];
          const tag = element.tagName.toLowerCase();
          const href = attributeSelector(element, 'href');
          const testId = attributeSelector(element, 'data-testid');
          const ariaLabel = attributeSelector(element, 'aria-label');
          const role = attributeSelector(element, 'role');
          const name = attributeSelector(element, 'name');
          const title = attributeSelector(element, 'title');
          if (href) candidates.push(tag + href);
          if (testId) candidates.push(tag + testId, testId);
          if (role && ariaLabel) candidates.push(tag + role + ariaLabel, role + ariaLabel);
          if (ariaLabel) candidates.push(tag + ariaLabel, ariaLabel);
          if (role) candidates.push(tag + role, role);
          if (name) candidates.push(tag + name, name);
          if (title) candidates.push(tag + title, title);
          return Array.from(new Set(candidates));
        };

        const scopeBaseCandidates = (element) => {
          const candidates = [];
          const tag = element.tagName.toLowerCase();
          const testId = attributeSelector(element, 'data-testid');
          const ariaLabel = attributeSelector(element, 'aria-label');
          const role = attributeSelector(element, 'role');
          if (testId) candidates.push(tag + testId);
          if (role && ariaLabel) candidates.push(tag + role + ariaLabel);
          if (ariaLabel) candidates.push(tag + ariaLabel);
          if (role) candidates.push(tag + role);
          candidates.push(tag);
          return Array.from(new Set(candidates));
        };

        const uniqueMatchFor = (selector) => {
          try {
            const matches = document.querySelectorAll(selector);
            return matches.length === 1 ? matches[0] : null;
          } catch (_) {
            return null;
          }
        };

        const uniquelySelects = (selector, element) => uniqueMatchFor(selector) === element;

        const isRepeatableBoundary = (element) => element.matches(
          'article[data-testid="tweet"], [data-testid="cellInnerDiv"], [data-testid="UserCell"]'
        );

        const closestSemanticBoundary = (element) => element.closest(
          'article[data-testid="tweet"], [data-testid="cellInnerDiv"], [data-testid="UserCell"], ' +
          '[data-testid="sidebarColumn"], nav, header, aside'
        );

        const hrefStrength = (href) => {
          if (/^\/[A-Za-z0-9_]+\/status\/\d+/.test(href)) return 3;
          if (/^\/[A-Za-z0-9_]+\/?(?:[?#].*)?$/.test(href)) return 2;
          return 0;
        };

        const supportsHas = (() => {
          try {
            return typeof CSS !== 'undefined' &&
              typeof CSS.supports === 'function' &&
              CSS.supports('selector(:has(*))');
          } catch (_) {
            return false;
          }
        })();

        const boundarySelectorFor = (boundary) => {
          if (!isRepeatableBoundary(boundary)) {
            for (const candidate of directCandidates(boundary)) {
              if (uniquelySelects(candidate, boundary)) return candidate;
            }
          }

          if (!supportsHas) return null;

          const anchors = Array.from(boundary.querySelectorAll('a[href]'))
            .map((anchor) => ({ anchor, strength: hrefStrength(anchor.getAttribute('href') || '') }))
            .filter((entry) => entry.strength > 0)
            .sort((left, right) => right.strength - left.strength);

          for (const { anchor } of anchors) {
            const href = attributeSelector(anchor, 'href');
            if (!href) continue;
            const anchorSelector = 'a' + href;
            for (const base of scopeBaseCandidates(boundary)) {
              const candidate = base + ':has(' + anchorSelector + ')';
              if (uniquelySelects(candidate, boundary)) return candidate;
            }
          }
          return null;
        };

        const stableSegment = (element) => {
          const candidates = directCandidates(element);
          return candidates.length > 0 ? candidates[0] : element.tagName.toLowerCase();
        };

        const selectorWithin = (boundary, boundarySelector, element) => {
          if (boundary === element) return boundarySelector;

          for (const candidate of directCandidates(element)) {
            const scopedCandidate = boundarySelector + ' ' + candidate;
            if (uniquelySelects(scopedCandidate, element)) return scopedCandidate;
          }

          const parts = [];
          let current = element;
          for (let depth = 0; current && current !== boundary && depth < 8; depth += 1) {
            parts.unshift(stableSegment(current));
            const candidate = boundarySelector + ' ' + parts.join(' > ');
            if (uniquelySelects(candidate, element)) return candidate;
            current = current.parentElement;
          }
          return null;
        };

        const uniqueAncestorScope = (element) => {
          let current = element.parentElement;
          for (let depth = 0; current && current !== document.documentElement && depth < 8; depth += 1) {
            for (const candidate of directCandidates(current)) {
              if (uniquelySelects(candidate, current)) {
                return { element: current, selector: candidate };
              }
            }
            current = current.parentElement;
          }
          return null;
        };

        const selectorFor = (element) => {
          const semanticBoundary = closestSemanticBoundary(element);
          if (semanticBoundary) {
            const boundarySelector = boundarySelectorFor(semanticBoundary);
            if (boundarySelector) {
              const scopedSelector = selectorWithin(semanticBoundary, boundarySelector, element);
              if (scopedSelector) return scopedSelector;
            }
            if (isRepeatableBoundary(semanticBoundary)) return null;
          }

          for (const candidate of directCandidates(element)) {
            if (uniquelySelects(candidate, element)) return candidate;
          }

          const ancestorScope = uniqueAncestorScope(element);
          if (ancestorScope) {
            return selectorWithin(ancestorScope.element, ancestorScope.selector, element);
          }
          return null;
        };

        const clearSelection = () => {
          if (state.selected) {
            state.selected.removeAttribute(selectedAttribute);
          }
          state.selected = null;
          state.selector = null;
        };

        state.handler = (event) => {
          if (!state.active || !(event.target instanceof Element)) return;
          event.preventDefault();
          event.stopPropagation();
          event.stopImmediatePropagation();

          const preferredTarget = event.target.closest(
            'a[href], button, [role="button"], [data-testid], article, section, nav, aside, img, video'
          );
          const target = preferredTarget || event.target.closest('span, div');
          if (!target || target === document.body || target === document.documentElement) return;

          clearSelection();
          state.selected = target;
          state.selector = selectorFor(target);
          target.setAttribute(selectedAttribute, 'true');
        };

        state.stop = () => {
          state.active = false;
          document.removeEventListener('click', state.handler, true);
          clearSelection();
          document.getElementById(styleId)?.remove();
        };

        document.addEventListener('click', state.handler, true);
        window[stateKey] = state;
        return true;
      })();
    """.trimIndent(),
  ) { result ->
    onResult(result == "true")
  }
}

private fun WebView.cancelElementPicker() {
  evaluateJavascript(
    """
      (() => {
        const stateKey = '$X_ELEMENT_PICKER_STATE_KEY';
        const state = window[stateKey];
        if (state && typeof state.stop === 'function') state.stop();
        window[stateKey] = null;
      })();
    """.trimIndent(),
    null,
  )
}

private fun WebView.takeSelectedElementSelector(onResult: (String?) -> Unit) {
  evaluateJavascript(
    """
      (() => {
        const stateKey = '$X_ELEMENT_PICKER_STATE_KEY';
        const state = window[stateKey];
        if (!state) return null;
        let selector = state.selector || null;
        if (selector && state.selected) {
          try {
            const matches = document.querySelectorAll(selector);
            if (matches.length !== 1 || matches[0] !== state.selected) selector = null;
          } catch (_) {
            selector = null;
          }
        } else {
          selector = null;
        }
        if (typeof state.stop === 'function') state.stop();
        window[stateKey] = null;
        return selector ? encodeURIComponent(selector) : null;
      })();
    """.trimIndent(),
  ) { result ->
    onResult(decodeElementPickerSelectorResult(result))
  }
}
