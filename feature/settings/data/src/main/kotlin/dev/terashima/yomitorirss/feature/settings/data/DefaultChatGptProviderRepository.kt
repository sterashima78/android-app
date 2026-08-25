package dev.terashima.yomitorirss.feature.settings.data

import dev.terashima.yomitorirss.core.aicloudopenai.ChatGptModelInfo
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

  override suspend fun listModels(): List<ChatGptProviderModel> {
    val models = selectChatGptProviderModels(client.listModels())
    val selectedModelId = modelPreferences.selectedModelId()
    if (shouldClearSelectedChatGptModel(selectedModelId, models)) {
      modelPreferences.clearSelection()
    }
    return models
  }
}

internal fun shouldClearSelectedChatGptModel(
  selectedModelId: String?,
  models: List<ChatGptProviderModel>,
): Boolean = selectedModelId != null && models.none { it.id == selectedModelId }

internal fun selectChatGptProviderModels(models: List<ChatGptModelInfo>): List<ChatGptProviderModel> = models
  .asSequence()
  .filter { it.visibleInPicker && it.supportedInApi && it.supportsWebSearch }
  .map { model ->
    ChatGptProviderModel(
      id = model.id,
      name = model.displayName,
      description = model.description,
      supportsWebSearch = model.supportsWebSearch,
    )
  }
  .toList()
