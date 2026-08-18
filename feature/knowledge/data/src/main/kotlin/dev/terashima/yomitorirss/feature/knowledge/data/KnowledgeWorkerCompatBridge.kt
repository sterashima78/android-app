package dev.terashima.yomitorirss.feature.knowledge.data

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

suspend fun runKnowledgeBuildWorker(
  appContext: Context,
  params: WorkerParameters,
): ListenableWorker.Result = KnowledgeBuildWorker(appContext, params).doWork()

suspend fun runKnowledgeBuildResumeOnChargingWorker(
  appContext: Context,
  params: WorkerParameters,
): ListenableWorker.Result = KnowledgeBuildResumeOnChargingWorker(appContext, params).doWork()
