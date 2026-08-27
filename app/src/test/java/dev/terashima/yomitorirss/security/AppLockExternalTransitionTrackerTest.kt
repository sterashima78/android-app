package dev.terashima.yomitorirss.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockExternalTransitionTrackerTest {
  @Test
  fun `Custom Tab 起動がない stop は通常どおりロックする`() {
    val tracker = AppLockExternalTransitionTracker { 1_000L }

    assertTrue(tracker.shouldLockOnStop())
  }

  @Test
  fun `Custom Tab 起動直後の stop は一度だけロック対象外にする`() {
    var elapsedRealtimeMillis = 1_000L
    val tracker = AppLockExternalTransitionTracker { elapsedRealtimeMillis }

    tracker.onCustomTabLaunchStarted()

    assertFalse(tracker.shouldLockOnStop())
    assertTrue(tracker.shouldLockOnStop())
  }

  @Test
  fun `Custom Tab 起動失敗後の stop は通常どおりロックする`() {
    val tracker = AppLockExternalTransitionTracker { 1_000L }

    tracker.onCustomTabLaunchStarted()
    tracker.onCustomTabLaunchFailed()

    assertTrue(tracker.shouldLockOnStop())
  }

  @Test
  fun `古い Custom Tab 起動記録では通常のバックグラウンド移行を抑制しない`() {
    var elapsedRealtimeMillis = 1_000L
    val tracker = AppLockExternalTransitionTracker { elapsedRealtimeMillis }

    tracker.onCustomTabLaunchStarted()
    elapsedRealtimeMillis = 20_000L

    assertTrue(tracker.shouldLockOnStop())
  }
}
