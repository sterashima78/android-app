package dev.terashima.yomitorirss.feature.game

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GodotKlondikeActivityTest {
  @Test
  fun `runtimeのportrait要求を無視してmanifestのlandscapeを維持する`() {
    val activity = Robolectric.buildActivity(GodotKlondikeActivity::class.java).get()

    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
      activity.requestedOrientation,
    )

    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    assertEquals(
      ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
      activity.requestedOrientation,
    )
  }
}
