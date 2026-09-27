package dev.terashima.yomitorirss.core.background

import androidx.work.CoroutineWorker
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * Establishes the execution context required by one-shot production AI inference.
 *
 * The receiver requirement intentionally makes the capability originate from a WorkManager worker.
 * Feature UI and ordinary application services cannot create the marker directly.
 */
suspend fun <T> CoroutineWorker.withAiBackgroundInferenceExecution(
  block: suspend () -> T,
): T = withContext(AiBackgroundInferenceExecution(workerId = id.toString())) {
  block()
}

/**
 * Fails when one-shot production AI inference is invoked outside a WorkManager background worker.
 */
suspend fun requireAiBackgroundInferenceExecution() {
  check(currentCoroutineContext()[AiBackgroundInferenceExecution] != null) {
    "One-shot AI inference must run from a durable background worker"
  }
}

private class AiBackgroundInferenceExecution(
  val workerId: String,
) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<AiBackgroundInferenceExecution>
}
