package dev.terashima.yomitorirss.feature.bookmark
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkFolderTest {
  @Test
  fun `systemKindの有無でシステムフォルダを判定する`() {
    assertFalse(folder(null).isSystem)
    assertTrue(folder("read_later").isSystem)
  }

  private fun folder(systemKind: String?) = BookmarkFolder(
    id = "folder-1",
    name = "Folder",
    normalizedName = "folder",
    systemKind = systemKind,
    createdAt = "2026-08-08T00:00:00Z",
  )
}
