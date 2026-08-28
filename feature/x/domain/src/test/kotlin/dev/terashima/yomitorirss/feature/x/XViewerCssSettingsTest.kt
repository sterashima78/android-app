package dev.terashima.yomitorirss.feature.x

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XViewerCssSettingsTest {
  @Test
  fun `CSS は3セットまで保持できる`() {
    val settings = XViewerCssSettings(enabled = true, css = "set-1")
      .copyCurrentCssTo(1)
      .selectSet(1)
      .copy(css = "set-2")
      .selectSet(2)
      .copy(css = "set-3")

    assertEquals("set-1", settings.cssAt(0))
    assertEquals("set-2", settings.cssAt(1))
    assertEquals("set-3", settings.cssAt(2))
  }

  @Test
  fun `セットを切り替えても編集中のCSSを保持する`() {
    val settings = XViewerCssSettings(enabled = true, css = "first")
      .copy(css = "edited-first")
      .selectSet(1)
      .copy(css = "second")
      .selectSet(0)

    assertEquals(0, settings.activeSetIndex)
    assertEquals("edited-first", settings.css)
    assertEquals("second", settings.cssAt(1))
  }

  @Test
  fun `現在のセットを別セットへコピーしても選択中セットは変わらない`() {
    val settings = XViewerCssSettings(enabled = true, css = "source")
      .copyCurrentCssTo(2)

    assertEquals(0, settings.activeSetIndex)
    assertEquals("source", settings.css)
    assertEquals("source", settings.cssAt(2))
  }

  @Test
  fun `コピー先に既存CSSがあれば上書きする`() {
    val settings = XViewerCssSettings(enabled = true, css = "source")
      .selectSet(1)
      .copy(css = "destination")
      .selectSet(0)
      .copyCurrentCssTo(1)

    assertEquals("source", settings.cssAt(1))
  }

  @Test
  fun `CSS が無効なら注入文字列は空になる`() {
    val settings = XViewerCssSettings(enabled = false, css = "body { display: none; }")

    assertEquals("", settings.cssForInjection())
  }

  @Test
  fun `同じページとコンテナの表示ルールは新しい選択で置き換える`() {
    val first = keepMatchingRule("/i/lists/")
    val second = keepMatchingRule("/communities/")

    val settings = XViewerCssSettings(enabled = true, css = "")
      .upsertDomRule(first)
      .upsertDomRule(second)

    assertEquals(listOf(second), settings.domRules)
  }

  @Test
  fun `別ページの表示ルールは併存できる`() {
    val homeRule = keepMatchingRule("/i/lists/")
    val exploreRule = keepMatchingRule("/communities/").copy(pagePath = "/explore")

    val settings = XViewerCssSettings(enabled = true, css = "")
      .upsertDomRule(homeRule)
      .upsertDomRule(exploreRule)

    assertEquals(listOf(homeRule, exploreRule), settings.domRules)
  }

  @Test
  fun `カスタマイズ無効時はDOM表示ルールを注入しない`() {
    val settings = XViewerCssSettings(
      enabled = false,
      css = "",
      domRules = listOf(keepMatchingRule("/i/lists/")),
    )

    assertTrue(settings.domRulesForInjection().isEmpty())
  }

  @Test
  fun `DOM表示ルールを一括削除できる`() {
    val settings = XViewerCssSettings(
      enabled = true,
      css = "",
      domRules = listOf(keepMatchingRule("/i/lists/")),
    )

    assertTrue(settings.clearDomRules().domRules.isEmpty())
  }

  private fun keepMatchingRule(target: String) = XViewerDomRule(
    kind = XViewerDomRuleKind.KEEP_MATCHING_ITEMS,
    pagePath = "/home",
    containerSelector = "[role=\"tablist\"]",
    itemSelector = "[role=\"tab\"]",
    targetKind = XViewerDomTargetKind.HREF_PATH_PREFIX,
    targetValue = target,
  )
}
