package dev.terashima.yomitorirss.core.background

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

enum class LocalAiBackgroundTaskPriority(internal val rank: Int) {
  LOW(0),
  NORMAL(1),
  HIGH(2),
}

/**
 * Serializes expensive background local-AI jobs across feature-owned queues.
 *
 * Durable task state and the business meaning of a priority remain owned by each feature. This
 * gate only chooses which waiting task may run next. Running inference is never preempted; the
 * highest-priority waiter is selected when the current permit is released, with FIFO ordering
 * inside the same priority.
 */
object LocalAiBackgroundTaskGate {
  private val lock = Any()
  private val waiters = mutableListOf<Waiter>()
  private var active = false
  private var nextSequence = 0L

  @Volatile
  private var activeDiagnosticLabel: String? = null

  suspend fun <T> withPermit(
    priority: LocalAiBackgroundTaskPriority = LocalAiBackgroundTaskPriority.NORMAL,
    diagnosticLabel: String? = null,
    block: suspend () -> T,
  ): T {
    val resolvedDiagnosticLabel = diagnosticLabel?.takeIf(String::isNotBlank)
      ?: inferDiagnosticLabel()
    acquire(priority, resolvedDiagnosticLabel)
    return try {
      block()
    } finally {
      release()
    }
  }

  /**
   * Returns a sanitized implementation-level label for the background local-AI task currently
   * holding the permit. This is diagnostics-only state; business behavior must not depend on it.
   */
  fun currentDiagnosticLabel(): String? = activeDiagnosticLabel

  private suspend fun acquire(
    priority: LocalAiBackgroundTaskPriority,
    diagnosticLabel: String,
  ) {
    val waiter = synchronized(lock) {
      if (!active) {
        active = true
        activeDiagnosticLabel = diagnosticLabel
        null
      } else {
        Waiter(
          priority = priority,
          sequence = nextSequence++,
          signal = CompletableDeferred(),
          diagnosticLabel = diagnosticLabel,
        ).also(waiters::add)
      }
    } ?: return

    try {
      waiter.signal.await()
    } catch (cancelled: CancellationException) {
      val hadPermit = synchronized(lock) {
        when (waiter.state) {
          WaiterState.WAITING -> {
            waiter.state = WaiterState.CANCELLED
            waiters.remove(waiter)
            false
          }
          WaiterState.GRANTED -> {
            waiter.state = WaiterState.CANCELLED
            true
          }
          WaiterState.CANCELLED -> false
        }
      }
      if (hadPermit) release()
      throw cancelled
    }
  }

  private fun release() {
    val next = synchronized(lock) {
      val candidate = waiters
        .asSequence()
        .filter { it.state == WaiterState.WAITING }
        .minWithOrNull(
          compareByDescending<Waiter> { it.priority.rank }
            .thenBy { it.sequence },
        )

      if (candidate == null) {
        active = false
        activeDiagnosticLabel = null
        null
      } else {
        waiters.remove(candidate)
        candidate.state = WaiterState.GRANTED
        activeDiagnosticLabel = candidate.diagnosticLabel
        candidate
      }
    }
    next?.signal?.complete(Unit)
  }

  private fun inferDiagnosticLabel(): String =
    Throwable().stackTrace
      .asSequence()
      .map(StackTraceElement::getClassName)
      .map { className -> className.substringBefore('$') }
      .firstOrNull { className ->
        className.startsWith(APP_PACKAGE_PREFIX) &&
          !className.startsWith("${APP_PACKAGE_PREFIX}core.background.")
      }
      ?.filter { character -> character.isLetterOrDigit() || character in ".:_-" }
      ?.take(MAX_DIAGNOSTIC_LABEL_CHARS)
      ?.takeIf(String::isNotBlank)
      ?: "unknown"

  private data class Waiter(
    val priority: LocalAiBackgroundTaskPriority,
    val sequence: Long,
    val signal: CompletableDeferred<Unit>,
    val diagnosticLabel: String,
    var state: WaiterState = WaiterState.WAITING,
  )

  private enum class WaiterState {
    WAITING,
    GRANTED,
    CANCELLED,
  }

  private const val APP_PACKAGE_PREFIX = "dev.terashima.yomitorirss."
  private const val MAX_DIAGNOSTIC_LABEL_CHARS = 160
}
