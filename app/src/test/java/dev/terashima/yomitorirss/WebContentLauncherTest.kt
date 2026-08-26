package dev.terashima.yomitorirss

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebContentLauncherTest {
  @Test
  fun `https URL は package を固定しない Custom Tab request にする`() {
    val request = requireNotNull(buildWebCustomTabRequest("https://example.com/article"))

    assertEquals(Intent.ACTION_VIEW, request.customTabsIntent.intent.action)
    assertEquals("https://example.com/article", request.uri.toString())
    assertNull(request.customTabsIntent.intent.`package`)
  }

  @Test
  fun `http URL も Custom Tab request にできる`() {
    assertNotNull(buildWebCustomTabRequest("http://example.com/article"))
  }

  @Test
  fun `Web 以外の scheme は Custom Tab request にしない`() {
    assertNull(buildWebCustomTabRequest("mailto:test@example.com"))
    assertNull(buildWebCustomTabRequest("kindle://book/?action=open&asin=B000000000"))
    assertNull(buildWebCustomTabRequest(""))
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
