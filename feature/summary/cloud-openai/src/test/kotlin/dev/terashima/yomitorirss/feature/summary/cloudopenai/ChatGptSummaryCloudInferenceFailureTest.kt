package dev.terashima.yomitorirss.feature.summary.cloudopenai

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderException
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderFailureKind
import dev.terashima.yomitorirss.feature.summary.SummaryCloudFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptSummaryCloudInferenceFailureTest {
  @Test fun `provider failure is mapped to summary policy`() {
    val transient = classifySummaryProviderFailure(providerFailure(ChatGptProviderFailureKind.TRANSIENT, true, 503))
    assertEquals(SummaryCloudFailureKind.TRANSIENT, transient.kind); assertTrue(transient.retryable)
    val authentication = classifySummaryProviderFailure(providerFailure(ChatGptProviderFailureKind.AUTHENTICATION, false, 401))
    assertEquals(SummaryCloudFailureKind.AUTHENTICATION, authentication.kind); assertFalse(authentication.retryable)
  }

  @Test fun `web target failure has summary specific safe message`() {
    val failure = classifySummaryProviderFailure(providerFailure(ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED, false))
    assertEquals(SummaryCloudFailureKind.UNKNOWN, failure.kind)
    assertEquals("ChatGPT / Codex が指定した記事URLを開けませんでした", failure.message)
  }

  private fun providerFailure(kind: ChatGptProviderFailureKind, retryable: Boolean, statusCode: Int? = null) =
    ChatGptProviderException(kind, retryable, statusCode, "normalized provider failure")
}
