package dev.terashima.yomitorirss.feature.summary

import kotlinx.coroutines.flow.StateFlow

enum class SummaryExecutionProvider {
  LOCAL,
  CHATGPT,
}

interface SummaryExecutionSettings {
  val provider: StateFlow<SummaryExecutionProvider>
  fun currentProvider(): SummaryExecutionProvider
  fun setProvider(provider: SummaryExecutionProvider)
}
