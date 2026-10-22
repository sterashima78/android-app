package dev.terashima.yomitorirss.core.network

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Propagates a feature attribution through coroutine-based HTTP calls. */
class TrafficAttribution(val tag: String) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<TrafficAttribution>
}

/** Opaque RSS feed identifier and the refresh entry point; never a URL or title. */
data class TrafficResource(val id: String, val origin: String)

data class TrafficTransfer(
  val tag: String,
  val receivedBytes: Long,
  val sentBytes: Long,
  val resource: TrafficResource? = null,
  val responseStatus: Int? = null,
)

/** Recording must never interrupt the operation being measured. */
object TrafficTelemetry {
  @Volatile var recorder: ((TrafficTransfer) -> Unit)? = null

  fun record(
    tag: String,
    receivedBytes: Long,
    sentBytes: Long,
    resource: TrafficResource? = null,
    responseStatus: Int? = null,
  ) {
    // A body-less 304 (or failed request) still counts as an RSS fetch attempt.
    if (receivedBytes <= 0L && sentBytes <= 0L && resource == null) return
    try {
      recorder?.invoke(
        TrafficTransfer(tag, receivedBytes.coerceAtLeast(0L), sentBytes.coerceAtLeast(0L), resource, responseStatus),
      )
    } catch (_: Exception) {
      // Best-effort diagnostics.
    }
  }
}
