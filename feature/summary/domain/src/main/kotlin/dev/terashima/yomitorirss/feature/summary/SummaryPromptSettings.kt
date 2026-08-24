package dev.terashima.yomitorirss.feature.summary

import kotlinx.coroutines.flow.Flow

/** Summary-owned mutable settings contract for the user-editable summarization prompt. */
interface SummaryPromptSettings {
  val prompt: Flow<String>

  fun update(prompt: String)

  fun reset()
}
