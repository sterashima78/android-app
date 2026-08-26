package dev.terashima.yomitorirss.core.aicloudopenai

import java.io.IOException
import kotlinx.coroutines.CancellationException

enum class ChatGptProviderFailureKind {
  TRANSIENT,
  RATE_LIMITED,
  AUTHENTICATION,
  REQUEST_REJECTED,
  NOT_CONNECTED,
  WEB_TARGET_NOT_OPENED,
  UNKNOWN,
}

class ChatGptProviderException(
  val kind: ChatGptProviderFailureKind,
  val retryable: Boolean,
  val statusCode: Int?,
  message: String,
) : IllegalStateException(message)

/**
 * Inference-facing ChatGPT client that prevents raw transport/provider failures from crossing
 * the core OpenAI adapter boundary.
 */
class ChatGptInferenceClient(
  private val delegate: ChatGptOpenAiClient,
) {
  fun connectionStatus(): ChatGptConnectionStatus = delegate.connectionStatus()

  suspend fun generate(modelId: String, prompt: String): ChatGptGenerationResult = normalizeFailure {
    delegate.generate(modelId, prompt)
  }

  suspend fun generateWithWebSearch(
    modelId: String,
    prompt: String,
    targetUrl: String,
  ): ChatGptWebGenerationResult = normalizeFailure {
    delegate.generateWithWebSearch(modelId, prompt, targetUrl)
  }

  private suspend fun <T> normalizeFailure(block: suspend () -> T): T = try {
    block()
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (_: IOException) {
    throw chatGptTransportFailure()
  } catch (error: IllegalStateException) {
    throw classifyChatGptProviderFailure(error)
  }
}

internal fun chatGptTransportFailure(): ChatGptProviderException = ChatGptProviderException(
  kind = ChatGptProviderFailureKind.TRANSIENT,
  retryable = true,
  statusCode = null,
  message = "ChatGPT / Codex transport failure",
)

internal fun classifyChatGptProviderFailure(error: IllegalStateException): ChatGptProviderException {
  val rawMessage = error.message.orEmpty()
  val status = CHATGPT_HTTP_STATUS_PATTERN.find(rawMessage)
    ?.groupValues?.getOrNull(1)?.toIntOrNull()
  return when {
    rawMessage.contains("not connected", ignoreCase = true) -> ChatGptProviderException(
      kind = ChatGptProviderFailureKind.NOT_CONNECTED,
      retryable = false,
      statusCode = status,
      message = "ChatGPT is not connected",
    )
    rawMessage.contains("did not open the specified article URL", ignoreCase = true) -> ChatGptProviderException(
      kind = ChatGptProviderFailureKind.WEB_TARGET_NOT_OPENED,
      retryable = false,
      statusCode = status,
      message = "ChatGPT / Codex did not open the requested web target",
    )
    status == 429 -> ChatGptProviderException(
      kind = ChatGptProviderFailureKind.RATE_LIMITED,
      retryable = true,
      statusCode = status,
      message = "ChatGPT / Codex rate limited the request (HTTP $status)",
    )
    status == 408 || status != null && status in 500..599 -> ChatGptProviderException(
      kind = ChatGptProviderFailureKind.TRANSIENT,
      retryable = true,
      statusCode = status,
      message = "ChatGPT / Codex is temporarily unavailable (HTTP $status)",
    )
    status == 401 || status == 403 || isRefreshCredentialRejection(rawMessage, status) ->
      ChatGptProviderException(
        kind = ChatGptProviderFailureKind.AUTHENTICATION,
        retryable = false,
        statusCode = status,
        message = "ChatGPT authentication failed${status?.let { " (HTTP $it)" }.orEmpty()}",
      )
    status != null && status in 400..499 -> ChatGptProviderException(
      kind = ChatGptProviderFailureKind.REQUEST_REJECTED,
      retryable = false,
      statusCode = status,
      message = "ChatGPT / Codex rejected the request (HTTP $status)",
    )
    else -> ChatGptProviderException(
      kind = ChatGptProviderFailureKind.UNKNOWN,
      retryable = false,
      statusCode = status,
      message = "ChatGPT / Codex provider failure",
    )
  }
}

private fun isRefreshCredentialRejection(message: String, status: Int?): Boolean =
  status != null && status in 400..499 && message.contains("OAuth token refresh", ignoreCase = true)

private val CHATGPT_HTTP_STATUS_PATTERN = Regex("\\((\\d{3})\\)")
