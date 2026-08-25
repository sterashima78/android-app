package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.summary.SummaryCloudFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    assertNull(rateLimit.cause)

    val unavailable = classifyProviderFailure(
      IllegalStateException("ChatGPT/Codex request failed (503): upstream echoed secret-token"),
    )
    assertEquals(SummaryCloudFailureKind.TRANSIENT, unavailable.kind)
    assertTrue(unavailable.retryable)
    assertFalse(unavailable.message.orEmpty().contains("secret-token"))
    assertNull(unavailable.cause)
  }

  @Test
  fun `transport failure is retryable without carrying transport details`() {
    val transport = classifyTransportFailure()

    assertEquals(SummaryCloudFailureKind.TRANSIENT, transport.kind)
    assertTrue(transport.retryable)
    assertNull(transport.cause)
  }

  @Test
  fun `authentication and request rejection require user action`() {
    val authentication = classifyProviderFailure(IllegalStateException("ChatGPT/Codex request failed (401): token invalid"))
    assertEquals(SummaryCloudFailureKind.AUTHENTICATION, authentication.kind)
    assertFalse(authentication.retryable)
    assertFalse(authentication.message.orEmpty().contains("token invalid"))

    val refreshRejected = classifyProviderFailure(IllegalStateException("ChatGPT OAuth token refresh failed (400)"))
    assertEquals(SummaryCloudFailureKind.AUTHENTICATION, refreshRejected.kind)
    assertFalse(refreshRejected.retryable)

    val badRequest = classifyProviderFailure(IllegalStateException("ChatGPT/Codex request failed (400): private prompt"))
    assertEquals(SummaryCloudFailureKind.REQUEST_REJECTED, badRequest.kind)
    assertFalse(badRequest.retryable)
    assertFalse(badRequest.message.orEmpty().contains("private prompt"))
  }
}
