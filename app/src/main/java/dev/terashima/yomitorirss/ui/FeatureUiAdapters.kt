package dev.terashima.yomitorirss.ui

import androidx.compose.runtime.Composable
import dev.terashima.yomitorirss.feature.settings.AiSummaryProgress

@Composable
internal fun SummaryPromptDialog(
  prompt: String,
  onDismiss: () -> Unit,
  onSave: (String) -> Unit,
  onReset: () -> Unit,
) = dev.terashima.yomitorirss.feature.summary.SummaryPromptDialog(
  prompt = prompt,
  onDismiss = onDismiss,
  onSave = onSave,
  onReset = onReset,
)

internal fun summaryProgressLabel(progress: AiSummaryProgress): String =
  dev.terashima.yomitorirss.feature.summary.summaryProgressLabel(
    stage = progress.stage,
    modelName = progress.modelName,
  )
