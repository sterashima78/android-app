package dev.terashima.yomitorirss.feature.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbMetadataNormalizationPromptEditPolicyTest {
  @Test
  fun `解析待ちが残っていてもプロンプトを編集できる`() {
    val state = LibraryUiState(
      smbMetadataNormalization = SmbMetadataNormalizationBatchSnapshot(
        batchId = "batch-1",
        status = SmbMetadataNormalizationBatchStatus.RUNNING,
        items = listOf(
          SmbMetadataNormalizationItem(
            batchId = "batch-1",
            sourceId = "source-1",
            originalFileName = "sample-book.pdf",
            inputSize = 1L,
            inputModifiedAt = 1L,
            status = SmbMetadataNormalizationStatus.QUEUED,
            updatedAtEpochMillis = 1L,
          ),
        ),
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L,
      ),
    )

    assertTrue(state.canEditSmbMetadataNormalizationPrompt())
  }

  @Test
  fun `プロンプト設定の更新中は編集できない`() {
    val state = LibraryUiState(smbMetadataNormalizationBusy = true)

    assertFalse(state.canEditSmbMetadataNormalizationPrompt())
  }
}
