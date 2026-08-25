package dev.terashima.yomitorirss.core.aicloudopenai

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatGptOpenAiRetryTest {
  @Test
  fun `401 refreshes credentials once and retries the generation once`() = runBlocking {
    val oldAccess = jwt("acct-old")
    val newAccess = jwt("acct-new")
    val store = RetryCredentialStore().apply {
      write(ChatGptCredentials(oldAccess, "refresh-old", 10_000_000L, "acct-old"))
    }
    val http = RetryRecordingHttpClient(
      retryResponse(401, """{"error":{"message":"expired"}}"""),
      retryResponse(200, """{"access_token":"$newAccess","expires_in":3600}"""),
      retryResponse(
        200,
        """data: {"type":"response.output_text.delta","delta":"OK"}

data: {"type":"response.completed","response":{"status":"completed"}}

""",
      ),
    )
    val client = ChatGptOpenAiClient(
      httpClient = http,
      credentialStore = store,
      config = ChatGptProtocolConfig(
        authBaseUrl = "https://auth.test",
        codexBaseUrl = "https://codex.test",
        clientId = "public-client-test",
        originator = "mosaic-test",
      ),
      clockMillis = { 1_000L },
    )

    assertEquals("OK", client.generate("gpt-test", "ping").text)
    assertEquals(3, http.requests.size)
    assertTrue(http.requests[0].url.endsWith("/codex/responses"))
    assertEquals("Bearer $oldAccess", http.requests[0].headers["Authorization"])
    assertTrue(http.requests[1].url.endsWith("/oauth/token"))
    assertTrue(http.requests[2].url.endsWith("/codex/responses"))
    assertEquals("Bearer $newAccess", http.requests[2].headers["Authorization"])
    assertEquals("refresh-old", store.read()!!.refreshToken)
    assertEquals("acct-new", store.read()!!.accountId)
  }
}

private class RetryRecordingHttpClient(vararg responses: HttpResponse) : HttpClient {
  private val responses = ArrayDeque(responses.toList())
  val requests = mutableListOf<HttpRequest>()

  override suspend fun execute(request: HttpRequest): HttpResponse {
    requests += request
    return responses.removeFirst()
  }
}

private class RetryCredentialStore : ChatGptCredentialStore {
  private var credentials: ChatGptCredentials? = null
  override fun read(): ChatGptCredentials? = credentials
  override fun write(credentials: ChatGptCredentials) { this.credentials = credentials }
  override fun clear() { credentials = null }
}

private fun retryResponse(status: Int, body: String): HttpResponse = HttpResponse(
  statusCode = status,
  reasonPhrase = if (status in 200..299) "OK" else "Error",
  finalUrl = "https://test.invalid",
  headers = emptyMap(),
  body = body.toByteArray(StandardCharsets.UTF_8),
)

private fun jwt(accountId: String): String {
  val encoder = Base64.getUrlEncoder().withoutPadding()
  val header = encoder.encodeToString("{}".toByteArray(StandardCharsets.UTF_8))
  val payload = encoder.encodeToString(
    """{"https://api.openai.com/auth":{"chatgpt_account_id":"$accountId"}}""".toByteArray(StandardCharsets.UTF_8),
  )
  return "$header.$payload.signature"
}
