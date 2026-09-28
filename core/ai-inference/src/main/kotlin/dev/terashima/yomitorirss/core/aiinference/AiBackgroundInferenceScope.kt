package dev.terashima.yomitorirss.core.aiinference

import androidx.work.CoroutineWorker
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * Establishes the execution boundary required by production one-shot AI inference.
 *
 * A caller needs a real [CoroutineWorker] receiver to establish this context. Inference adapters
 * reject generation outside this scope, so UI/ViewModel coroutines cannot execute one-shot
 * inference even when a feature wrapper accidentally exposes it.
 */
suspend fun <T> CoroutineWorker.withAiBackgroundInference(
  block: suspend () -> T,
): T = withContext(AiBackgroundInferenceExecution(id.toString())) {
  block()
}

suspend fun requireAiBackgroundInferenceExecution() {
  check(currentCoroutineContext()[AiBackgroundInferenceExecution] != null) {
    "One-shot AI inference must run inside a durable background worker"
  }
}

private class AiBackgroundInferenceExecution(
  val workerId: String,
) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<AiBackgroundInferenceExecution>
}
