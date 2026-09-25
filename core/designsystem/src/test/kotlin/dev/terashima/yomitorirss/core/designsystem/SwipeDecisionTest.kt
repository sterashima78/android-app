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
  fun `大きな左スワイプはfar leftを優先する`() {
    assertEquals(
      SwipeCommit.FAR_LEFT,
      resolveSwipeCommit(
        offset = -176f,
        normalThreshold = 92f,
        farThreshold = 176f,
        hasLeft = true,
        hasRight = true,
        hasFarRight = true,
        hasFarLeft = true,
      ),
    )
  }

  @Test
  fun `far leftだけがある場合も大きな左スワイプを判定する`() {
    assertEquals(
      SwipeCommit.FAR_LEFT,
      resolveSwipeCommit(
        offset = -200f,
        normalThreshold = 92f,
        farThreshold = 176f,
        hasLeft = false,
        hasRight = false,
        hasFarRight = false,
        hasFarLeft = true,
      ),
    )
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
  fun `カード幅基準のfar閾値は固定閾値より小さくしない`() {
    assertEquals(260f, resolveFarThreshold(rowWidth = 400f, fixedThreshold = 176f, fraction = 0.65f), 0.001f)
    assertEquals(176f, resolveFarThreshold(rowWidth = 200f, fixedThreshold = 176f, fraction = 0.65f), 0.001f)
  }

  @Test
  fun `意図的なfar leftでは中程度のスワイプを通常左として扱う`() {
    assertEquals(
      SwipeCommit.LEFT,
      resolveSwipeCommit(
        offset = -176f,
        normalThreshold = 76f,
        farThreshold = 260f,
        hasLeft = true,
        hasRight = true,
        hasFarRight = true,
        hasFarLeft = true,
      ),
    )
    assertEquals(
      SwipeCommit.FAR_LEFT,
      resolveSwipeCommit(
        offset = -260f,
        normalThreshold = 76f,
        farThreshold = 260f,
        hasLeft = true,
        hasRight = true,
        hasFarRight = true,
        hasFarLeft = true,
      ),
    )
  }

  @Test
  fun `far境界手前では押し込み方向だけ抵抗を加える`() {
    assertEquals(
      -13f,
      applyFarTransitionResistance(
        offset = -250f,
        delta = -20f,
        farThreshold = 260f,
        towardNegative = true,
        enabled = true,
      ),
      0.001f,
    )
    assertEquals(
      20f,
      applyFarTransitionResistance(
        offset = -250f,
        delta = 20f,
        farThreshold = 260f,
        towardNegative = true,
        enabled = true,
      ),
      0.001f,
    )
  }

  @Test
  fun `存在しない方向の操作は確定しない`() {
    assertEquals(SwipeCommit.NONE, resolveSwipeCommit(-200f, 92f, 176f, false, true, false))
    assertEquals(SwipeCommit.NONE, resolveSwipeCommit(200f, 92f, 176f, true, false, false))
  }
}
