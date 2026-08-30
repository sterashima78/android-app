package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmbMetadataNormalizationInferenceProcessTest {
  @Test
  fun `2冊目の完了で vision process を再生成する`() {
    val policy = SmbVisionProcessBatchPolicy(maxItems = 2)

    assertFalse(policy.itemFinished())
    assertTrue(policy.itemFinished())
  }

  @Test
  fun `proposal は Binder 用 Bundle を往復しても情報を保持する`() {
    val proposal = SmbBookMetadataProposal(
      title = "Sample",
      authors = listOf("Author A", "Author B"),
      publisher = "Publisher",
      publishedDate = "2026-08-23",
      isbn10 = "1234567890",
      isbn13 = "1234567890123",
      seriesName = "Series",
      seriesPosition = 8,
      confidence = 0.9f,
      reason = "cover and filename",
    )

    assertEquals(proposal, proposal.toBundle().toProposal())
  }

  @Test(expected = IllegalArgumentException::class)
  fun `batch size は正数だけを受け付ける`() {
    SmbVisionProcessBatchPolicy(maxItems = 0)
  }
}
