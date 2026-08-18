package dev.terashima.yomitorirss.feature.x

const val X_CSS_SET_COUNT = 3

private fun initialCssSets(css: String, activeSetIndex: Int): List<String> =
  List(X_CSS_SET_COUNT) { index -> if (index == activeSetIndex) css else "" }

data class XViewerCssSettings(
  val enabled: Boolean,
  val css: String,
  val activeSetIndex: Int = 0,
  private val cssSets: List<String> = initialCssSets(css, activeSetIndex),
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
}

interface XViewerCssRepository {
  fun load(): XViewerCssSettings

  fun save(settings: XViewerCssSettings)

  fun defaultCss(): String
}

interface XViewerCssRepositoryProvider {
  val xViewerCssRepository: XViewerCssRepository
}

fun XViewerCssSettings.cssForInjection(): String = if (enabled) css else ""
