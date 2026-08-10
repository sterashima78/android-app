package dev.terashima.yomitorirss.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeDecisionTest {
  @Test
  fun `閾値未満のスワイプは確定しない`() {
    assertEquals(SwipeCommit.NONE, resolveSwipeCommit(91f, 92f, 176f, true, true, true))
    assertEquals(SwipeCommit.NONE, resolveSwipeCommit(-91f, 92f, 176f, true, true, true))
  }

  @Test
  fun `左右の通常操作を判定する`() {
    assertEquals(SwipeCommit.LEFT, resolveSwipeCommit(-92f, 92f, 176f, true, true, true))
    assertEquals(SwipeCommit.RIGHT, resolveSwipeCommit(92f, 92f, 176f, true, true, true))
  }

  @Test
  fun `大きな右スワイプはfar rightを優先する`() {
    assertEquals(SwipeCommit.FAR_RIGHT, resolveSwipeCommit(176f, 92f, 176f, true, true, true))
  }

  @Test
  fun `far rightだけがある場合も大きな右スワイプを判定する`() {
    assertEquals(SwipeCommit.FAR_RIGHT, resolveSwipeCommit(200f, 92f, 176f, false, false, true))
  }

  @Test
  fun `存在しない方向の操作は確定しない`() {
    assertEquals(SwipeCommit.NONE, resolveSwipeCommit(-200f, 92f, 176f, false, true, false))
    assertEquals(SwipeCommit.NONE, resolveSwipeCommit(200f, 92f, 176f, true, false, false))
  }
}
