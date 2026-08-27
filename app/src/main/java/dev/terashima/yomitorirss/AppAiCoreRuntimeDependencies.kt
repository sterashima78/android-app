package dev.terashima.yomitorirss

import android.app.Application
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptInferenceClient
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptModelPreferences
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptOpenAiClient
import dev.terashima.yomitorirss.core.aiinference.AiTextInference
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.airuntime.ProcessIsolatedLocalAiTextInference
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.settings.AiModelRepository
import dev.terashima.yomitorirss.feature.settings.ChatGptDebugRepository
import dev.terashima.yomitorirss.feature.settings.ChatGptProviderRepository
import dev.terashima.yomitorirss.feature.settings.data.DefaultAiModelRepository
import dev.terashima.yomitorirss.feature.settings.data.DefaultChatGptDebugRepository
import dev.terashima.yomitorirss.feature.settings.data.DefaultChatGptProviderRepository
import dev.terashima.yomitorirss.feature.summary.SummaryCloudInference
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionSettings
import dev.terashima.yomitorirss.feature.summary.SummaryPromptSettings
import dev.terashima.yomitorirss.feature.summary.SummaryRepository
import dev.terashima.yomitorirss.feature.summary.data.DefaultSummaryRepository
import dev.terashima.yomitorirss.feature.summary.data.SummaryExecutionPreferences
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
    ProcessIsolatedLocalAiTextInference(application, modelManager)
  }

  private val chatGptClient: ChatGptOpenAiClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    ChatGptOpenAiClient.create(application, httpClient)
  }

  private val chatGptInferenceClient: ChatGptInferenceClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    ChatGptInferenceClient(chatGptClient)
  }

  private val chatGptModelPreferences: ChatGptModelPreferences by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    ChatGptModelPreferences(application)
  }

  val knowledgeCloudTextInference: AiTextInference by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    ChatGptKnowledgeTextInference(chatGptInferenceClient, chatGptModelPreferences)
  }

  val aiModelRepository: AiModelRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultAiModelRepository(application, modelManager)
  }

  val chatGptDebugRepository: ChatGptDebugRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultChatGptDebugRepository(chatGptClient)
  }

  val chatGptProviderRepository: ChatGptProviderRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultChatGptProviderRepository(chatGptClient, chatGptModelPreferences)
  }

  val summaryExecutionSettings: SummaryExecutionSettings by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SummaryExecutionPreferences(application)
  }

  val summaryCloudInference: SummaryCloudInference by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    ChatGptSummaryCloudInference(chatGptInferenceClient, chatGptModelPreferences)
  }

  val summaryPromptSettings: SummaryPromptSettings by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    SummaryPromptStore(application)
  }

  val summaryRepository: SummaryRepository by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    DefaultSummaryRepository(
      context = application,
      database = database,
      textInference = textInference,
      executionSettings = summaryExecutionSettings,
      cloudInference = summaryCloudInference,
    )
  }
}
