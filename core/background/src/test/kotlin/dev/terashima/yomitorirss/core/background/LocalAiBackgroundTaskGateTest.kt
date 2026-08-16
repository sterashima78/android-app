package dev.terashima.yomitorirss.core.background

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalAiBackgroundTaskGateTest {
  @Test
  fun `バックグラウンドAI処理は同時に一つだけ実行する`() = runBlocking {
    var active = 0
    var maxActive = 0

    coroutineScope {
      repeat(8) {
        launch(Dispatchers.Default) {
          LocalAiBackgroundTaskGate.withPermit {
            active += 1
            maxActive = maxOf(maxActive, active)
            delay(5)
            active -= 1
          }
        }
      }
    }

    assertEquals(1, maxActive)
  }
}
