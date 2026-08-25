package dev.terashima.yomitorirss.feature.knowledge.data

import android.content.Context
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionProvider
import dev.terashima.yomitorirss.feature.knowledge.KnowledgeExecutionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KnowledgeExecutionPreferences(
  context: Context,
  private val onProviderChanged: () -> Unit = {},
) : KnowledgeExecutionSettings {
  private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val _provider = MutableStateFlow(readProvider())

  override val provider: StateFlow<KnowledgeExecutionProvider> = _provider.asStateFlow()

  override fun currentProvider(): KnowledgeExecutionProvider = _provider.value

  override fun setProvider(provider: KnowledgeExecutionProvider) {
    if (_provider.value == provider) return
    preferences.edit().putString(KEY_PROVIDER, provider.name).apply()
    _provider.value = provider
    onProviderChanged()
  }

  private fun readProvider(): KnowledgeExecutionProvider = preferences.getString(KEY_PROVIDER, null)
    ?.let { saved -> KnowledgeExecutionProvider.entries.firstOrNull { it.name == saved } }
    ?: KnowledgeExecutionProvider.LOCAL

  private companion object {
    const val PREFERENCES_NAME = "knowledge_execution"
    const val KEY_PROVIDER = "provider"
  }
}
