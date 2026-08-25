package dev.terashima.yomitorirss.core.aicloudopenai

import android.content.Context
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpMethod
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

const val DEFAULT_CHATGPT_CODEX_MODEL_ID = "gpt-5.6-sol"

private const val CHATGPT_OAUTH_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
private const val DEFAULT_AUTH_BASE_URL = "https://auth.openai.com"
private const val DEFAULT_CODEX_BASE_URL = "https://chatgpt.com/backend-api"
private const val DEVICE_LOGIN_LIFETIME_MILLIS = 15 * 60 * 1000L
private const val REFRESH_EARLY_MILLIS = 2 * 60 * 1000L
private const val MAX_SUCCESS_BODY_BYTES = 16 * 1024 * 1024
private const val MAX_ERROR_BODY_BYTES = 16 * 1024

internal data class ChatGptProtocolConfig(
  val authBaseUrl: String = DEFAULT_AUTH_BASE_URL,
  val codexBaseUrl: String = DEFAULT_CODEX_BASE_URL,
  val clientId: String = CHATGPT_OAUTH_CLIENT_ID,
  val originator: String = "mosaic",
)

internal class ChatGptCredentials(
  val accessToken: String,
  val refreshToken: String,
  val expiresAtEpochMillis: Long,
  val accountId: String,
) {
  override fun toString(): String = "ChatGptCredentials(redacted)"
}

internal interface ChatGptCredentialStore {
  fun read(): ChatGptCredentials?
  fun write(credentials: ChatGptCredentials)
  fun clear()
}

data class ChatGptConnectionStatus(
  val connected: Boolean,
  val accountIdSuffix: String? = null,
  val expiresAtEpochMillis: Long? = null,
)

data class ChatGptDeviceLogin(
  val deviceAuthId: String,
  val userCode: String,
  val verificationUrl: String,
  val pollIntervalSeconds: Long,
  val expiresAtEpochMillis: Long,
)

enum class ChatGptDeviceLoginPollResult { PENDING, SLOW_DOWN, AUTHORIZED }

data class ChatGptGenerationResult(val modelId: String, val text: String)

class ChatGptOpenAiClient internal constructor(
  private val httpClient: HttpClient,
  private val credentialStore: ChatGptCredentialStore,
  private val config: ChatGptProtocolConfig = ChatGptProtocolConfig(),
  private val clockMillis: () -> Long = System::currentTimeMillis,
) {
  private val refreshMutex = Mutex()
  private val json = Json { ignoreUnknownKeys = true }

  fun connectionStatus(): ChatGptConnectionStatus {
    val credentials = credentialStore.read() ?: return ChatGptConnectionStatus(false)
    return ChatGptConnectionStatus(
      connected = true,
      accountIdSuffix = credentials.accountId.takeLast(6),
      expiresAtEpochMillis = credentials.expiresAtEpochMillis,
    )
  }

  suspend fun startDeviceLogin(): ChatGptDeviceLogin {
    val response = httpClient.execute(
      HttpRequest(
        url = "${config.authBaseUrl.trimEnd('/')}/api/accounts/deviceauth/usercode",
        method = HttpMethod.POST,
        body = buildJsonObject { put("client_id", JsonPrimitive(config.clientId)) }
          .toString().toByteArray(StandardCharsets.UTF_8),
        contentType = "application/json",
      ),
    )
    if (response.statusCode == 404) {
      error("ChatGPT device code authentication is not enabled for this account or workspace")
    }
    requireSuccessful(response, "ChatGPT device code request")
    val body = parseObject(response.body)
    val deviceAuthId = body.string("device_auth_id")
      ?: error("ChatGPT device code response did not contain device_auth_id")
    val userCode = body.string("user_code") ?: body.string("usercode")
      ?: error("ChatGPT device code response did not contain user_code")
    val interval = body.long("interval")?.coerceAtLeast(1L) ?: 5L
    return ChatGptDeviceLogin(
      deviceAuthId = deviceAuthId,
      userCode = userCode,
      verificationUrl = "${config.authBaseUrl.trimEnd('/')}/codex/device",
      pollIntervalSeconds = interval,
      expiresAtEpochMillis = clockMillis() + DEVICE_LOGIN_LIFETIME_MILLIS,
    )
  }

  suspend fun pollDeviceLogin(login: ChatGptDeviceLogin): ChatGptDeviceLoginPollResult {
    if (clockMillis() >= login.expiresAtEpochMillis) error("ChatGPT device login expired. Start a new login.")
    val response = httpClient.execute(
      HttpRequest(
        url = "${config.authBaseUrl.trimEnd('/')}/api/accounts/deviceauth/token",
        method = HttpMethod.POST,
        body = buildJsonObject {
          put("device_auth_id", JsonPrimitive(login.deviceAuthId))
          put("user_code", JsonPrimitive(login.userCode))
        }.toString().toByteArray(StandardCharsets.UTF_8),
        contentType = "application/json",
      ),
    )
    if (response.statusCode == 403 || response.statusCode == 404) return ChatGptDeviceLoginPollResult.PENDING
    if (response.statusCode == 429) return ChatGptDeviceLoginPollResult.SLOW_DOWN
    requireSuccessful(response, "ChatGPT device login polling")
    val body = parseObject(response.body)
    val authorizationCode = body.string("authorization_code")
      ?: error("ChatGPT device login response did not contain authorization_code")
    val codeVerifier = body.string("code_verifier")
      ?: error("ChatGPT device login response did not contain code_verifier")
    credentialStore.write(exchangeAuthorizationCode(authorizationCode, codeVerifier))
    return ChatGptDeviceLoginPollResult.AUTHORIZED
  }

  fun logout() = credentialStore.clear()

  suspend fun generate(modelId: String, prompt: String): ChatGptGenerationResult {
    require(modelId.isNotBlank()) { "ChatGPT model id must not be blank" }
    require(prompt.isNotBlank()) { "ChatGPT prompt must not be blank" }
    var credentials = ensureFreshCredentials(false)
    var response = executeGeneration(credentials, modelId, prompt)
    if (response.statusCode == 401) {
      credentials = ensureFreshCredentials(true)
      response = executeGeneration(credentials, modelId, prompt)
    }
    if (!response.isSuccessful) throw IllegalStateException(providerFailureMessage(response))
    if (response.body.size > MAX_SUCCESS_BODY_BYTES) error("ChatGPT/Codex response exceeded the allowed debug response size")
    return ChatGptGenerationResult(modelId, parseResponseText(response.body.toString(StandardCharsets.UTF_8)))
  }

  private suspend fun ensureFreshCredentials(forceRefresh: Boolean): ChatGptCredentials = refreshMutex.withLock {
    val current = credentialStore.read() ?: error("ChatGPT is not connected")
    if (!forceRefresh && current.expiresAtEpochMillis - clockMillis() > REFRESH_EARLY_MILLIS) return@withLock current
    val latest = credentialStore.read() ?: error("ChatGPT is not connected")
    if (!forceRefresh && latest.expiresAtEpochMillis - clockMillis() > REFRESH_EARLY_MILLIS) return@withLock latest
    refreshCredentials(latest).also(credentialStore::write)
  }

  private suspend fun exchangeAuthorizationCode(authorizationCode: String, codeVerifier: String): ChatGptCredentials {
    val response = postTokenForm(
      mapOf(
        "grant_type" to "authorization_code",
        "client_id" to config.clientId,
        "code" to authorizationCode,
        "code_verifier" to codeVerifier,
        "redirect_uri" to "${config.authBaseUrl.trimEnd('/')}/deviceauth/callback",
      ),
    )
    requireSuccessful(response, "ChatGPT OAuth token exchange")
    return credentialsFromTokenResponse(parseObject(response.body), null)
  }

  private suspend fun refreshCredentials(previous: ChatGptCredentials): ChatGptCredentials {
    val response = postTokenForm(
      mapOf(
        "grant_type" to "refresh_token",
        "refresh_token" to previous.refreshToken,
        "client_id" to config.clientId,
      ),
    )
    requireSuccessful(response, "ChatGPT OAuth token refresh")
    return credentialsFromTokenResponse(parseObject(response.body), previous)
  }

  private suspend fun postTokenForm(fields: Map<String, String>): HttpResponse = httpClient.execute(
    HttpRequest(
      url = "${config.authBaseUrl.trimEnd('/')}/oauth/token",
      method = HttpMethod.POST,
      body = formEncode(fields).toByteArray(StandardCharsets.UTF_8),
      contentType = "application/x-www-form-urlencoded",
    ),
  )

  private fun credentialsFromTokenResponse(body: JsonObject, previous: ChatGptCredentials?): ChatGptCredentials {
    val accessToken = body.string("access_token") ?: error("ChatGPT OAuth response did not contain access_token")
    val refreshToken = body.string("refresh_token") ?: previous?.refreshToken
      ?: error("ChatGPT OAuth response did not contain refresh_token")
    val expiresInSeconds = body.long("expires_in") ?: error("ChatGPT OAuth response did not contain expires_in")
    val accountId = extractAccountId(accessToken)
      ?: body.string("id_token")?.let(::extractAccountId)
      ?: previous?.accountId
      ?: error("ChatGPT OAuth token did not contain a ChatGPT account id")
    return ChatGptCredentials(accessToken, refreshToken, clockMillis() + expiresInSeconds * 1000L, accountId)
  }

  private suspend fun executeGeneration(
    credentials: ChatGptCredentials,
    modelId: String,
    prompt: String,
  ): HttpResponse {
    val requestBody = buildJsonObject {
      put("model", JsonPrimitive(modelId))
      put("store", JsonPrimitive(false))
      put("stream", JsonPrimitive(true))
      put("instructions", JsonPrimitive("You are a helpful assistant."))
      put("input", buildJsonArray {
        add(buildJsonObject {
          put("role", JsonPrimitive("user"))
          put("content", buildJsonArray {
            add(buildJsonObject {
              put("type", JsonPrimitive("input_text"))
              put("text", JsonPrimitive(prompt))
            })
          })
        })
      })
      put("text", buildJsonObject { put("verbosity", JsonPrimitive("low")) })
      put("include", buildJsonArray { add(JsonPrimitive("reasoning.encrypted_content")) })
    }
    return httpClient.execute(
      HttpRequest(
        url = resolveCodexResponsesUrl(config.codexBaseUrl),
        headers = mapOf(
          "Authorization" to "Bearer ${credentials.accessToken}",
          "chatgpt-account-id" to credentials.accountId,
          "originator" to config.originator,
          "OpenAI-Beta" to "responses=experimental",
          "Accept" to "text/event-stream",
        ),
        method = HttpMethod.POST,
        body = requestBody.toString().toByteArray(StandardCharsets.UTF_8),
        contentType = "application/json",
      ),
    )
  }

  private fun parseResponseText(raw: String): String {
    val output = StringBuilder()
    var completedResponse: JsonObject? = null
    raw.split(Regex("(?:\\r\\n|\\n|\\r){2}")).forEach { block ->
      val data = block.lineSequence()
        .filter { it.startsWith("data:") }
        .joinToString("\n") { it.removePrefix("data:").trim() }
        .trim()
      if (data.isBlank() || data == "[DONE]") return@forEach
      val event = runCatching { json.parseToJsonElement(data).jsonObject }
        .getOrElse { error("ChatGPT/Codex returned malformed streaming data") }
      when (event.string("type")) {
        "response.output_text.delta" -> event.string("delta")?.let(output::append)
        "response.completed", "response.done" -> completedResponse = event["response"] as? JsonObject
        "response.failed" -> error((event["response"] as? JsonObject)?.let(::extractResponseError) ?: "ChatGPT/Codex response failed")
        "error" -> error(event.string("message") ?: "ChatGPT/Codex returned an error")
      }
    }
    val streamed = output.toString().trim()
    if (streamed.isNotEmpty()) return streamed
    val completed = completedResponse?.let(::extractCompletedText)?.trim().orEmpty()
    if (completed.isNotEmpty()) return completed
    error("ChatGPT/Codex response did not contain text")
  }

  private fun extractCompletedText(response: JsonObject): String {
    val output = response["output"] as? JsonArray ?: return ""
    return output.mapNotNull { item ->
      val itemObject = item as? JsonObject ?: return@mapNotNull null
      val content = itemObject["content"] as? JsonArray ?: return@mapNotNull null
      content.mapNotNull { contentItem ->
        val contentObject = contentItem as? JsonObject ?: return@mapNotNull null
        if (contentObject.string("type") == "output_text") contentObject.string("text") else null
      }.joinToString("")
    }.joinToString("")
  }

  private fun extractResponseError(response: JsonObject): String? =
    (response["error"] as? JsonObject)?.string("message")

  private fun providerFailureMessage(response: HttpResponse): String {
    val body = response.body.take(MAX_ERROR_BODY_BYTES).toByteArray().toString(StandardCharsets.UTF_8)
    val parsedMessage = runCatching {
      val root = json.parseToJsonElement(body).jsonObject
      root.string("message") ?: (root["error"] as? JsonObject)?.string("message")
    }.getOrNull()
    return parsedMessage?.takeIf(String::isNotBlank)
      ?.let { "ChatGPT/Codex request failed (${response.statusCode}): $it" }
      ?: "ChatGPT/Codex request failed (${response.statusCode})"
  }

  private fun requireSuccessful(response: HttpResponse, operation: String) {
    if (!response.isSuccessful) throw IllegalStateException("$operation failed (${response.statusCode})")
  }

  private fun parseObject(bytes: ByteArray): JsonObject {
    if (bytes.size > MAX_ERROR_BODY_BYTES) error("ChatGPT OAuth response was unexpectedly large")
    return json.parseToJsonElement(bytes.toString(StandardCharsets.UTF_8)).jsonObject
  }

  private fun extractAccountId(token: String): String? {
    val payloadPart = token.split('.').getOrNull(1) ?: return null
    val padding = "=".repeat((4 - payloadPart.length % 4) % 4)
    val decoded = runCatching { Base64.getUrlDecoder().decode(payloadPart + padding) }.getOrNull() ?: return null
    val payload = runCatching { json.parseToJsonElement(decoded.toString(StandardCharsets.UTF_8)).jsonObject }.getOrNull() ?: return null
    payload.string("chatgpt_account_id")?.let { return it }
    payload.string("https://api.openai.com/auth.chatgpt_account_id")?.let { return it }
    (payload["https://api.openai.com/auth"] as? JsonObject)?.string("chatgpt_account_id")?.let { return it }
    return (payload["organizations"] as? JsonArray)?.firstOrNull()?.let { it as? JsonObject }?.string("id")
  }

  companion object {
    fun create(context: Context, httpClient: HttpClient): ChatGptOpenAiClient = ChatGptOpenAiClient(
      httpClient = httpClient,
      credentialStore = AndroidChatGptCredentialStore(context.applicationContext),
    )
  }
}

private fun JsonObject.string(key: String): String? =
  (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun formEncode(fields: Map<String, String>): String = fields.entries.joinToString("&") { (key, value) ->
  "${URLEncoder.encode(key, StandardCharsets.UTF_8.toString())}=${URLEncoder.encode(value, StandardCharsets.UTF_8.toString())}"
}

private fun resolveCodexResponsesUrl(baseUrl: String): String {
  val normalized = baseUrl.trimEnd('/')
  return when {
    normalized.endsWith("/codex/responses") -> normalized
    normalized.endsWith("/codex") -> "$normalized/responses"
    else -> "$normalized/codex/responses"
  }
}
