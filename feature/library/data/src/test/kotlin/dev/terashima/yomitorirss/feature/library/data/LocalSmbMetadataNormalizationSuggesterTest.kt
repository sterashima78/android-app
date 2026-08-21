package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSmbMetadataNormalizationSuggesterTest {
  @Test
  fun `書誌候補は構造化tool引数を検証する`() {
    val proposal = parseSmbBookMetadataProposal(
      mapOf(
        "title" to "架空の技術書",
        "authors" to listOf("著者A", "著者B"),
        "publisher" to "架空出版社",
        "publishedDate" to "2026-01-01",
        "seriesName" to "架空シリーズ",
        "seriesPosition" to 3.0,
        "confidence" to 0.9,
        "reason" to "表紙とファイル名から判断",
      ),
    )

    assertEquals("架空の技術書", proposal.title)
    assertEquals(listOf("著者A", "著者B"), proposal.authors)
    assertEquals("架空シリーズ", proposal.seriesName)
    assertEquals(3, proposal.seriesPosition)
    assertEquals(0.9f, proposal.confidence)
    assertNull(proposal.isbn10)
    assertNull(proposal.isbn13)
  }

  @Test
  fun `判別できない任意項目はtool引数から省略できる`() {
    val proposal = parseSmbBookMetadataProposal(
      mapOf(
        "title" to "架空本",
        "authors" to emptyList<String>(),
      ),
    )

    assertEquals("架空本", proposal.title)
    assertTrue(proposal.authors.isEmpty())
    assertNull(proposal.publisher)
    assertNull(proposal.seriesPosition)
    assertNull(proposal.confidence)
  }

  @Test
  fun `書誌候補は追加フィールドを拒否する`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      parseSmbBookMetadataProposal(
        mapOf(
          "title" to "架空本",
          "authors" to emptyList<String>(),
          "path" to "secret",
        ),
      )
    }

    assertTrue(error.message.orEmpty().contains("追加フィールド"))
  }

  @Test
  fun `authorsが配列でなければ拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      parseSmbBookMetadataProposal(
        mapOf(
          "title" to "架空本",
          "authors" to "著者A",
        ),
      )
    }
  }

  @Test
  fun `promptはJSON直書きではなく出力toolを要求する`() {
    val prompt = buildSmbMetadataNormalizationPrompt("sample.pdf")

    assertTrue(prompt.contains("submit_book_metadata"))
    assertFalse(prompt.contains("JSON Schema"))
    assertFalse(prompt.contains("additionalProperties"))
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
