package dev.terashima.yomitorirss.feature.summary.data

import android.content.Context
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionProvider
import dev.terashima.yomitorirss.feature.summary.SummaryExecutionSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SummaryExecutionPreferences(context: Context) : SummaryExecutionSettings {
  private val appContext = context.applicationContext
  private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
  private val _provider = MutableStateFlow(readProvider())

  override val provider: StateFlow<SummaryExecutionProvider> = _provider.asStateFlow()

  override fun currentProvider(): SummaryExecutionProvider = _provider.value

  override fun setProvider(provider: SummaryExecutionProvider) {
    if (_provider.value == provider) return
    preferences.edit().putString(KEY_PROVIDER, provider.name).apply()
    _provider.value = provider
    SummaryQueue.kick(appContext)
  }

  private fun readProvider(): SummaryExecutionProvider = preferences.getString(KEY_PROVIDER, null)
    ?.let { saved -> SummaryExecutionProvider.entries.firstOrNull { it.name == saved } }
    ?: SummaryExecutionProvider.LOCAL

  private companion object {
    const val PREFERENCES_NAME = "summary_execution"
    const val KEY_PROVIDER = "provider"
  }
}
