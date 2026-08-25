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

class ChatGptOpenAiClientTest {
  @Test
  fun `device code login exchanges and stores rotated credentials`() = runBlocking {
    val accessToken = jwt("acct-test-123456")
    val http = RecordingHttpClient(
      response(200, """{"device_auth_id":"device-1","user_code":"CODE-1234","interval":"2"}"""),
      response(200, """{"authorization_code":"auth-code","code_verifier":"verifier"}"""),
      response(200, """{"access_token":"$accessToken","refresh_token":"refresh-1","expires_in":3600}"""),
    )
    val store = InMemoryCredentialStore()
    val client = client(http, store)

    val login = client.startDeviceLogin()
    assertEquals("CODE-1234", login.userCode)
    assertEquals(2L, login.pollIntervalSeconds)
    assertEquals(ChatGptDeviceLoginPollResult.AUTHORIZED, client.pollDeviceLogin(login))

    val stored = requireNotNull(store.read())
    assertEquals("acct-test-123456", stored.accountId)
    assertEquals("refresh-1", stored.refreshToken)
    val form = (http.requests.last().body ?: byteArrayOf()).toString(StandardCharsets.UTF_8)
    assertTrue(form.contains("grant_type=authorization_code"))
    assertTrue(form.contains("redirect_uri=https%3A%2F%2Fauth.test%2Fdeviceauth%2Fcallback"))
  }

  @Test
  fun `generation uses codex responses endpoint and extracts SSE text`() = runBlocking {
    val store = InMemoryCredentialStore().apply {
      write(ChatGptCredentials(jwt("acct-test-abcdef"), "refresh-test", 10_000_000L, "acct-test-abcdef"))
    }
    val http = RecordingHttpClient(
      response(200, """data: {"type":"response.output_text.delta","delta":"接続"}

data: {"type":"response.output_text.delta","delta":"確認"}

data: {"type":"response.completed","response":{"status":"completed"}}

"""),
    )
    val client = client(http, store)

    val result = client.generate("gpt-test", "ping")

    assertEquals("接続確認", result.text)
    val request = http.requests.single()
    assertEquals("https://codex.test/codex/responses", request.url)
    assertEquals("Bearer ${store.read()!!.accessToken}", request.headers["Authorization"])
    assertEquals("acct-test-abcdef", request.headers["chatgpt-account-id"])
    assertEquals("mosaic-test", request.headers["originator"])
    val body = (request.body ?: byteArrayOf()).toString(StandardCharsets.UTF_8)
    assertTrue(body.contains("\"model\":\"gpt-test\""))
    assertTrue(body.contains("\"text\":\"ping\""))
    assertTrue(body.contains("\"store\":false"))
  }

  @Test
  fun `expired access token refreshes once before generation`() = runBlocking {
    val store = InMemoryCredentialStore().apply {
      write(ChatGptCredentials(jwt("acct-old"), "refresh-old", 999L, "acct-old"))
    }
    val refreshedAccess = jwt("acct-new")
    val http = RecordingHttpClient(
      response(200, """{"access_token":"$refreshedAccess","refresh_token":"refresh-new","expires_in":3600}"""),
      response(200, """data: {"type":"response.output_text.delta","delta":"OK"}

"""),
    )
    val client = client(http, store)

    assertEquals("OK", client.generate("gpt-test", "ping").text)
    assertEquals(2, http.requests.size)
    assertTrue(http.requests.first().url.endsWith("/oauth/token"))
    assertEquals("refresh-new", store.read()!!.refreshToken)
    assertEquals("acct-new", store.read()!!.accountId)
  }

  private fun client(http: RecordingHttpClient, store: InMemoryCredentialStore) = ChatGptOpenAiClient(
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
}

private class RecordingHttpClient(vararg responses: HttpResponse) : HttpClient {
  private val responses = ArrayDeque(responses.toList())
  val requests = mutableListOf<HttpRequest>()
  override suspend fun execute(request: HttpRequest): HttpResponse {
    requests += request
    return responses.removeFirst()
  }
}

private class InMemoryCredentialStore : ChatGptCredentialStore {
  private var credentials: ChatGptCredentials? = null
  override fun read(): ChatGptCredentials? = credentials
  override fun write(credentials: ChatGptCredentials) { this.credentials = credentials }
  override fun clear() { credentials = null }
}

private fun response(status: Int, body: String): HttpResponse = HttpResponse(
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
