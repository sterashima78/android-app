package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KindlePersonalDocumentDeepLinkTest {
  @Test
  fun `32文字IDから検証候補を生成する`() {
    val id = "0123456789ABCDEF0123456789ABCDEF"

    val candidates = kindlePersonalDocumentDeepLinkCandidates(id)

    assertEquals(4, candidates.size)
    assertEquals("kindle://book/?action=open&asin=$id", candidates[0].uri)
    assertEquals("kindle://book/?action=open&asin=$id:KindlePDoc", candidates[1].uri)
    assertEquals(
      "kindle://book/?action=open&contentIdentifier=$id:KindlePDoc",
      candidates[2].uri,
    )
    assertEquals(
      "kindle://book/?action=open&contentId=$id:KindlePDoc",
      candidates[3].uri,
    )
  }

  @Test
  fun `IDは空白除去と大文字化をして扱う`() {
    val candidates = kindlePersonalDocumentDeepLinkCandidates(
      " 0123456789abcdef0123456789abcdef ",
    )

    assertEquals(
      "kindle://book/?action=open&asin=0123456789ABCDEF0123456789ABCDEF",
      candidates.single { it.label == "購入本と同じ asin 形式" }.uri,
    )
  }

  @Test
  fun `32文字でないIDは候補を生成しない`() {
    assertTrue(kindlePersonalDocumentDeepLinkCandidates("B0ABCDEFGHI").isEmpty())
    assertTrue(kindlePersonalDocumentDeepLinkCandidates("0123456789ABCDEF").isEmpty())
  }
}
