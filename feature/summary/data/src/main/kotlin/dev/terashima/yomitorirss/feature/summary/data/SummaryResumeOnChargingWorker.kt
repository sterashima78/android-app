package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class SummaryResumeOnChargingWorker(
  appContext: Context,
  params: WorkerParameters,
) : Worker(appContext, params) {
  override fun doWork(): Result = runCatching {
    SummaryQueue.resumeAutomaticallyWhenCharging(applicationContext)
    Result.success()
  }.getOrElse {
    Result.retry()
  }
}
