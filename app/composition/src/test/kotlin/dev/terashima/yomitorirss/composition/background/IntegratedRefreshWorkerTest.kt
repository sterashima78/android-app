package dev.terashima.yomitorirss.composition.background

import org.junit.Assert.assertEquals
import org.junit.Test

class IntegratedRefreshWorkerTest {
  @Test
  fun `更新後に追加された未読だけを新着として扱う`() {
    val before = setOf("article:a", "youtube:b", "mail:c")
    val after = setOf("article:a", "youtube:b", "article:d", "mail:e")

    assertEquals(setOf("article:d", "mail:e"), newUnreadKeys(before, after))
  }

  @Test
  fun `既存未読だけなら新着通知対象は空になる`() {
    val before = setOf("article:a", "youtube:b")
    val after = setOf("article:a", "youtube:b")

    assertEquals(emptySet<String>(), newUnreadKeys(before, after))
  }
}
