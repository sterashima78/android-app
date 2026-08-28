package dev.terashima.yomitorirss.feature.x

const val X_CSS_SET_COUNT = 3

private fun initialCssSets(css: String, activeSetIndex: Int): List<String> =
  List(X_CSS_SET_COUNT) { index -> if (index == activeSetIndex) css else "" }

enum class XViewerDomRuleKind {
  KEEP_ONLY_MATCHING_ITEM,
}

enum class XViewerDomTargetKind {
  HREF,
  ARIA_LABEL,
  TEXT,
}

data class XViewerDomRule(
  val kind: XViewerDomRuleKind,
  val pagePath: String,
  val containerSelector: String,
  val itemSelector: String,
  val targetKind: XViewerDomTargetKind,
  val targetValue: String,
) {
  init {
    require(pagePath.startsWith('/'))
    require(containerSelector.isNotBlank())
    require(itemSelector.isNotBlank())
    require(targetValue.isNotBlank())
  }

  internal fun scopeKey(): String =
    listOf(kind.name, pagePath, containerSelector, itemSelector).joinToString("\u0000")
}

data class XViewerCssSettings(
  val enabled: Boolean,
  val css: String,
  val activeSetIndex: Int = 0,
  private val cssSets: List<String> = initialCssSets(css, activeSetIndex),
  val domRules: List<XViewerDomRule> = emptyList(),
) {
  init {
    require(cssSets.size == X_CSS_SET_COUNT)
    require(activeSetIndex in 0 until X_CSS_SET_COUNT)
  }

  fun selectSet(index: Int): XViewerCssSettings {
    require(index in 0 until X_CSS_SET_COUNT)
    if (index == activeSetIndex) return this

    val updatedSets = persistedCssSets()
    return XViewerCssSettings(
      enabled = enabled,
      css = updatedSets[index],
      activeSetIndex = index,
      cssSets = updatedSets,
      domRules = domRules,
    )
  }

  fun copyCurrentCssTo(targetSetIndex: Int): XViewerCssSettings {
    require(targetSetIndex in 0 until X_CSS_SET_COUNT)
    if (targetSetIndex == activeSetIndex) return this

    val updatedSets = persistedCssSets().toMutableList().apply {
      this[targetSetIndex] = css
    }
    return copy(cssSets = updatedSets)
  }

  fun cssAt(index: Int): String {
    require(index in 0 until X_CSS_SET_COUNT)
    return if (index == activeSetIndex) css else cssSets[index]
  }

  fun persistedCssSets(): List<String> = cssSets.toMutableList().apply {
    this[activeSetIndex] = css
  }

  fun upsertDomRule(rule: XViewerDomRule): XViewerCssSettings {
    val scopeKey = rule.scopeKey()
    return copy(domRules = domRules.filterNot { it.scopeKey() == scopeKey } + rule)
  }

  fun clearDomRules(): XViewerCssSettings = copy(domRules = emptyList())
}

interface XViewerCssRepository {
  fun load(): XViewerCssSettings

  fun save(settings: XViewerCssSettings)

  fun defaultCss(): String
}

fun XViewerCssSettings.cssForInjection(): String = if (enabled) css else ""

fun XViewerCssSettings.domRulesForInjection(): List<XViewerDomRule> = if (enabled) domRules else emptyList()
