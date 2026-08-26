package dev.terashima.yomitorirss.core.aicloudopenai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptProviderFailureTest {
  @Test
  fun `HTTP statusをprovider共通failure taxonomyへ正規化する`() {
    val rateLimit = classifyChatGptProviderFailure(
      IllegalStateException("ChatGPT/Codex request failed (429): private prompt fragment"),
    )
    assertEquals(ChatGptProviderFailureKind.RATE_LIMITED, rateLimit.kind)
    assertTrue(rateLimit.retryable)
    assertEquals(429, rateLimit.statusCode)
    assertFalse(rateLimit.message.orEmpty().contains("private prompt fragment"))

    val unavailable = classifyChatGptProviderFailure(
      IllegalStateException("ChatGPT/Codex request failed (503): secret-token"),
    )
    assertEquals(ChatGptProviderFailureKind.TRANSIENT, unavailable.kind)
    assertTrue(unavailable.retryable)
    assertFalse(unavailable.message.orEmpty().contains("secret-token"))

    val rejected = classifyChatGptProviderFailure(
      IllegalStateException("ChatGPT/Codex request failed (400): private article"),
    )
    assertEquals(ChatGptProviderFailureKind.REQUEST_REJECTED, rejected.kind)
    assertFalse(rejected.retryable)
  }

  @Test
  fun `OAuth refreshの4xxは認証失敗として正規化する`() {
    val failure = classifyChatGptProviderFailure(
      IllegalStateException("ChatGPT OAuth token refresh failed (400)"),
    )

    assertEquals(ChatGptProviderFailureKind.AUTHENTICATION, failure.kind)
    assertFalse(failure.retryable)
    assertEquals(400, failure.statusCode)
  }

  @Test
  fun `接続状態とWeb target失敗をraw messageなしで分類する`() {
    val disconnected = classifyChatGptProviderFailure(
      IllegalStateException("ChatGPT is not connected account=private-account"),
    )
    assertEquals(ChatGptProviderFailureKind.NOT_CONNECTED, disconnected.kind)
    assertFalse(disconnected.message.orEmpty().contains("private-account"))

    val webTarget = classifyChatGptProviderFailure(
      IllegalStateException("ChatGPT/Codex did not open the specified article URL https://private.example/path"),
    )
    assertEquals(ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED, webTarget.kind)
    assertFalse(webTarget.message.orEmpty().contains("private.example"))
  }

  @Test
  fun `transport failureはretryableでstatusを持たない`() {
    val failure = chatGptTransportFailure()

    assertEquals(ChatGptProviderFailureKind.TRANSIENT, failure.kind)
    assertTrue(failure.retryable)
    assertNull(failure.statusCode)
  }
}
