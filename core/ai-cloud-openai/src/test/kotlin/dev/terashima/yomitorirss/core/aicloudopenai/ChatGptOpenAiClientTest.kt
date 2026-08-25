package dev.terashima.yomitorirss.core.aicloudopenai

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    assertTrue(http.requests.all { it.maxResponseBytes == 16L * 1024 })
  }

  @Test
  fun `generation uses codex responses endpoint and extracts SSE text`() = runBlocking {
    val store = connectedStore()
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
    assertEquals(16L * 1024 * 1024, request.maxResponseBytes)
    assertEquals(16L * 1024, request.maxErrorResponseBytes)
    val body = (request.body ?: byteArrayOf()).toString(StandardCharsets.UTF_8)
    assertTrue(body.contains("\"model\":\"gpt-test\""))
    assertTrue(body.contains("\"text\":\"ping\""))
    assertTrue(body.contains("\"store\":false"))
    assertFalse(body.contains("\"tools\""))
  }

  @Test
  fun `model discovery uses current web search capability metadata and bounded response`() = runBlocking {
    val store = connectedStore()
    val http = RecordingHttpClient(
      response(
        200,
        """{
          "models": [
            {
              "slug": "gpt-5.6-luna",
              "display_name": "GPT-5.6-Luna",
              "description": "Fast model",
              "context_window": 272000,
              "max_context_window": 1000000,
              "web_search_tool_type": "text",
              "supports_search_tool": false,
              "supported_in_api": true,
              "visibility": "list",
              "priority": 2
            },
            {
              "slug": "hidden-model",
              "display_name": "Hidden",
              "web_search_tool_type": "none",
              "supports_search_tool": true,
              "supported_in_api": false,
              "visibility": "hide",
              "priority": 9
            }
          ]
        }""",
      ),
    )
    val client = client(http, store)

    val models = client.listModels()

    assertEquals(listOf("gpt-5.6-luna", "hidden-model"), models.map(ChatGptModelInfo::id))
    assertTrue(models.first().supportsWebSearch)
    assertFalse(models.last().supportsWebSearch)
    assertTrue(models.first().visibleInPicker)
    assertEquals(272000, models.first().contextWindowTokens)
    val request = http.requests.single()
    assertTrue(request.url.startsWith("https://codex.test/codex/models?client_version="))
    assertEquals("Bearer ${store.read()!!.accessToken}", request.headers["Authorization"])
    assertEquals("acct-test-abcdef", request.headers["chatgpt-account-id"])
    assertEquals(4L * 1024 * 1024, request.maxResponseBytes)
    assertEquals(16L * 1024, request.maxErrorResponseBytes)
  }

  @Test
  fun `web generation requires live web search on target domain and verifies opened page`() = runBlocking {
    val store = connectedStore()
    val http = RecordingHttpClient(
      response(200, """data: {"type":"response.output_item.done","item":{"type":"web_search_call","status":"completed","action":{"type":"open_page","url":"https://example.com/articles/1?ref=search"}}}

data: {"type":"response.output_text.delta","delta":"要約結果"}

data: {"type":"response.completed","response":{"status":"completed"}}

"""),
    )
    val client = client(http, store)

    val result = client.generateWithWebSearch(
      modelId = "gpt-5.6-luna",
      prompt = "指定URLの記事を要約してください。",
      targetUrl = "https://example.com/articles/1",
    )

    assertEquals("要約結果", result.text)
    assertEquals(listOf("https://example.com/articles/1?ref=search"), result.openedUrls)
    val request = http.requests.single()
    assertEquals(16L * 1024 * 1024, request.maxResponseBytes)
    assertEquals(16L * 1024, request.maxErrorResponseBytes)
    val body = (request.body ?: byteArrayOf()).toString(StandardCharsets.UTF_8)
    assertTrue(body.contains("\"type\":\"web_search\""))
    assertTrue(body.contains("\"external_web_access\":true"))
    assertTrue(body.contains("\"allowed_domains\":[\"example.com\"]"))
    assertTrue(body.contains("\"tool_choice\":\"required\""))
    assertTrue(body.contains("\"parallel_tool_calls\":false"))
  }

  @Test(expected = IllegalStateException::class)
  fun `web generation fails when codex did not open requested page`() {
    runBlocking {
      val store = connectedStore()
      val http = RecordingHttpClient(
        response(200, """data: {"type":"response.output_item.done","item":{"type":"web_search_call","status":"completed","action":{"type":"open_page","url":"https://example.com/other"}}}

data: {"type":"response.output_text.delta","delta":"推測要約"}

"""),
      )
      client(http, store).generateWithWebSearch(
        modelId = "gpt-test",
        prompt = "要約",
        targetUrl = "https://example.com/articles/1",
      )
    }
  }

  @Test
  fun `refresh keeps previous refresh token when provider omits rotation`() = runBlocking {
    val store = InMemoryCredentialStore().apply {
      write(ChatGptCredentials(jwt("acct-old"), "refresh-old", 999L, "acct-old"))
    }
    val refreshedAccess = jwt("acct-new")
    val http = RecordingHttpClient(
      response(200, """{"access_token":"$refreshedAccess","expires_in":3600}"""),
      response(200, """data: {"type":"response.output_text.delta","delta":"OK"}

"""),
    )
    val client = client(http, store)

    assertEquals("OK", client.generate("gpt-test", "ping").text)
    assertEquals(2, http.requests.size)
    assertTrue(http.requests.first().url.endsWith("/oauth/token"))
    assertEquals(16L * 1024, http.requests.first().maxResponseBytes)
    assertEquals("refresh-old", store.read()!!.refreshToken)
    assertEquals("acct-new", store.read()!!.accountId)
  }

  private fun connectedStore() = InMemoryCredentialStore().apply {
    write(ChatGptCredentials(jwt("acct-test-abcdef"), "refresh-test", 10_000_000L, "acct-test-abcdef"))
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
