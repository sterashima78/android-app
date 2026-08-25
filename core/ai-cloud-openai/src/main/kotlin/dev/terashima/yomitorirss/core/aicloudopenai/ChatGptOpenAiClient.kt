package dev.terashima.yomitorirss.core.aicloudopenai

import android.content.Context
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpMethod
import dev.terashima.yomitorirss.core.network.HttpRequest
import dev.terashima.yomitorirss.core.network.HttpResponse
import java.net.URI
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
private const val CODEX_COMPAT_CLIENT_VERSION = "0.149.0"
private const val DEVICE_LOGIN_LIFETIME_MILLIS = 15 * 60 * 1000L
private const val REFRESH_EARLY_MILLIS = 2 * 60 * 1000L
private const val MAX_SUCCESS_BODY_BYTES = 16 * 1024 * 1024
private const val MAX_MODELS_BODY_BYTES = 4 * 1024 * 1024
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

data class ChatGptWebGenerationResult(
  val modelId: String,
  val text: String,
  val openedUrls: List<String>,
)

data class ChatGptModelInfo(
  val id: String,
  val displayName: String,
  val description: String?,
  val contextWindowTokens: Int?,
  val maxContextWindowTokens: Int?,
  val supportsWebSearch: Boolean,
  val supportedInApi: Boolean,
  val visibleInPicker: Boolean,
  val priority: Int,
)

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
        maxResponseBytes = MAX_ERROR_BODY_BYTES.toLong(),
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
        maxResponseBytes = MAX_ERROR_BODY_BYTES.toLong(),
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

  suspend fun listModels(): List<ChatGptModelInfo> {
    var credentials = ensureFreshCredentials(false)
    var response = executeListModels(credentials)
    if (response.statusCode == 401) {
      credentials = ensureFreshCredentials(true)
      response = executeListModels(credentials)
    }
    if (!response.isSuccessful) throw IllegalStateException(providerFailureMessage(response))
    if (response.body.size > MAX_MODELS_BODY_BYTES) error("ChatGPT/Codex model catalog response was unexpectedly large")
    val root = json.parseToJsonElement(response.body.toString(StandardCharsets.UTF_8)).jsonObject
    val models = root["models"] as? JsonArray ?: error("ChatGPT/Codex model catalog did not contain models")
    return models.mapNotNull { element ->
      val model = element as? JsonObject ?: return@mapNotNull null
      val id = model.string("slug") ?: return@mapNotNull null
      ChatGptModelInfo(
        id = id,
        displayName = model.string("display_name") ?: id,
        description = model.string("description"),
        contextWindowTokens = model.int("context_window"),
        maxContextWindowTokens = model.int("max_context_window"),
        supportsWebSearch = model.string("web_search_tool_type")
          ?.let { it == "text" || it == "text_and_image" }
          ?: (model.bool("supports_search_tool") ?: false),
        supportedInApi = model.bool("supported_in_api") ?: false,
        visibleInPicker = model.string("visibility") == "list",
        priority = model.int("priority") ?: Int.MAX_VALUE,
      )
    }.sortedWith(compareBy(ChatGptModelInfo::priority, ChatGptModelInfo::displayName))
  }

  suspend fun generate(modelId: String, prompt: String): ChatGptGenerationResult {
    require(modelId.isNotBlank()) { "ChatGPT model id must not be blank" }
    require(prompt.isNotBlank()) { "ChatGPT prompt must not be blank" }
    var credentials = ensureFreshCredentials(false)
    var response = executeGeneration(credentials, modelId, prompt, webTarget = null)
    if (response.statusCode == 401) {
      credentials = ensureFreshCredentials(true)
      response = executeGeneration(credentials, modelId, prompt, webTarget = null)
    }
    if (!response.isSuccessful) throw IllegalStateException(providerFailureMessage(response))
    val parsed = parseGenerationResponse(response)
    return ChatGptGenerationResult(modelId, parsed.text)
  }

  suspend fun generateWithWebSearch(
    modelId: String,
    prompt: String,
    targetUrl: String,
  ): ChatGptWebGenerationResult {
    require(modelId.isNotBlank()) { "ChatGPT model id must not be blank" }
    require(prompt.isNotBlank()) { "ChatGPT prompt must not be blank" }
    val target = validatePublicWebTarget(targetUrl)
    var credentials = ensureFreshCredentials(false)
    var response = executeGeneration(credentials, modelId, prompt, target)
    if (response.statusCode == 401) {
      credentials = ensureFreshCredentials(true)
      response = executeGeneration(credentials, modelId, prompt, target)
    }
    if (!response.isSuccessful) throw IllegalStateException(providerFailureMessage(response))
    val parsed = parseGenerationResponse(response)
    check(parsed.openedUrls.any { sameTargetPage(target, runCatching { URI(it) }.getOrNull()) }) {
      "ChatGPT/Codex did not open the specified article URL"
    }
    return ChatGptWebGenerationResult(modelId, parsed.text, parsed.openedUrls)
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
      maxResponseBytes = MAX_ERROR_BODY_BYTES.toLong(),
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

  private suspend fun executeListModels(credentials: ChatGptCredentials): HttpResponse = httpClient.execute(
    HttpRequest(
      url = resolveCodexModelsUrl(config.codexBaseUrl),
      headers = authenticatedHeaders(credentials) + mapOf("Accept" to "application/json"),
      maxResponseBytes = MAX_MODELS_BODY_BYTES.toLong(),
      maxErrorResponseBytes = MAX_ERROR_BODY_BYTES.toLong(),
    ),
  )

  private suspend fun executeGeneration(
    credentials: ChatGptCredentials,
    modelId: String,
    prompt: String,
    webTarget: URI?,
  ): HttpResponse {
    val requestBody = buildJsonObject {
      put("model", JsonPrimitive(modelId))
      put("store", JsonPrimitive(false))
      put("stream", JsonPrimitive(true))
      put("instructions", JsonPrimitive("You are a helpful assistant. Follow the user request exactly."))
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
      if (webTarget != null) {
        put("tools", buildJsonArray {
          add(buildJsonObject {
            put("type", JsonPrimitive("web_search"))
            put("external_web_access", JsonPrimitive(true))
            put("filters", buildJsonObject {
              put("allowed_domains", buildJsonArray { add(JsonPrimitive(webTarget.host)) })
            })
          })
        })
        put("tool_choice", JsonPrimitive("required"))
        put("parallel_tool_calls", JsonPrimitive(false))
      }
      put("text", buildJsonObject { put("verbosity", JsonPrimitive("low")) })
      put("include", buildJsonArray { add(JsonPrimitive("reasoning.encrypted_content")) })
    }
    return httpClient.execute(
      HttpRequest(
        url = resolveCodexResponsesUrl(config.codexBaseUrl),
        headers = authenticatedHeaders(credentials) + mapOf("Accept" to "text/event-stream"),
        method = HttpMethod.POST,
        body = requestBody.toString().toByteArray(StandardCharsets.UTF_8),
        contentType = "application/json",
        maxResponseBytes = MAX_SUCCESS_BODY_BYTES.toLong(),
        maxErrorResponseBytes = MAX_ERROR_BODY_BYTES.toLong(),
      ),
    )
  }

  private fun authenticatedHeaders(credentials: ChatGptCredentials): Map<String, String> = mapOf(
    "Authorization" to "Bearer ${credentials.accessToken}",
    "chatgpt-account-id" to credentials.accountId,
    "originator" to config.originator,
    "OpenAI-Beta" to "responses=experimental",
  )

  private fun parseGenerationResponse(response: HttpResponse): ParsedGeneration {
    if (response.body.size > MAX_SUCCESS_BODY_BYTES) error("ChatGPT/Codex response exceeded the allowed response size")
    val raw = response.body.toString(StandardCharsets.UTF_8)
    val output = StringBuilder()
    val openedUrls = linkedSetOf<String>()
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
        "response.output_item.done" -> collectOpenedUrls(event["item"] as? JsonObject, openedUrls)
        "response.completed", "response.done" -> completedResponse = event["response"] as? JsonObject
        "response.failed" -> error((event["response"] as? JsonObject)?.let(::extractResponseError) ?: "ChatGPT/Codex response failed")
        "error" -> error(event.string("message") ?: "ChatGPT/Codex returned an error")
      }
    }
    completedResponse?.let { responseObject ->
      (responseObject["output"] as? JsonArray)?.forEach { collectOpenedUrls(it as? JsonObject, openedUrls) }
    }
    val text = output.toString().trim().ifEmpty {
      completedResponse?.let(::extractCompletedText)?.trim().orEmpty()
    }
    check(text.isNotBlank()) { "ChatGPT/Codex response did not contain text" }
    return ParsedGeneration(text, openedUrls.toList())
  }

  private fun collectOpenedUrls(item: JsonObject?, destination: MutableSet<String>) {
    if (item?.string("type") != "web_search_call") return
    val action = item["action"] as? JsonObject ?: return
    if (action.string("type") == "open_page") action.string("url")?.let(destination::add)
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

private data class ParsedGeneration(val text: String, val openedUrls: List<String>)

private fun JsonObject.string(key: String): String? =
  (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

private fun JsonObject.bool(key: String): Boolean? =
  (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

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

private fun resolveCodexModelsUrl(baseUrl: String): String {
  val normalized = baseUrl.trimEnd('/')
  val endpoint = when {
    normalized.endsWith("/codex/models") -> normalized
    normalized.endsWith("/codex") -> "$normalized/models"
    else -> "$normalized/codex/models"
  }
  return "$endpoint?client_version=${URLEncoder.encode(CODEX_COMPAT_CLIENT_VERSION, StandardCharsets.UTF_8.toString())}"
}

private fun validatePublicWebTarget(value: String): URI {
  val uri = runCatching { URI(value.trim()) }.getOrElse { error("記事URLが正しくありません") }
  require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
    "クラウド要約は公開HTTPS URLだけを対象にできます"
  }
  require(uri.userInfo == null) { "ユーザー情報を含むURLはクラウド要約できません" }
  val host = uri.host.lowercase()
  require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local")) {
    "ローカルURLはクラウド要約できません"
  }
  require(!isPrivateIpLiteral(host)) { "プライベートIPのURLはクラウド要約できません" }
  return uri
}

private fun isPrivateIpLiteral(host: String): Boolean {
  val parts = host.split('.')
  if (parts.size != 4) return host == "::1" || host.startsWith("fc", true) || host.startsWith("fd", true) || host.startsWith("fe80:", true)
  val octets = parts.map { it.toIntOrNull() ?: return false }
  return octets[0] == 10 ||
    octets[0] == 127 ||
    (octets[0] == 169 && octets[1] == 254) ||
    (octets[0] == 172 && octets[1] in 16..31) ||
    (octets[0] == 192 && octets[1] == 168)
}

private fun sameTargetPage(expected: URI, actual: URI?): Boolean {
  actual ?: return false
  if (!expected.host.equals(actual.host, ignoreCase = true)) return false
  val expectedPath = expected.path.orEmpty().trimEnd('/').ifEmpty { "/" }
  val actualPath = actual.path.orEmpty().trimEnd('/').ifEmpty { "/" }
  return expectedPath == actualPath
}
