package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSmbMetadataNormalizationSuggesterTest {
  @Test
  fun `書誌候補は厳密なJSON契約を検証する`() {
    val proposal = parseSmbBookMetadataProposal(
      """
        {
          "title":"架空の技術書",
          "authors":["著者A","著者B"],
          "publisher":"架空出版社",
          "publishedDate":"2026-01-01",
          "isbn10":null,
          "isbn13":null,
          "seriesName":"架空シリーズ",
          "seriesPosition":3,
          "confidence":0.9,
          "reason":"表紙とファイル名から判断"
        }
      """.trimIndent(),
    )

    assertEquals("架空の技術書", proposal.title)
    assertEquals(listOf("著者A", "著者B"), proposal.authors)
    assertEquals("架空シリーズ", proposal.seriesName)
    assertEquals(3, proposal.seriesPosition)
    assertEquals(0.9f, proposal.confidence)
  }

  @Test
  fun `書誌候補は追加フィールドを拒否する`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      parseSmbBookMetadataProposal(
        """{"title":"架空本","authors":[],"publisher":null,"publishedDate":null,"isbn10":null,"isbn13":null,"seriesName":null,"seriesPosition":null,"confidence":null,"reason":null,"path":"secret"}""",
      )
    }

    assertTrue(error.message.orEmpty().contains("追加フィールド"))
  }

  @Test
  fun `正規化ファイル名は危険文字を除去し巻数と元拡張子を維持する`() {
    val result = normalizedSmbBookFileName(
      originalFileName = "scan_003.CBZ",
      proposal = SmbBookMetadataProposal(
        title = "架空:シリーズ/特別版",
        seriesName = "架空シリーズ",
        seriesPosition = 3,
      ),
    )

    assertEquals("架空 シリーズ 特別版 第3巻.cbz", result)
  }

  @Test
  fun `レビュー編集でも拡張子変更とパス区切りを拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      validateProposedSmbFileName("before.cbz", "after.pdf")
    }
    assertThrows(IllegalArgumentException::class.java) {
      validateProposedSmbFileName("before.cbz", "folder/after.cbz")
    }
  }
}
