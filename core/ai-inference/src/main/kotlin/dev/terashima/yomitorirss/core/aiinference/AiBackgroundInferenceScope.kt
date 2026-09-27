package dev.terashima.yomitorirss.core.aiinference

import androidx.work.CoroutineWorker
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * Capability token for one-shot AI inference owned by a durable WorkManager execution.
 *
 * The constructor is private. Production callers can obtain a token only while executing a
 * [CoroutineWorker] through [withAiBackgroundInference].
 */
class AiBackgroundInferenceScope private constructor(
  private val workerId: String,
) {
  suspend fun requireActive() {
    check(currentCoroutineContext()[AiBackgroundInferenceExecution]?.workerId == workerId) {
      "One-shot AI inference must run inside its durable background worker"
    }
  }

  internal companion object {
    fun forWorker(workerId: String): AiBackgroundInferenceScope =
      AiBackgroundInferenceScope(workerId)
  }
}

suspend fun <T> CoroutineWorker.withAiBackgroundInference(
  block: suspend (AiBackgroundInferenceScope) -> T,
): T {
  val workerId = id.toString()
  return withContext(AiBackgroundInferenceExecution(workerId)) {
    block(AiBackgroundInferenceScope.forWorker(workerId))
  }
}

private class AiBackgroundInferenceExecution(
  val workerId: String,
) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<AiBackgroundInferenceExecution>
}
