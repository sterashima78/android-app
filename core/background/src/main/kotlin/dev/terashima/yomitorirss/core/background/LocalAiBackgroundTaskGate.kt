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

  suspend fun <T> withPermit(
    priority: LocalAiBackgroundTaskPriority = LocalAiBackgroundTaskPriority.NORMAL,
    block: suspend () -> T,
  ): T {
    acquire(priority)
    return try {
      block()
    } finally {
      release()
    }
  }

  private suspend fun acquire(priority: LocalAiBackgroundTaskPriority) {
    val waiter = synchronized(lock) {
      if (!active) {
        active = true
        null
      } else {
        Waiter(
          priority = priority,
          sequence = nextSequence++,
          signal = CompletableDeferred(),
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
        null
      } else {
        waiters.remove(candidate)
        candidate.state = WaiterState.GRANTED
        candidate
      }
    }
    next?.signal?.complete(Unit)
  }

  private data class Waiter(
    val priority: LocalAiBackgroundTaskPriority,
    val sequence: Long,
    val signal: CompletableDeferred<Unit>,
    var state: WaiterState = WaiterState.WAITING,
  )

  private enum class WaiterState {
    WAITING,
    GRANTED,
    CANCELLED,
  }
}
