package dev.terashima.yomitorirss.core.airuntime

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One logical consumer of the retained local-AI engine.
 *
 * A session is an application/runtime lifecycle boundary, not a LiteRT-LM Conversation. Closing a
 * session only releases its lease; the Engine is kept warm until the idle timeout expires.
 */
internal class LocalInferenceSession(
  private val onClose: () -> Unit,
) : AutoCloseable {
  private val closed = AtomicBoolean(false)

  override fun close() {
    if (closed.compareAndSet(false, true)) onClose()
  }
}

internal fun interface IdleReleaseHandle {
  fun cancel()
}

internal fun interface IdleReleaseScheduler {
  fun schedule(delayMillis: Long, action: () -> Unit): IdleReleaseHandle
}

/**
 * Reference-counts inference sessions and releases the retained engine only after a stable idle
 * period. A new session cancels a pending release before it can evict the engine.
 */
internal class LocalInferenceSessionTracker(
  private val idleTimeoutMillis: Long,
  private val onIdle: () -> Unit,
  private val scheduler: IdleReleaseScheduler = SharedIdleReleaseScheduler,
) : AutoCloseable {
  private val lock = Any()
  private var activeSessions = 0
  private var pendingRelease: IdleReleaseHandle? = null
  private var closed = false

  fun openSession(): LocalInferenceSession {
    synchronized(lock) {
      check(!closed) { "推論セッション管理は終了しています" }
      pendingRelease?.cancel()
      pendingRelease = null
      activeSessions += 1
    }
    return LocalInferenceSession(::releaseSession)
  }

  internal fun activeSessionCount(): Int = synchronized(lock) { activeSessions }

  private fun releaseSession() {
    synchronized(lock) {
      check(activeSessions > 0) { "推論セッション数が不正です" }
      activeSessions -= 1
      if (activeSessions != 0 || closed) return

      pendingRelease?.cancel()
      pendingRelease = scheduler.schedule(idleTimeoutMillis) {
        // Keep the tracker lock while evicting. A newly opening session therefore cannot race with
        // Engine.close(); it starts only after the old retained Engine has been fully released.
        synchronized(lock) {
          if (closed || activeSessions != 0) return@synchronized
          pendingRelease = null
          onIdle()
        }
      }
    }
  }

  override fun close() {
    synchronized(lock) {
      if (closed) return
      closed = true
      pendingRelease?.cancel()
      pendingRelease = null
    }
  }
}

private object SharedIdleReleaseScheduler : IdleReleaseScheduler {
  private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
    Thread(runnable, "local-ai-idle-release").apply { isDaemon = true }
  }

  override fun schedule(delayMillis: Long, action: () -> Unit): IdleReleaseHandle {
    val future = executor.schedule(action, delayMillis, TimeUnit.MILLISECONDS)
    return IdleReleaseHandle { future.cancel(false) }
  }
}
