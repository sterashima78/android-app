package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptOpenAiClient
import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.airuntime.LocalAiTextInference
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.settings.AiModelRepository
import dev.terashima.yomitorirss.feature.settings.ChatGptDebugRepository
import dev.terashima.yomitorirss.feature.settings.data.DefaultAiModelRepository
import dev.terashima.yomitorirss.feature.settings.data.DefaultChatGptDebugRepository
import dev.terashima.yomitorirss.feature.summary.SummaryPromptSettings
import dev.terashima.yomitorirss.feature.summary.SummaryRepository
import dev.terashima.yomitorirss.feature.summary.data.DefaultSummaryRepository
import dev.terashima.yomitorirss.feature.summary.data.SummaryPromptStore

/** Application-scope AI primitives that do not depend on other feature repositories. */
internal class AppAiCoreRuntimeDependencies(
  private val application: Application,
  private val database: YomitoriDatabase,
  private val httpClient: HttpClient,
) {
  val modelManager: LocalModelManager by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    LocalModelManager.shared(application)
  }

  val textInference: AiTextInference by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    LocalAiTextInference(modelManager)
  }

  val aiModelRepository: AiModelRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultAiModelRepository(application, modelManager)
  }

  val chatGptDebugRepository: ChatGptDebugRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultChatGptDebugRepository(
      ChatGptOpenAiClient.create(application, httpClient),
    )
  }

  val summaryPromptSettings: SummaryPromptSettings by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SummaryPromptStore(application)
  }

  val summaryRepository: SummaryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultSummaryRepository(application, database, textInference)
  }
}
