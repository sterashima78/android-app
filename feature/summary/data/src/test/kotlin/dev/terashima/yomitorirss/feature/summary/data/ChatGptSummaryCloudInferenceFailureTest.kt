package dev.terashima.yomitorirss.feature.summary.data

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderException
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderFailureKind
import dev.terashima.yomitorirss.feature.summary.SummaryCloudFailureKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptSummaryCloudInferenceFailureTest {
  @Test fun `provider failure is mapped to summary policy`() {
    val transient = classifySummaryProviderFailure(providerFailure(ChatGptProviderFailureKind.TRANSIENT, true, 503))
    assertEquals(SummaryCloudFailureKind.TRANSIENT, transient.kind)
    assertTrue(transient.retryable)
    val authentication = classifySummaryProviderFailure(providerFailure(ChatGptProviderFailureKind.AUTHENTICATION, false, 401))
    assertEquals(SummaryCloudFailureKind.AUTHENTICATION, authentication.kind)
    assertFalse(authentication.retryable)
  }

  @Test fun `web target failure has summary specific safe message`() {
    val failure = classifySummaryProviderFailure(providerFailure(ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED, false))
    assertEquals(SummaryCloudFailureKind.UNKNOWN, failure.kind)
    assertFalse(failure.retryable)
    assertEquals("ChatGPT / Codex が指定した記事URLを複数回試しましたが取得できませんでした", failure.message)
  }

  @Test fun `web target open is retried up to three times`() = runBlocking {
    var attempts = 0
    val result = retryWebTargetOpen(baseDelayMillis = 0L) {
      attempts += 1
      if (attempts < 3) throw providerFailure(ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED, false)
      "ok"
    }
    assertEquals("ok", result)
    assertEquals(3, attempts)
  }

  @Test fun `non web provider failure is not retried`() = runBlocking {
    var attempts = 0
    val failure = runCatching {
      retryWebTargetOpen(baseDelayMillis = 0L) {
        attempts += 1
        throw providerFailure(ChatGptProviderFailureKind.TRANSIENT, true, 503)
      }
    }.exceptionOrNull()
    assertTrue(failure is ChatGptProviderException)
    assertEquals(1, attempts)
  }

  @Test fun `short web fetch failure text is rejected before persistence`() {
    assertTrue(isLikelyWebFetchFailureText("ページを取得できませんでした。"))
    assertTrue(isLikelyWebFetchFailureText("I couldn't access the page."))
    assertTrue(isLikelyWebFetchFailureText("__YOMITORI_WEB_FETCH_FAILED__"))
    assertFalse(isLikelyWebFetchFailureText("記事本文を取得し、主要な論点を3点に整理しました。"))
  }

  @Test fun `long summary mentioning a fetch failure is not rejected`() {
    val summary = "この記事はWeb取得失敗時の設計について解説しています。".repeat(30) + " ページを取得できませんという表示も扱います。"
    assertFalse(isLikelyWebFetchFailureText(summary))
  }

  private fun providerFailure(
    kind: ChatGptProviderFailureKind,
    retryable: Boolean,
    statusCode: Int? = null,
  ) = ChatGptProviderException(kind, retryable, statusCode, "normalized provider failure")
}
