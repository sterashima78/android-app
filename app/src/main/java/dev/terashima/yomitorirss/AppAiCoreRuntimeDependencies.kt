package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.settings.AiModelRepository
import dev.terashima.yomitorirss.feature.settings.data.DefaultAiModelRepository
import dev.terashima.yomitorirss.feature.summary.SummaryRepository
import dev.terashima.yomitorirss.feature.summary.data.DefaultSummaryRepository

/** Application-scope AI primitives that do not depend on other feature repositories. */
internal class AppAiCoreRuntimeDependencies(
  private val application: Application,
  private val database: YomitoriDatabase,
) {
  val modelManager: LocalModelManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    LocalModelManager.shared(application)
  }

  val aiModelRepository: AiModelRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultAiModelRepository(application, modelManager)
  }

  val summaryRepository: SummaryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultSummaryRepository(application, database, modelManager)
  }
}
