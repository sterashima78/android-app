package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.knowledge.KnowledgeCloudFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptKnowledgeTextInferenceFailureTest {
  @Test
  fun `rate limit and server failures are retryable without prompt leakage`() {
    val rateLimit = classifyKnowledgeProviderFailure(
      IllegalStateException("ChatGPT/Codex request failed (429): wiki=private-content"),
    )
    assertEquals(KnowledgeCloudFailureKind.RATE_LIMITED, rateLimit.kind)
    assertTrue(rateLimit.retryable)
    assertFalse(rateLimit.message.orEmpty().contains("private-content"))
    assertNull(rateLimit.cause)

    val unavailable = classifyKnowledgeProviderFailure(
      IllegalStateException("ChatGPT/Codex request failed (503): upstream echoed secret-token"),
    )
    assertEquals(KnowledgeCloudFailureKind.TRANSIENT, unavailable.kind)
    assertTrue(unavailable.retryable)
    assertFalse(unavailable.message.orEmpty().contains("secret-token"))
    assertNull(unavailable.cause)
  }

  @Test
  fun `transport failure is retryable without carrying transport details`() {
    val transport = classifyKnowledgeTransportFailure()

    assertEquals(KnowledgeCloudFailureKind.TRANSIENT, transport.kind)
    assertTrue(transport.retryable)
    assertNull(transport.cause)
  }

  @Test
  fun `authentication and request rejection require user action`() {
    val authentication = classifyKnowledgeProviderFailure(
      IllegalStateException("ChatGPT/Codex request failed (401): token invalid"),
    )
    assertEquals(KnowledgeCloudFailureKind.AUTHENTICATION, authentication.kind)
    assertFalse(authentication.retryable)
    assertFalse(authentication.message.orEmpty().contains("token invalid"))

    val refreshRejected = classifyKnowledgeProviderFailure(
      IllegalStateException("ChatGPT OAuth token refresh failed (400)"),
    )
    assertEquals(KnowledgeCloudFailureKind.AUTHENTICATION, refreshRejected.kind)
    assertFalse(refreshRejected.retryable)

    val badRequest = classifyKnowledgeProviderFailure(
      IllegalStateException("ChatGPT/Codex request failed (400): private wiki prompt"),
    )
    assertEquals(KnowledgeCloudFailureKind.REQUEST_REJECTED, badRequest.kind)
    assertFalse(badRequest.retryable)
    assertFalse(badRequest.message.orEmpty().contains("private wiki prompt"))
  }
}
