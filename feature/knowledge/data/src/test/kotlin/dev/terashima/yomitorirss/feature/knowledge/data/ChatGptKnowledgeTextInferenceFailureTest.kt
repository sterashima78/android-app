package dev.terashima.yomitorirss.feature.knowledge.data

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderException
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderFailureKind
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeCloudFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptKnowledgeTextInferenceFailureTest {
  @Test fun `rate limit and server failures are retryable without prompt leakage`() {
    val rateLimit = classifyKnowledgeProviderFailure(providerFailure(ChatGptProviderFailureKind.RATE_LIMITED, true, 429))
    assertEquals(KnowledgeCloudFailureKind.RATE_LIMITED, rateLimit.kind); assertTrue(rateLimit.retryable); assertNull(rateLimit.cause)
    val unavailable = classifyKnowledgeProviderFailure(providerFailure(ChatGptProviderFailureKind.TRANSIENT, true, 503))
    assertEquals(KnowledgeCloudFailureKind.TRANSIENT, unavailable.kind); assertTrue(unavailable.retryable); assertNull(unavailable.cause)
  }
  @Test fun `authentication and request rejection require user action`() {
    val authentication = classifyKnowledgeProviderFailure(providerFailure(ChatGptProviderFailureKind.AUTHENTICATION, false, 401))
    assertEquals(KnowledgeCloudFailureKind.AUTHENTICATION, authentication.kind); assertFalse(authentication.retryable)
    val badRequest = classifyKnowledgeProviderFailure(providerFailure(ChatGptProviderFailureKind.REQUEST_REJECTED, false, 400))
    assertEquals(KnowledgeCloudFailureKind.REQUEST_REJECTED, badRequest.kind); assertFalse(badRequest.retryable)
    assertEquals("ChatGPT / Codex にリクエストを受け付けてもらえませんでした (HTTP 400)", badRequest.message)
  }
  private fun providerFailure(kind: ChatGptProviderFailureKind, retryable: Boolean, statusCode: Int? = null) = ChatGptProviderException(kind, retryable, statusCode, "normalized provider failure")
}
