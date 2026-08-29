package dev.terashima.yomitorirss.feature.bookreader.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipBookPageFilterTest {
  @Test
  fun `通常の画像エントリを表示対象にする`() {
    assertTrue(isDisplayableZipImageEntry("chapter/001.jpg"))
    assertTrue(isDisplayableZipImageEntry("chapter/002.JPEG"))
    assertTrue(isDisplayableZipImageEntry("003.png"))
    assertTrue(isDisplayableZipImageEntry("004.webp"))
  }

  @Test
  fun `macOSのメタデータを表示対象から除外する`() {
    assertFalse(isDisplayableZipImageEntry("__MACOSX/chapter/._001.jpg"))
    assertFalse(isDisplayableZipImageEntry("book/__MACOSX/chapter/001.jpg"))
    assertFalse(isDisplayableZipImageEntry("chapter/._001.jpg"))
    assertFalse(isDisplayableZipImageEntry("chapter\\._001.jpg"))
  }

  @Test
  fun `画像ではないエントリを表示対象から除外する`() {
    assertFalse(isDisplayableZipImageEntry("chapter/001.txt"))
    assertFalse(isDisplayableZipImageEntry(".DS_Store"))
  }
}
