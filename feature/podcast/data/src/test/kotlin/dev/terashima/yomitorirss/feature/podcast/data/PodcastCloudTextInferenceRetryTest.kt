package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderException
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptProviderFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastCloudTextInferenceRetryTest {
  @Test
  fun `retryable failureは2秒5秒15秒の待機後に最大3回再試行する`() = runBlocking {
    var attempts = 0
    val delays = mutableListOf<Long>()

    val result = retryPodcastCloudInference(
      sleep = { delays += it },
      jitterMillis = { 0L },
    ) {
      attempts += 1
      if (attempts <= 3) {
        throw providerFailure(ChatGptProviderFailureKind.TRANSIENT, statusCode = 503)
      }
      "generated"
    }

    assertEquals("generated", result)
    assertEquals(4, attempts)
    assertEquals(listOf(2_000L, 5_000L, 15_000L), delays)
  }

  @Test
  fun `rate limitもretryableなら同じbounded retryを使う`() = runBlocking {
    var attempts = 0
    val delays = mutableListOf<Long>()

    val error = runCatching {
      retryPodcastCloudInference(
        retryDelaysMillis = listOf(2_000L),
        sleep = { delays += it },
        jitterMillis = { 0L },
      ) {
        attempts += 1
        throw providerFailure(ChatGptProviderFailureKind.RATE_LIMITED, statusCode = 429)
      }
    }.exceptionOrNull()

    assertTrue(error is ChatGptProviderException)
    assertEquals(2, attempts)
    assertEquals(listOf(2_000L), delays)
  }

  @Test
  fun `non retryable failureは待機せずそのまま返す`() = runBlocking {
    val expected = providerFailure(ChatGptProviderFailureKind.AUTHENTICATION, retryable = false, statusCode = 401)
    var attempts = 0
    val delays = mutableListOf<Long>()

    val actual = runCatching {
      retryPodcastCloudInference(
        sleep = { delays += it },
        jitterMillis = { 0L },
      ) {
        attempts += 1
        throw expected
      }
    }.exceptionOrNull()

    assertSame(expected, actual)
    assertEquals(1, attempts)
    assertTrue(delays.isEmpty())
  }

  @Test
  fun `cancellationは再試行しない`() = runBlocking {
    val expected = CancellationException("cancelled")
    var attempts = 0
    val delays = mutableListOf<Long>()

    val actual = runCatching {
      retryPodcastCloudInference(
        sleep = { delays += it },
        jitterMillis = { 0L },
      ) {
        attempts += 1
        throw expected
      }
    }.exceptionOrNull()

    assertSame(expected, actual)
    assertEquals(1, attempts)
    assertTrue(delays.isEmpty())
  }

  private fun providerFailure(
    kind: ChatGptProviderFailureKind,
    retryable: Boolean = true,
    statusCode: Int?,
  ) = ChatGptProviderException(
    kind = kind,
    retryable = retryable,
    statusCode = statusCode,
    message = "normalized failure",
  )
}
