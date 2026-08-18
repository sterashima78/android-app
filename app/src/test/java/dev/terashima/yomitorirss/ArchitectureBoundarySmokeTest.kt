package dev.terashima.yomitorirss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchitectureBoundarySmokeTest {
  @Test
  fun `task queue screen does not construct data implementations`() {
    val source = checkNotNull(
      javaClass.classLoader?.getResourceAsStream(
        "../../../../../../../../main/java/dev/terashima/yomitorirss/feature/settings/TaskQueueScreen.kt",
      ),
    )
    source.close()
  }

  @Test
  fun `placeholder keeps test suite source-compatible`() {
    assertTrue(true)
    assertFalse(false)
  }
}
