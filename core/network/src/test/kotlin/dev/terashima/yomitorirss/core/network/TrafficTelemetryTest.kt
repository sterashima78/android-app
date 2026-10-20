package dev.terashima.yomitorirss.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.coroutines.coroutineContext

class TrafficTelemetryTest {
  @Test
  fun `空の通信を無視し診断エラーを隔離する`() {
    val prior = TrafficTelemetry.recorder
    var calls = 0
    try {
      TrafficTelemetry.recorder = {
        calls += 1
        error("diagnostic failure")
      }
      TrafficTelemetry.record("rss", 0, 0)
      assertEquals(0, calls)
      TrafficTelemetry.record("rss", 120, 0)
      assertEquals(1, calls)
    } finally {
      TrafficTelemetry.recorder = prior
    }
  }

  @Test
  fun `機能属性が子コルーチンへ伝播する`() = runBlocking {
    assertNull(coroutineContext[TrafficAttribution])
    withContext(TrafficAttribution("wiki")) {
      assertEquals("wiki", coroutineContext[TrafficAttribution]?.tag)
      withContext(Dispatchers.IO) {
        assertEquals("wiki", coroutineContext[TrafficAttribution]?.tag)
      }
    }
    assertNull(coroutineContext[TrafficAttribution])
  }

  @Test
  fun `診断値の負数はゼロへ補正される`() {
    val prior = TrafficTelemetry.recorder
    var value: TrafficTransfer? = null
    try {
      TrafficTelemetry.recorder = { value = it }
      TrafficTelemetry.record("video", 100, -1)
      assertEquals(TrafficTransfer("video", 100, 0), value)
    } finally {
      TrafficTelemetry.recorder = prior
    }
  }
}
