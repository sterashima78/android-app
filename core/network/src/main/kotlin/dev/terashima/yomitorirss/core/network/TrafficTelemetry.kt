package dev.terashima.yomitorirss.core.network

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Propagates a feature attribution through coroutine-based HTTP calls. */
class TrafficAttribution(val tag: String) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<TrafficAttribution>
}

data class TrafficTransfer(val tag: String, val receivedBytes: Long, val sentBytes: Long)

/** Recording must never interrupt the operation being measured. */
object TrafficTelemetry {
  @Volatile var recorder: ((TrafficTransfer) -> Unit)? = null

  fun record(tag: String, receivedBytes: Long, sentBytes: Long) {
    if (receivedBytes <= 0L && sentBytes <= 0L) return
    try {
      recorder?.invoke(TrafficTransfer(tag, receivedBytes.coerceAtLeast(0L), sentBytes.coerceAtLeast(0L)))
    } catch (_: Exception) {
      // Best-effort diagnostics.
    }
  }
}
