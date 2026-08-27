package dev.terashima.yomitorirss

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = YomitoriApplication::class)
class StartupSmokeTest {
  @Test
  fun `MainActivity が起動して初期 destination を構成できる`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        assertFalse(activity.isFinishing)
      }
    }
  }
}
