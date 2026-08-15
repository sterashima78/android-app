package dev.terashima.yomitorirss.feature.bookreader.data

import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalCompareTest {
  @Test
  fun `画像名を自然順で比較する`() {
    assertTrue(naturalCompare("2.jpg", "10.jpg") < 0)
    assertTrue(naturalCompare("chapter/009.jpg", "chapter/010.jpg") < 0)
    assertTrue(naturalCompare("A1.jpg", "a2.jpg") < 0)
  }
}
