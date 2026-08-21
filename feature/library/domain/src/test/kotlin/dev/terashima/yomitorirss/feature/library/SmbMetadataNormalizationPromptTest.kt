package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbMetadataNormalizationPromptTest {
  @Test
  fun `fileName placeholderを現在のファイル名へ展開する`() {
    val rendered = renderSmbMetadataNormalizationPrompt(
      template = "対象: {{fileName}}",
      currentFileName = "Kakuu_Shoshi_01.pdf",
    )

    assertEquals("対象: Kakuu_Shoshi_01.pdf", rendered)
  }

  @Test
  fun `placeholderがなければ現在のファイル名を末尾へ追加する`() {
    val rendered = renderSmbMetadataNormalizationPrompt(
      template = "表紙を確認してください。",
      currentFileName = "Kakuu_Shoshi_01.pdf",
    )

    assertTrue(rendered.endsWith("現在のファイル名:\nKakuu_Shoshi_01.pdf"))
  }

  @Test
  fun `空のpromptを拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      normalizeSmbMetadataNormalizationPrompt("   ")
    }
  }

  @Test
  fun `上限を超えるpromptを拒否する`() {
    assertThrows(IllegalArgumentException::class.java) {
      normalizeSmbMetadataNormalizationPrompt(
        "a".repeat(SMB_METADATA_NORMALIZATION_PROMPT_MAX_LENGTH + 1),
      )
    }
  }
}