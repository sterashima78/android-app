package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import dev.terashima.yomitorirss.feature.summary.DEFAULT_SUMMARY_PROMPT
import dev.terashima.yomitorirss.feature.summary.normalizeSummaryPrompt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SummaryPromptStore(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val _prompt = MutableStateFlow(
    preferences.getString(SUMMARY_PROMPT_KEY, null)
      ?.let { runCatching { normalizeSummaryPrompt(it) }.getOrNull() }
      ?: DEFAULT_SUMMARY_PROMPT,
  )
  val prompt: StateFlow<String> = _prompt.asStateFlow()

  fun update(prompt: String) {
    val normalized = normalizeSummaryPrompt(prompt)
    preferences.edit().putString(SUMMARY_PROMPT_KEY, normalized).apply()
    _prompt.value = normalized
  }

  fun reset() {
    preferences.edit().remove(SUMMARY_PROMPT_KEY).apply()
    _prompt.value = DEFAULT_SUMMARY_PROMPT
  }

  private companion object {
    // Keep the legacy preference location so existing installations retain their custom prompt.
    const val PREFERENCES_NAME = "local_summary_models"
    const val SUMMARY_PROMPT_KEY = "summary_prompt"
  }
}
