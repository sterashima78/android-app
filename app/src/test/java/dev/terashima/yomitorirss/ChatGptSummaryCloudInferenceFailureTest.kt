package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.summary.SummaryCloudFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptSummaryCloudInferenceFailureTest {
  @Test
  fun `rate limit and server failures are retryable without provider body leakage`() {
    val rateLimit = classifyProviderFailure(
      IllegalStateException("ChatGPT/Codex request failed (429): prompt=private-article-content"),
    )
    assertEquals(SummaryCloudFailureKind.RATE_LIMITED, rateLimit.kind)
    assertTrue(rateLimit.retryable)
    assertFalse(rateLimit.message.orEmpty().contains("private-article-content"))

    val unavailable = classifyProviderFailure(
      IllegalStateException("ChatGPT/Codex request failed (503): upstream echoed secret-token"),
    )
    assertEquals(SummaryCloudFailureKind.TRANSIENT, unavailable.kind)
    assertTrue(unavailable.retryable)
    assertFalse(unavailable.message.orEmpty().contains("secret-token"))
  }

  @Test
  fun `authentication and request rejection require user action`() {
    val authentication = classifyProviderFailure(IllegalStateException("ChatGPT/Codex request failed (401): token invalid"))
    assertEquals(SummaryCloudFailureKind.AUTHENTICATION, authentication.kind)
    assertFalse(authentication.retryable)
    assertFalse(authentication.message.orEmpty().contains("token invalid"))

    val badRequest = classifyProviderFailure(IllegalStateException("ChatGPT/Codex request failed (400): private prompt"))
    assertEquals(SummaryCloudFailureKind.REQUEST_REJECTED, badRequest.kind)
    assertFalse(badRequest.retryable)
    assertFalse(badRequest.message.orEmpty().contains("private prompt"))
  }
}
