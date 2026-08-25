package dev.terashima.yomitorirss.feature.settings

data class ChatGptProviderModel(
  val id: String,
  val name: String,
  val description: String?,
  val supportsWebSearch: Boolean,
)

interface ChatGptProviderRepository {
  fun selectedModelId(): String?
  fun selectModel(modelId: String)
  suspend fun listModels(): List<ChatGptProviderModel>
}
