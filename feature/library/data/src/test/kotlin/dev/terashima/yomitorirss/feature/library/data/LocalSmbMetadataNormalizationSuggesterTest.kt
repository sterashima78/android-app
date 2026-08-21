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
  fun `巻数がある場合はシリーズ名も必須とする`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      parseSmbBookMetadataProposal(
        mapOf(
          "title" to "架空本",
          "authors" to emptyList<String>(),
          "seriesPosition" to 12,
        ),
      )
    }

    assertTrue(error.message.orEmpty().contains("seriesName"))
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
  fun `LiteRTのtool call parse failureを構造化出力失敗として判定する`() {
    val parseFailure = IllegalStateException(
      "outer",
      IllegalArgumentException("Failed to parse FC tool calls"),
    )

    assertTrue(parseFailure.isSmbMetadataToolCallParseFailure())
    assertFalse(IllegalStateException("GPU unavailable").isSmbMetadataToolCallParseFailure())
  }

  @Test
  fun `promptは元ファイル名のローマ字情報と巻数を書誌根拠として利用する`() {
    val prompt = buildSmbMetadataNormalizationPrompt("Kakuu_Bouken_Tan_authorA_12.pdf")

    assertTrue(prompt.contains("ローマ字"))
    assertTrue(prompt.contains("著者名"))
    assertTrue(prompt.contains("seriesName"))
    assertTrue(prompt.contains("seriesPosition"))
    assertTrue(prompt.contains("末尾の1〜3桁"))
    assertTrue(prompt.contains("12Kan"))
    assertTrue(prompt.contains("架空冒険譚08.pdf"))
    assertTrue(prompt.contains("Kakuu_Bouken_Tan_authorA_12.pdf"))
    assertTrue(prompt.contains("現在のファイル名末尾の 12 は巻数候補です。"))
    assertTrue(prompt.contains("submit_book_metadata"))
    assertFalse(prompt.contains("JSON Schema"))
    assertFalse(prompt.contains("additionalProperties"))
  }

  @Test
  fun `明示的な1Kan表記は固定promptでも巻数候補として示す`() {
    val prompt = buildSmbMetadataNormalizationPrompt(
      currentFileName = "Kakuu_Bouken_Tan_1Kan_(KakuuSha).pdf",
      promptTemplate = "表紙から書誌を推定してください。",
    )

    assertTrue(prompt.contains("明示的な巻数表現があり、巻数候補は 1 です。"))
  }

  @Test
  fun `カスタムpromptでも固定指示で巻数メタデータを保持させる`() {
    val prompt = buildSmbMetadataNormalizationPrompt(
      currentFileName = "架空冒険譚08.pdf",
      promptTemplate = "表紙から書誌を推定してください。",
    )

    assertTrue(prompt.contains("seriesName と seriesPosition を必ず保持してください"))
    assertTrue(prompt.contains("現在のファイル名:\n架空冒険譚08.pdf"))
    assertTrue(prompt.endsWith("submit_book_metadata ツールを1回だけ呼び出してください。"))
  }

  @Test
  fun `カスタムpromptのfileName placeholderを展開し固定tool指示を維持する`() {
    val prompt = buildSmbMetadataNormalizationPrompt(
      currentFileName = "Kakuu_Bouken_Tan_03.pdf",
      promptTemplate = "ファイル名 {{fileName}} と表紙を照合してください。",
    )

    assertTrue(prompt.startsWith("ファイル名 Kakuu_Bouken_Tan_03.pdf と表紙を照合してください。"))
    assertTrue(prompt.endsWith("submit_book_metadata ツールを1回だけ呼び出してください。"))
  }

  @Test
  fun `fileName placeholderがないカスタムpromptには現在名と固定tool指示を追記する`() {
    val prompt = buildSmbMetadataNormalizationPrompt(
      currentFileName = "Kakuu_Bouken_Tan_03.pdf",
      promptTemplate = "表紙を中心に書誌を推定してください。",
    )

    assertTrue(prompt.startsWith("表紙を中心に書誌を推定してください。"))
    assertTrue(prompt.contains("現在のファイル名:\nKakuu_Bouken_Tan_03.pdf"))
    assertTrue(prompt.endsWith("submit_book_metadata ツールを1回だけ呼び出してください。"))
  }

  @Test
  fun `タイトルに一致する末尾番号から欠落したシリーズ情報を補完する`() {
    val result = completeSmbSeriesMetadataFromFileName(
      currentFileName = "架空冒険譚08.pdf",
      proposal = SmbBookMetadataProposal(title = "架空冒険譚"),
    )

    assertEquals("架空冒険譚", result.seriesName)
    assertEquals(8, result.seriesPosition)
  }

  @Test
  fun `区切り付き末尾番号から欠落したシリーズ情報を補完する`() {
    val result = completeSmbSeriesMetadataFromFileName(
      currentFileName = "架空冒険譚_０８.pdf",
      proposal = SmbBookMetadataProposal(title = "架空冒険譚"),
    )

    assertEquals("架空冒険譚", result.seriesName)
    assertEquals(8, result.seriesPosition)
  }

  @Test
  fun `明示的な1Kan表記は日本語タイトルでもシリーズ情報を補完する`() {
    val result = completeSmbSeriesMetadataFromFileName(
      currentFileName = "Kakuu_Bouken_Tan_1Kan_(KakuuSha).pdf",
      proposal = SmbBookMetadataProposal(title = "架空冒険譚"),
    )

    assertEquals("架空冒険譚", result.seriesName)
    assertEquals(1, result.seriesPosition)
  }

  @Test
  fun `タイトル自体に数字を含む場合は末尾番号を巻数として補完しない`() {
    val result = completeSmbSeriesMetadataFromFileName(
      currentFileName = "架空規格11.pdf",
      proposal = SmbBookMetadataProposal(title = "架空規格 11"),
    )

    assertNull(result.seriesName)
    assertNull(result.seriesPosition)
  }

  @Test
  fun `既存のシリーズ情報はファイル名ヒューリスティックで上書きしない`() {
    val result = completeSmbSeriesMetadataFromFileName(
      currentFileName = "架空冒険譚08.pdf",
      proposal = SmbBookMetadataProposal(
        title = "架空冒険譚",
        seriesName = "架空冒険譚",
        seriesPosition = 7,
      ),
    )

    assertEquals("架空冒険譚", result.seriesName)
    assertEquals(7, result.seriesPosition)
  }

  @Test
  fun `巻数があれば第n巻形式でファイル名へ付与する`() {
    val result = normalizedSmbBookFileName(
      originalFileName = "Kakuu_Bouken_Tan_12.CBZ",
      proposal = SmbBookMetadataProposal(
        title = "架空冒険譚",
        seriesName = "架空冒険譚",
        seriesPosition = 12,
      ),
    )

    assertEquals("架空冒険譚 第12巻.cbz", result)
  }

  @Test
  fun `元ファイル名の第n巻表記も正規化後は第n巻形式にする`() {
    val result = normalizedSmbBookFileName(
      originalFileName = "Kakuu_Bouken_Tan_第3巻.CBZ",
      proposal = SmbBookMetadataProposal(
        title = "架空冒険譚",
        seriesName = "架空冒険譚",
        seriesPosition = 3,
      ),
    )

    assertEquals("架空冒険譚 第3巻.cbz", result)
  }

  @Test
  fun `元ファイル名の巻数表記が推定巻数と異なる場合は推定巻数を使う`() {
    val result = normalizedSmbBookFileName(
      originalFileName = "Kakuu_Bouken_Tan_第2巻.CBZ",
      proposal = SmbBookMetadataProposal(
        title = "架空冒険譚",
        seriesName = "架空冒険譚",
        seriesPosition = 3,
      ),
    )

    assertEquals("架空冒険譚 第3巻.cbz", result)
  }

  @Test
  fun `元ファイル名がVol表記でも第n巻形式へ正規化する`() {
    val result = normalizedSmbBookFileName(
      originalFileName = "Kakuu_Bouken_Tan_Vol.03.pdf",
      proposal = SmbBookMetadataProposal(
        title = "架空冒険譚",
        seriesName = "架空冒険譚",
        seriesPosition = 3,
      ),
    )

    assertEquals("架空冒険譚 第3巻.pdf", result)
  }

  @Test
  fun `長いタイトルでも巻数ラベルを切り捨てない`() {
    val result = normalizedSmbBookFileName(
      originalFileName = "scan_012.cbz",
      proposal = SmbBookMetadataProposal(
        title = "架".repeat(240),
        seriesName = "架空シリーズ",
        seriesPosition = 12,
      ),
    )

    assertTrue(result.endsWith(" 第12巻.cbz"))
    assertTrue(result.length <= 240)
  }

  @Test
  fun `正規化ファイル名は危険文字を除去し元拡張子を維持する`() {
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