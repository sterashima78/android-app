package dev.terashima.yomitorirss.core.background

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

  @Test
  fun `待機中は高優先度タスクを先に実行する`() = runBlocking {
    val firstEntered = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    val executionOrder = mutableListOf<String>()

    val first = launch(start = CoroutineStart.UNDISPATCHED) {
      LocalAiBackgroundTaskGate.withPermit(LocalAiBackgroundTaskPriority.NORMAL) {
        firstEntered.complete(Unit)
        releaseFirst.await()
        executionOrder += "first"
      }
    }
    firstEntered.await()

    val low = launch(start = CoroutineStart.UNDISPATCHED) {
      LocalAiBackgroundTaskGate.withPermit(LocalAiBackgroundTaskPriority.LOW) {
        executionOrder += "low"
      }
    }
    val high = launch(start = CoroutineStart.UNDISPATCHED) {
      LocalAiBackgroundTaskGate.withPermit(LocalAiBackgroundTaskPriority.HIGH) {
        executionOrder += "high"
      }
    }

    releaseFirst.complete(Unit)
    joinAll(first, low, high)

    assertEquals(listOf("first", "high", "low"), executionOrder)
  }

  @Test
  fun `診断ラベルはpermit保持中だけ公開し次のタスクへ切り替える`() = runBlocking {
    val firstEntered = CompletableDeferred<Unit>()
    val releaseFirst = CompletableDeferred<Unit>()
    val secondEntered = CompletableDeferred<Unit>()

    val first = launch(start = CoroutineStart.UNDISPATCHED) {
      LocalAiBackgroundTaskGate.withPermit(diagnosticLabel = "summary") {
        assertEquals("summary", LocalAiBackgroundTaskGate.currentDiagnosticLabel())
        firstEntered.complete(Unit)
        releaseFirst.await()
      }
    }
    firstEntered.await()

    val second = launch(start = CoroutineStart.UNDISPATCHED) {
      LocalAiBackgroundTaskGate.withPermit(diagnosticLabel = "knowledge") {
        assertEquals("knowledge", LocalAiBackgroundTaskGate.currentDiagnosticLabel())
        secondEntered.complete(Unit)
      }
    }

    assertEquals("summary", LocalAiBackgroundTaskGate.currentDiagnosticLabel())
    releaseFirst.complete(Unit)
    secondEntered.await()
    joinAll(first, second)
    assertNull(LocalAiBackgroundTaskGate.currentDiagnosticLabel())
  }
}
