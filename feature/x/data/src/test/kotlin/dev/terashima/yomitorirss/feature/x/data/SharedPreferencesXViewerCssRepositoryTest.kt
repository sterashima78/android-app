package dev.terashima.yomitorirss.feature.x.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.feature.x.XViewerCssSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
    assertEquals(false, settings.javaScriptEnabled)
    assertEquals("", settings.javaScript)
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
  fun `保存したJavaScript設定を再読込できる`() {
    val repository = SharedPreferencesXViewerCssRepository(context) { "default-css" }
    val settings = XViewerCssSettings(
      enabled = true,
      css = "default-css",
      javaScriptEnabled = true,
      javaScript = "document.body.dataset.reader = 'custom';",
    )

    repository.save(settings)
    val restored = repository.load()

    assertEquals(true, restored.javaScriptEnabled)
    assertEquals("document.body.dataset.reader = 'custom';", restored.javaScript)
  }
}
