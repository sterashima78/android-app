package dev.terashima.yomitorirss.feature.library.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SmbBookFileNameTest {
  @Test
  fun `拡張子を省略した名前変更では元の拡張子を維持する`() {
    assertEquals(
      "新しい書名.cbz",
      renamedSmbFileName("books\\元の書名.cbz", "新しい書名"),
    )
  }

  @Test
  fun `同じ拡張子を入力した場合は重複して付与しない`() {
    assertEquals(
      "新しい書名.PDF",
      renamedSmbFileName("books\\元の書名.pdf", "新しい書名.PDF"),
    )
  }

  @Test
  fun `パス区切りを含む名前は拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      renamedSmbFileName("books\\元の書名.zip", "subdir/新しい書名")
    }
  }

  @Test
  fun `空の名前は拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      renamedSmbFileName("books\\元の書名.zip", "   ")
    }
  }
}
