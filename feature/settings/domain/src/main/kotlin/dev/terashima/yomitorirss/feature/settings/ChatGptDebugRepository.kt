package dev.terashima.yomitorirss.feature.settings

data class ChatGptDebugStatus(
  val connected: Boolean,
  val accountLabel: String? = null,
  val expiresAtEpochMillis: Long? = null,
)

data class ChatGptDebugLoginSession(
  val id: String,
  val userCode: String,
  val verificationUrl: String,
  val pollIntervalSeconds: Long,
  val expiresAtEpochMillis: Long,
)

enum class ChatGptDebugLoginPollResult { PENDING, SLOW_DOWN, AUTHORIZED }

data class ChatGptDebugInferenceResult(
  val modelId: String,
  val text: String,
  val elapsedMillis: Long,
)

interface ChatGptDebugRepository {
  val defaultModelId: String
  fun status(): ChatGptDebugStatus
  suspend fun startLogin(): ChatGptDebugLoginSession
  suspend fun pollLogin(sessionId: String): ChatGptDebugLoginPollResult
  fun logout()
  suspend fun runInference(modelId: String, prompt: String): ChatGptDebugInferenceResult
}
