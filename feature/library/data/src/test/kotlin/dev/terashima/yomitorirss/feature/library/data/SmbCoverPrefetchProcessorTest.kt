package dev.terashima.yomitorirss.feature.library.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbCoverPrefetchProcessorTest {
  @Test
  fun `PDF表紙先読みは256MiB以下だけを対象にする`() {
    val limit = 256L * 1024 * 1024

    assertFalse(shouldPrefetchPdf(-1L))
    assertTrue(shouldPrefetchPdf(0L))
    assertTrue(shouldPrefetchPdf(limit))
    assertFalse(shouldPrefetchPdf(limit + 1L))
  }

  @Test
  fun `対象外表示用のファイルサイズを読みやすい単位へ変換する`() {
    assertEquals("512 B", formatSmbBookFileSize(512L))
    assertEquals("1.5 KiB", formatSmbBookFileSize(1536L))
    assertEquals("256.0 MiB", formatSmbBookFileSize(256L * 1024 * 1024))
    assertEquals("1.5 GiB", formatSmbBookFileSize(1536L * 1024 * 1024))
  }

  @Test
  fun `対象外理由にはファイルサイズを付加する`() {
    assertEquals(
      "PDFが256MiBを超えるため自動取得しません（ファイルサイズ: 300.0 MiB）",
      smbCoverPrefetchSkippedReason("PDFが256MiBを超えるため自動取得しません", 300L * 1024 * 1024),
    )
  }
}
