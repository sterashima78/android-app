package dev.terashima.yomitorirss.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class FarLeftSwipeDecisionTest {
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
  fun `far leftがない既存の左スワイプは互換動作を維持する`() {
    assertEquals(
      SwipeCommit.LEFT,
      resolveSwipeCommit(
        offset = -200f,
        normalThreshold = 92f,
        farThreshold = 176f,
        hasLeft = true,
        hasRight = true,
        hasFarRight = true,
      ),
    )
  }
}
