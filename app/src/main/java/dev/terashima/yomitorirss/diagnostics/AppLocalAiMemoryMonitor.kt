package dev.terashima.yomitorirss.diagnostics

import android.app.Application
import android.os.SystemClock
import dev.terashima.yomitorirss.core.airuntime.LocalAiMemoryDiagnostics
import dev.terashima.yomitorirss.core.airuntime.LocalAiProcessMemoryPhase
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Samples main-process memory while background local-AI work is active and for a bounded period
 * after it becomes idle. The diagnostic label is an implementation class name supplied by the
 * global AI gate; no article title, URL, prompt, model output, or other user data is persisted.
 */
internal object AppLocalAiMemoryMonitor {
  private val installed = AtomicBoolean(false)
  private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
    Thread(runnable, "local-ai-memory-monitor").apply { isDaemon = true }
  }

  fun install(application: Application) {
    if (!installed.compareAndSet(false, true)) return
    val samplingWindow = LocalAiMemorySamplingWindow(RETAINED_SAMPLE_WINDOW_MILLIS)
    executor.scheduleWithFixedDelay(
      {
        runCatching {
          val request = samplingWindow.next(
            nowMillis = SystemClock.elapsedRealtime(),
            activeDiagnosticLabel = LocalAiBackgroundTaskGate.currentDiagnosticLabel(),
          ) ?: return@runCatching
          LocalAiMemoryDiagnostics.recordProcessSample(
            context = application,
            phase = request.phase,
            diagnosticLabel = request.diagnosticLabel,
          )
        }
      },
      0,
      SAMPLE_INTERVAL_SECONDS,
      TimeUnit.SECONDS,
    )
  }

  private const val SAMPLE_INTERVAL_SECONDS = 10L
  private const val RETAINED_SAMPLE_WINDOW_MILLIS = 5L * 60L * 1000L
}

internal data class LocalAiMemorySampleRequest(
  val phase: LocalAiProcessMemoryPhase,
  val diagnosticLabel: String,
)

internal class LocalAiMemorySamplingWindow(
  private val retainedWindowMillis: Long,
) {
  private var lastActiveLabel: String? = null
  private var lastActiveAtMillis = Long.MIN_VALUE

  init {
    require(retainedWindowMillis >= 0L) { "retainedWindowMillis must not be negative" }
  }

  fun next(
    nowMillis: Long,
    activeDiagnosticLabel: String?,
  ): LocalAiMemorySampleRequest? {
    activeDiagnosticLabel?.takeIf(String::isNotBlank)?.let { label ->
      lastActiveLabel = label
      lastActiveAtMillis = nowMillis
      return LocalAiMemorySampleRequest(
        phase = LocalAiProcessMemoryPhase.ACTIVE_BACKGROUND_AI,
        diagnosticLabel = label,
      )
    }

    val retainedLabel = lastActiveLabel ?: return null
    if (nowMillis - lastActiveAtMillis > retainedWindowMillis) {
      lastActiveLabel = null
      return null
    }
    return LocalAiMemorySampleRequest(
      phase = LocalAiProcessMemoryPhase.RETAINED_AFTER_BACKGROUND_AI,
      diagnosticLabel = retainedLabel,
    )
  }
}
