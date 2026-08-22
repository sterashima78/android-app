package dev.terashima.yomitorirss.feature.web.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanWebServerStateStoreTest {
  @Test
  fun `起動から停止まで状態を更新する`() {
    LanWebServerStateStore.stopped()

    LanWebServerStateStore.starting()
    assertTrue(LanWebServerStateStore.state.value.running)
    assertNull(LanWebServerStateStore.state.value.accessUrl)

    LanWebServerStateStore.running(
      "192.168.1.10",
      "http://192.168.1.10:8765",
    )
    assertEquals("192.168.1.10", LanWebServerStateStore.state.value.address)
    assertEquals("http://192.168.1.10:8765", LanWebServerStateStore.state.value.accessUrl)

    LanWebServerStateStore.stopped("stopped")
    assertFalse(LanWebServerStateStore.state.value.running)
    assertEquals("stopped", LanWebServerStateStore.state.value.error)
  }
}
