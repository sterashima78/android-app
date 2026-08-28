package dev.terashima.yomitorirss.feature.x.data

import android.content.Context
import dev.terashima.yomitorirss.feature.x.X_CSS_SET_COUNT
import dev.terashima.yomitorirss.feature.x.XViewerCssRepository
import dev.terashima.yomitorirss.feature.x.XViewerCssSettings
import dev.terashima.yomitorirss.feature.x.XViewerDomRule
import dev.terashima.yomitorirss.feature.x.XViewerDomRuleKind
import dev.terashima.yomitorirss.feature.x.XViewerDomTargetKind
import org.json.JSONArray
import org.json.JSONObject

private const val X_CUSTOM_CSS_ASSET = "x_viewer.css"
private const val PREFS_NAME = "x_viewer_preferences"
private const val KEY_CSS_ENABLED = "custom_css_enabled"
private const val KEY_ACTIVE_CSS_SET = "active_css_set"
private const val KEY_CUSTOM_CSS_SET_PREFIX = "custom_css_set_"
private const val KEY_DOM_RULES = "dom_rules_v1"
private const val DOM_RULE_VERSION = 1

class SharedPreferencesXViewerCssRepository(
  context: Context,
  private val defaultCssProvider: () -> String = {
    context.assets.open(X_CUSTOM_CSS_ASSET).bufferedReader().use { it.readText() }
  },
) : XViewerCssRepository {
  private val preferences = context.applicationContext.getSharedPreferences(
    PREFS_NAME,
    Context.MODE_PRIVATE,
  )
  private val defaultCssValue: String by lazy(defaultCssProvider)

  override fun load(): XViewerCssSettings {
    val cssSets = List(X_CSS_SET_COUNT) { index ->
      val key = cssSetKey(index)
      when {
        preferences.contains(key) -> preferences.getString(key, "") ?: ""
        index == 0 -> defaultCss()
        else -> ""
      }
    }
    val activeSetIndex = preferences.getInt(KEY_ACTIVE_CSS_SET, 0)
      .coerceIn(0, X_CSS_SET_COUNT - 1)

    return XViewerCssSettings(
      enabled = preferences.getBoolean(KEY_CSS_ENABLED, true),
      css = cssSets[activeSetIndex],
      activeSetIndex = activeSetIndex,
      cssSets = cssSets,
      domRules = decodeDomRules(preferences.getString(KEY_DOM_RULES, null)),
    )
  }

  override fun save(settings: XViewerCssSettings) {
    val editor = preferences.edit()
      .putBoolean(KEY_CSS_ENABLED, settings.enabled)
      .putInt(KEY_ACTIVE_CSS_SET, settings.activeSetIndex)
      .putString(KEY_DOM_RULES, encodeDomRules(settings.domRules))

    settings.persistedCssSets().forEachIndexed { index, css ->
      editor.putString(cssSetKey(index), css)
    }
    editor.apply()
  }

  override fun defaultCss(): String = defaultCssValue

  private fun cssSetKey(index: Int): String = "$KEY_CUSTOM_CSS_SET_PREFIX${index + 1}"
}

internal fun encodeDomRules(rules: List<XViewerDomRule>): String = JSONArray().apply {
  rules.forEach { rule ->
    put(
      JSONObject()
        .put("version", DOM_RULE_VERSION)
        .put("kind", rule.kind.name)
        .put("pagePath", rule.pagePath)
        .put("containerSelector", rule.containerSelector)
        .put("itemSelector", rule.itemSelector)
        .put("targetKind", rule.targetKind.name)
        .put("targetValue", rule.targetValue),
    )
  }
}.toString()

internal fun decodeDomRules(raw: String?): List<XViewerDomRule> {
  if (raw.isNullOrBlank()) return emptyList()

  return runCatching {
    val array = JSONArray(raw)
    buildList {
      for (index in 0 until array.length()) {
        val json = array.optJSONObject(index) ?: continue
        if (json.optInt("version", -1) != DOM_RULE_VERSION) continue
        decodeDomRule(json)?.let(::add)
      }
    }
  }.getOrDefault(emptyList())
}

private fun decodeDomRule(json: JSONObject): XViewerDomRule? = runCatching {
  XViewerDomRule(
    kind = XViewerDomRuleKind.valueOf(json.getString("kind")),
    pagePath = json.getString("pagePath"),
    containerSelector = json.getString("containerSelector"),
    itemSelector = json.getString("itemSelector"),
    targetKind = XViewerDomTargetKind.valueOf(json.getString("targetKind")),
    targetValue = json.getString("targetValue"),
  )
}.getOrNull()
