package dev.terashima.yomitorirss.core.aicloudopenai

import android.content.Context

class ChatGptModelPreferences(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun selectedModelId(): String? = preferences.getString(KEY_SELECTED_MODEL_ID, null)?.takeIf(String::isNotBlank)

  fun selectModel(modelId: String) {
    require(modelId.isNotBlank()) { "ChatGPT model id must not be blank" }
    preferences.edit().putString(KEY_SELECTED_MODEL_ID, modelId).apply()
  }

  fun clearSelection() {
    preferences.edit().remove(KEY_SELECTED_MODEL_ID).apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "chatgpt_ai_settings"
    const val KEY_SELECTED_MODEL_ID = "selected_model_id"
  }
}
