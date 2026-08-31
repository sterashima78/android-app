package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.MAX_SMB_METADATA_REANALYSIS_CONTEXT_CHARS
import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbMetadataNormalizationReanalysisPromptTest {
  @Test
  fun `再解析では前回結果とユーザ補足を独立再評価の文脈として渡す`() {
    val prompt = buildSmbMetadataNormalizationPrompt(
      currentFileName = "sample_08.cbz",
      previousProposal = SmbBookMetadataProposal(
        title = "架空シリーズ",
        authors = listOf("架空著者"),
        seriesName = "架空シリーズ",
        seriesPosition = 8,
        reason = "前回の解析根拠",
      ),
      supplementalContext = "表紙の英字は副題として扱ってください",
    )

    assertTrue(prompt.contains("前回の解析結果"))
    assertTrue(prompt.contains("架空シリーズ"))
    assertTrue(prompt.contains("表紙の英字は副題として扱ってください"))
    assertTrue(prompt.contains("正解として固定せず"))
    assertTrue(prompt.contains("同じ結果でも構いません"))
    assertTrue(prompt.contains("参考情報としてのみ利用"))
  }

  @Test
  fun `初回解析には再解析コンテキストを追加しない`() {
    val prompt = buildSmbMetadataNormalizationPrompt(currentFileName = "sample.cbz")

    assertFalse(prompt.contains("再解析コンテキスト"))
    assertFalse(prompt.contains("前回の解析結果"))
  }

  @Test
  fun `ユーザ補足は上限を超えると拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      buildSmbMetadataNormalizationPrompt(
        currentFileName = "sample.cbz",
        supplementalContext = "x".repeat(MAX_SMB_METADATA_REANALYSIS_CONTEXT_CHARS + 1),
      )
    }
  }
}
