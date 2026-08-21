package dev.terashima.yomitorirss.feature.library.data

import android.content.Context
import dev.terashima.yomitorirss.feature.library.DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationPromptRepository
import dev.terashima.yomitorirss.feature.library.normalizeSmbMetadataNormalizationPrompt

class SharedPreferencesSmbMetadataNormalizationPromptRepository(
  context: Context,
) : SmbMetadataNormalizationPromptRepository {
  private val preferences = context.applicationContext.getSharedPreferences(
    PREFERENCES_NAME,
    Context.MODE_PRIVATE,
  )

  override fun prompt(): String = preferences.getString(PROMPT_KEY, null)
    ?.let { runCatching { normalizeSmbMetadataNormalizationPrompt(it) }.getOrNull() }
    ?: DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT

  override fun update(prompt: String) {
    val normalized = normalizeSmbMetadataNormalizationPrompt(prompt)
    preferences.edit().putString(PROMPT_KEY, normalized).apply()
  }

  override fun reset() {
    preferences.edit().remove(PROMPT_KEY).apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "library_ai_preferences"
    const val PROMPT_KEY = "smb_metadata_normalization_prompt"
  }
}
