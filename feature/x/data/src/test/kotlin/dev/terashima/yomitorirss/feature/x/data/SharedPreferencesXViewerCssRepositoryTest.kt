package dev.terashima.yomitorirss.feature.x.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.feature.x.XViewerCssSettings
import dev.terashima.yomitorirss.feature.x.XViewerDomRule
import dev.terashima.yomitorirss.feature.x.XViewerDomRuleKind
import dev.terashima.yomitorirss.feature.x.XViewerDomTargetKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesXViewerCssRepositoryTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("x_viewer_preferences", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun `未保存ならデフォルトCSSを最初のセットとして読み込む`() {
    val repository = SharedPreferencesXViewerCssRepository(context) { "default-css" }

    val settings = repository.load()

    assertEquals(true, settings.enabled)
    assertEquals(0, settings.activeSetIndex)
    assertEquals("default-css", settings.css)
    assertEquals("", settings.cssAt(1))
    assertTrue(settings.domRules.isEmpty())
  }

  @Test
  fun `保存した有効状態とCSSセットを再読込できる`() {
    val repository = SharedPreferencesXViewerCssRepository(context) { "default-css" }
    val settings = XViewerCssSettings(enabled = false, css = "set-1")
      .copyCurrentCssTo(1)
      .selectSet(1)
      .copy(css = "set-2")

    repository.save(settings)
    val restored = repository.load()

    assertEquals(false, restored.enabled)
    assertEquals(1, restored.activeSetIndex)
    assertEquals("set-2", restored.css)
    assertEquals("set-1", restored.cssAt(0))
  }

  @Test
  fun `DOM表示ルールを保存して再読込できる`() {
    val repository = SharedPreferencesXViewerCssRepository(context) { "default-css" }
    val rule = XViewerDomRule(
      kind = XViewerDomRuleKind.KEEP_MATCHING_ITEMS,
      pagePath = "/home",
      containerSelector = "[data-testid=\"primaryColumn\"] [role=\"tablist\"]",
      itemSelector = "[role=\"tab\"][aria-selected]",
      targetKind = XViewerDomTargetKind.FINGERPRINT_SET,
      targetValue =
        "[{\"kind\":\"TEXT\",\"value\":\"a\"},{\"kind\":\"TEXT\",\"value\":\"b\"}]",
    )

    repository.save(
      XViewerCssSettings(enabled = true, css = "set-1", domRules = listOf(rule)),
    )

    assertEquals(listOf(rule), repository.load().domRules)
  }

  @Test
  fun `旧単一表示ルールは無視する`() {
    val legacyRule =
      "[{\"version\":1,\"kind\":\"KEEP_ONLY_MATCHING_ITEM\",\"pagePath\":\"/home\"," +
        "\"containerSelector\":\"[role=\\\"tablist\\\"]\"," +
        "\"itemSelector\":\"[role=\\\"tab\\\"]\",\"targetKind\":\"TEXT\"," +
        "\"targetValue\":\"Lists\"}]"
    context.getSharedPreferences("x_viewer_preferences", Context.MODE_PRIVATE)
      .edit()
      .putString("dom_rules_v1", legacyRule)
      .commit()
    val repository = SharedPreferencesXViewerCssRepository(context) { "default-css" }

    assertTrue(repository.load().domRules.isEmpty())
  }

  @Test
  fun `旧URL prefix表示ルールは無視する`() {
    val legacyRule =
      "[{\"version\":1,\"kind\":\"KEEP_MATCHING_ITEMS\",\"pagePath\":\"/home\"," +
        "\"containerSelector\":\"[role=\\\"tablist\\\"]\"," +
        "\"itemSelector\":\"[role=\\\"tab\\\"]\",\"targetKind\":\"HREF_PATH_PREFIX\"," +
        "\"targetValue\":\"/i/lists/\"}]"
    context.getSharedPreferences("x_viewer_preferences", Context.MODE_PRIVATE)
      .edit()
      .putString("dom_rules_v1", legacyRule)
      .commit()
    val repository = SharedPreferencesXViewerCssRepository(context) { "default-css" }

    assertTrue(repository.load().domRules.isEmpty())
  }

  @Test
  fun `壊れたDOM表示ルールは無視する`() {
    context.getSharedPreferences("x_viewer_preferences", Context.MODE_PRIVATE)
      .edit()
      .putString("dom_rules_v1", "not-json")
      .commit()
    val repository = SharedPreferencesXViewerCssRepository(context) { "default-css" }

    assertTrue(repository.load().domRules.isEmpty())
  }
}
