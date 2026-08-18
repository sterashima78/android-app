package dev.terashima.yomitorirss.feature.knowledge

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.feature.knowledge.data.runKnowledgeBuildResumeOnChargingWorker
import dev.terashima.yomitorirss.feature.knowledge.data.runKnowledgeBuildWorker

@Deprecated("Compatibility shim for persisted WorkManager requests")
class KnowledgeBuildWorker(
  appContext: Context,
  private val params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = runKnowledgeBuildWorker(applicationContext, params)
}

@Deprecated("Compatibility shim for persisted WorkManager requests")
class KnowledgeBuildResumeOnChargingWorker(
  appContext: Context,
  private val params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result =
    runKnowledgeBuildResumeOnChargingWorker(applicationContext, params)
}
