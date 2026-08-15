package dev.terashima.yomitorirss.ui

import androidx.compose.runtime.Composable
import dev.terashima.yomitorirss.feature.settings.AiInferenceBackend
import dev.terashima.yomitorirss.feature.settings.AiModelStatus
import dev.terashima.yomitorirss.feature.settings.AiSummaryProgress

@Composable
internal fun ModelManagerDialog(
  supported: Boolean,
  models: List<AiModelStatus>,
  inferenceBackend: AiInferenceBackend,
  thinkingEnabled: Boolean,
  progressModelId: String?,
  progressText: String?,
  onDismiss: () -> Unit,
  onBackendChange: (AiInferenceBackend) -> Unit,
  onThinkingChange: (Boolean) -> Unit,
  onDownload: (String) -> Unit,
  onSelect: (String) -> Unit,
  onDelete: (String) -> Unit,
) = dev.terashima.yomitorirss.feature.settings.ModelManagerDialog(
  supported = supported,
  models = models,
  inferenceBackend = inferenceBackend,
  thinkingEnabled = thinkingEnabled,
  progressModelId = progressModelId,
  progressText = progressText,
  onDismiss = onDismiss,
  onBackendChange = onBackendChange,
  onThinkingChange = onThinkingChange,
  onDownload = onDownload,
  onSelect = onSelect,
  onDelete = onDelete,
)

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
