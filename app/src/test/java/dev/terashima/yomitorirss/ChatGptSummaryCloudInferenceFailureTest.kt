package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderException
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderFailureKind
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
      providerFailure(ChatGptProviderFailureKind.RATE_LIMITED, retryable = true, statusCode = 429),
    )
    assertEquals(SummaryCloudFailureKind.RATE_LIMITED, rateLimit.kind)
    assertTrue(rateLimit.retryable)
    assertNull(rateLimit.cause)

    val unavailable = classifyProviderFailure(
      providerFailure(ChatGptProviderFailureKind.TRANSIENT, retryable = true, statusCode = 503),
    )
    assertEquals(SummaryCloudFailureKind.TRANSIENT, unavailable.kind)
    assertTrue(unavailable.retryable)
    assertNull(unavailable.cause)
  }

  @Test
  fun `authentication and request rejection require user action`() {
    val authentication = classifyProviderFailure(
      providerFailure(ChatGptProviderFailureKind.AUTHENTICATION, retryable = false, statusCode = 401),
    )
    assertEquals(SummaryCloudFailureKind.AUTHENTICATION, authentication.kind)
    assertFalse(authentication.retryable)

    val badRequest = classifyProviderFailure(
      providerFailure(ChatGptProviderFailureKind.REQUEST_REJECTED, retryable = false, statusCode = 400),
    )
    assertEquals(SummaryCloudFailureKind.REQUEST_REJECTED, badRequest.kind)
    assertFalse(badRequest.retryable)
    assertEquals(
      "ChatGPT / Codex にリクエストを受け付けてもらえませんでした (HTTP 400)",
      badRequest.message,
    )
  }

  @Test
  fun `provider固有の接続状態とWeb取得失敗を安全な表示へ変換する`() {
    val disconnected = classifyProviderFailure(
      providerFailure(ChatGptProviderFailureKind.NOT_CONNECTED, retryable = false),
    )
    assertEquals("ChatGPT へ接続してください", disconnected.message)

    val webTarget = classifyProviderFailure(
      providerFailure(ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED, retryable = false),
    )
    assertEquals("ChatGPT / Codex が指定した記事URLを開けませんでした", webTarget.message)
  }

  private fun providerFailure(
    kind: ChatGptProviderFailureKind,
    retryable: Boolean,
    statusCode: Int? = null,
  ) = ChatGptProviderException(
    kind = kind,
    retryable = retryable,
    statusCode = statusCode,
    message = "normalized provider failure",
  )
}
