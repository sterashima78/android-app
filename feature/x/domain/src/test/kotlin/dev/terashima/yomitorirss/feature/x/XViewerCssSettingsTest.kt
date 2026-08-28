package dev.terashima.yomitorirss.feature.x

import org.junit.Assert.assertEquals
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
}
