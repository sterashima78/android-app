package dev.terashima.yomitorirss.feature.web
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanServerStatusTest {
  @Test
  fun `起動から停止まで状態を更新する`() {
    LanServerStatus.stopped()

    LanServerStatus.starting()
    assertTrue(LanServerStatus.state.value.running)
    assertNull(LanServerStatus.state.value.accessUrl)

    LanServerStatus.running("192.168.1.10", "http://192.168.1.10:8765")
    assertEquals("192.168.1.10", LanServerStatus.state.value.address)
    assertEquals("http://192.168.1.10:8765", LanServerStatus.state.value.accessUrl)

    LanServerStatus.stopped("stopped")
    assertFalse(LanServerStatus.state.value.running)
    assertEquals("stopped", LanServerStatus.state.value.error)
  }
}
