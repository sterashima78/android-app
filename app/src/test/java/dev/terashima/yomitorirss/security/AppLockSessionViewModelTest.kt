package dev.terashima.yomitorirss.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockSessionViewModelTest {
  @Test
  fun `認証成功後はセッションを解除状態にする`() {
    val session = AppLockSessionViewModel()

    session.unlock()

    assertTrue(session.unlocked)
  }

  @Test
  fun `バックグラウンド移行時はセッションをロック状態に戻す`() {
    val session = AppLockSessionViewModel()
    session.unlock()

    session.lock()

    assertFalse(session.unlocked)
  }
}
