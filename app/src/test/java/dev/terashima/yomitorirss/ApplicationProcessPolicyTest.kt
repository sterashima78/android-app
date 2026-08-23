package dev.terashima.yomitorirss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationProcessPolicyTest {
  @Test
  fun `main process では application runtime を初期化する`() {
    assertTrue(
      shouldInitializeMainProcessRuntime(
        processName = "dev.terashima.yomitorirss",
        packageName = "dev.terashima.yomitorirss",
      ),
    )
  }

  @Test
  fun `local ai vision process では application runtime を初期化しない`() {
    assertFalse(
      shouldInitializeMainProcessRuntime(
        processName = "dev.terashima.yomitorirss:local_ai_vision",
        packageName = "dev.terashima.yomitorirss",
      ),
    )
  }
}
