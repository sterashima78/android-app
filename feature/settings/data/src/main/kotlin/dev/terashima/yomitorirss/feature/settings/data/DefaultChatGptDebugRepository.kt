package dev.terashima.yomitorirss.feature.settings.data

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptDeviceLogin
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptDeviceLoginPollResult
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptOpenAiClient
import dev.terashima.yomitorirss.core.aicloudopenai.DEFAULT_CHATGPT_CODEX_MODEL_ID
import dev.terashima.yomitorirss.feature.settings.ChatGptDebugInferenceResult
import dev.terashima.yomitorirss.feature.settings.ChatGptDebugLoginPollResult
import dev.terashima.yomitorirss.feature.settings.ChatGptDebugLoginSession
import dev.terashima.yomitorirss.feature.settings.ChatGptDebugRepository
import dev.terashima.yomitorirss.feature.settings.ChatGptDebugStatus
import java.util.UUID

class DefaultChatGptDebugRepository(
  private val client: ChatGptOpenAiClient,
  private val clockMillis: () -> Long = System::currentTimeMillis,
) : ChatGptDebugRepository {
  private val pendingLogins = mutableMapOf<String, ChatGptDeviceLogin>()

  override val defaultModelId: String = DEFAULT_CHATGPT_CODEX_MODEL_ID

  override fun status(): ChatGptDebugStatus {
    val status = client.connectionStatus()
    return ChatGptDebugStatus(
      connected = status.connected,
      accountLabel = status.accountIdSuffix?.let { "…$it" },
      expiresAtEpochMillis = status.expiresAtEpochMillis,
    )
  }

  override suspend fun startLogin(): ChatGptDebugLoginSession {
    val login = client.startDeviceLogin()
    val id = UUID.randomUUID().toString()
    synchronized(pendingLogins) {
      pendingLogins.clear()
      pendingLogins[id] = login
    }
    return ChatGptDebugLoginSession(
      id = id,
      userCode = login.userCode,
      verificationUrl = login.verificationUrl,
      pollIntervalSeconds = login.pollIntervalSeconds,
      expiresAtEpochMillis = login.expiresAtEpochMillis,
    )
  }

  override suspend fun pollLogin(sessionId: String): ChatGptDebugLoginPollResult {
    val login = synchronized(pendingLogins) { pendingLogins[sessionId] }
      ?: error("ChatGPT login session is no longer available. Start a new login.")
    val result = client.pollDeviceLogin(login)
    if (result == ChatGptDeviceLoginPollResult.AUTHORIZED) {
      synchronized(pendingLogins) { pendingLogins.remove(sessionId) }
    }
    return when (result) {
      ChatGptDeviceLoginPollResult.PENDING -> ChatGptDebugLoginPollResult.PENDING
      ChatGptDeviceLoginPollResult.SLOW_DOWN -> ChatGptDebugLoginPollResult.SLOW_DOWN
      ChatGptDeviceLoginPollResult.AUTHORIZED -> ChatGptDebugLoginPollResult.AUTHORIZED
    }
  }

  override fun logout() {
    synchronized(pendingLogins) { pendingLogins.clear() }
    client.logout()
  }

  override suspend fun runInference(modelId: String, prompt: String): ChatGptDebugInferenceResult {
    val startedAt = clockMillis()
    val result = client.generate(modelId, prompt)
    return ChatGptDebugInferenceResult(
      modelId = result.modelId,
      text = result.text,
      elapsedMillis = (clockMillis() - startedAt).coerceAtLeast(0L),
    )
  }
}
