package dev.terashima.yomitorirss.core.airuntime

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalInferenceSessionTest {
  @Test
  fun `最後のセッション終了後にアイドル解放を予約する`() {
    val scheduler = FakeIdleReleaseScheduler()
    var idleReleaseCount = 0
    val tracker = LocalInferenceSessionTracker(
      idleTimeoutMillis = 300_000,
      onIdle = { idleReleaseCount += 1 },
      scheduler = scheduler,
    )

    val session = tracker.openSession()
    assertEquals(1, tracker.activeSessionCount())

    session.close()

    assertEquals(0, tracker.activeSessionCount())
    assertEquals(1, scheduler.pendingCount())
    assertEquals(0, idleReleaseCount)

    scheduler.runPending()
    assertEquals(1, idleReleaseCount)
  }

  @Test
  fun `アイドル待機中に新しいセッションが始まると解放を取り消す`() {
    val scheduler = FakeIdleReleaseScheduler()
    var idleReleaseCount = 0
    val tracker = LocalInferenceSessionTracker(
      idleTimeoutMillis = 300_000,
      onIdle = { idleReleaseCount += 1 },
      scheduler = scheduler,
    )

    tracker.openSession().close()
    val next = tracker.openSession()

    scheduler.runPending()
    assertEquals(0, idleReleaseCount)
    assertEquals(1, tracker.activeSessionCount())

    next.close()
    scheduler.runPending()
    assertEquals(1, idleReleaseCount)
  }

  @Test
  fun `複数セッションの最後が終了するまでは解放を予約しない`() {
    val scheduler = FakeIdleReleaseScheduler()
    val tracker = LocalInferenceSessionTracker(
      idleTimeoutMillis = 300_000,
      onIdle = {},
      scheduler = scheduler,
    )

    val first = tracker.openSession()
    val second = tracker.openSession()

    first.close()
    assertEquals(1, tracker.activeSessionCount())
    assertEquals(0, scheduler.pendingCount())

    second.close()
    assertEquals(0, tracker.activeSessionCount())
    assertEquals(1, scheduler.pendingCount())
  }

  @Test
  fun `セッションを複数回閉じても参照数は一度だけ減る`() {
    val scheduler = FakeIdleReleaseScheduler()
    val tracker = LocalInferenceSessionTracker(
      idleTimeoutMillis = 300_000,
      onIdle = {},
      scheduler = scheduler,
    )
    val session = tracker.openSession()

    session.close()
    session.close()

    assertEquals(0, tracker.activeSessionCount())
    assertEquals(1, scheduler.pendingCount())
  }

  @Test
  fun `Managerの資源解放相当のclose後も新しいセッションを開始できる`() {
    val scheduler = FakeIdleReleaseScheduler()
    val tracker = LocalInferenceSessionTracker(
      idleTimeoutMillis = 300_000,
      onIdle = {},
      scheduler = scheduler,
    )

    tracker.openSession().close()
    assertEquals(1, scheduler.pendingCount())

    tracker.close()
    assertEquals(0, scheduler.pendingCount())

    val next = tracker.openSession()
    assertEquals(1, tracker.activeSessionCount())
    next.close()
    assertEquals(1, scheduler.pendingCount())
  }
}

private class FakeIdleReleaseScheduler : IdleReleaseScheduler {
  private data class ScheduledAction(
    val action: () -> Unit,
    var cancelled: Boolean = false,
    var completed: Boolean = false,
  )

  private val scheduled = mutableListOf<ScheduledAction>()

  override fun schedule(delayMillis: Long, action: () -> Unit): IdleReleaseHandle {
    val scheduledAction = ScheduledAction(action)
    scheduled += scheduledAction
    return IdleReleaseHandle { scheduledAction.cancelled = true }
  }

  fun pendingCount(): Int = scheduled.count { !it.cancelled && !it.completed }

  fun runPending() {
    scheduled
      .filter { !it.cancelled && !it.completed }
      .forEach { scheduledAction ->
        scheduledAction.completed = true
        scheduledAction.action()
      }
  }
}
