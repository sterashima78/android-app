package dev.terashima.yomitorirss.core.background

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes expensive background local-AI jobs across feature-owned queues.
 *
 * Durable task state remains owned by each feature. This gate only controls execution so two
 * background workers do not load/run the local model at the same time in this app process.
 */
object LocalAiBackgroundTaskGate {
  private val mutex = Mutex()

  suspend fun <T> withPermit(block: suspend () -> T): T = mutex.withLock {
    block()
  }
}
