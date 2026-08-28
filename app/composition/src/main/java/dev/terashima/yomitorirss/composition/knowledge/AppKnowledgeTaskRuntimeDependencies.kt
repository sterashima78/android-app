package dev.terashima.yomitorirss.composition.knowledge

import android.app.Application
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildScheduler
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeBuildTaskController
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionSettings
import dev.terashima.yomitorirss.feature.knowledge.data.KnowledgeExecutionPreferences
import dev.terashima.yomitorirss.feature.knowledge.data.WorkManagerKnowledgeBuildTaskController

/** Knowledge execution preferences and background task control at application scope. */
internal class AppKnowledgeTaskRuntimeDependencies(
  private val application: Application,
) {
  val knowledgeExecutionSettings: KnowledgeExecutionSettings by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    KnowledgeExecutionPreferences(application) { knowledgeBuildRuntime.onProviderChanged() }
  }

  private val knowledgeBuildRuntime: WorkManagerKnowledgeBuildTaskController by lazy(
    LazyThreadSafetyMode.SYNCHRONIZED,
  ) {
    WorkManagerKnowledgeBuildTaskController(application, knowledgeExecutionSettings)
  }

  val knowledgeBuildTaskController: KnowledgeBuildTaskController
    get() = knowledgeBuildRuntime

  val knowledgeBuildScheduler: KnowledgeBuildScheduler = KnowledgeBuildScheduler {
    knowledgeBuildRuntime.enqueue()
  }
}
