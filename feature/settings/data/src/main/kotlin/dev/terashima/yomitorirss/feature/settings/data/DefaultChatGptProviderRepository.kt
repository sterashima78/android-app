package dev.terashima.yomitorirss.feature.settings.data

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptModelPreferences
import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptOpenAiClient
import dev.terashima.yomitorirss.feature.settings.ChatGptProviderModel
import dev.terashima.yomitorirss.feature.settings.ChatGptProviderRepository

class DefaultChatGptProviderRepository(
  private val client: ChatGptOpenAiClient,
  private val modelPreferences: ChatGptModelPreferences,
) : ChatGptProviderRepository {
  override fun selectedModelId(): String? = modelPreferences.selectedModelId()

  override fun selectModel(modelId: String) {
    modelPreferences.selectModel(modelId)
  }

  override suspend fun listModels(): List<ChatGptProviderModel> = client.listModels()
    .asSequence()
    .filter { it.visibleInPicker && it.supportedInApi }
    .map { model ->
      ChatGptProviderModel(
        id = model.id,
        name = model.displayName,
        description = model.description,
        supportsWebSearch = true,
      )
    }
    .toList()
}
