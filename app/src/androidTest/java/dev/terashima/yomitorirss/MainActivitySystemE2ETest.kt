package dev.terashima.yomitorirss

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySystemE2ETest {
  @get:Rule
  val activityRule = ActivityScenarioRule(MainActivity::class.java)

  @Test
  fun `UI Automator からアプリのドロワーを操作できる`() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device = UiDevice.getInstance(instrumentation)

    assertTrue(
      "アプリのメニューボタンが表示されませんでした",
      device.wait(Until.hasObject(By.desc("メニュー")), UI_TIMEOUT_MILLIS),
    )

    device.findObject(By.desc("メニュー")).click()

    assertTrue(
      "ナビゲーションドロワーが表示されませんでした",
      device.wait(Until.hasObject(By.text("RSS")), UI_TIMEOUT_MILLIS),
    )
  }

  private companion object {
    const val UI_TIMEOUT_MILLIS = 10_000L
  }
}
