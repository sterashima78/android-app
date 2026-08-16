package dev.terashima.yomitorirss.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.feature.settings.AiContextBenchmarkReport
import dev.terashima.yomitorirss.feature.settings.AiInferenceBackend
import dev.terashima.yomitorirss.feature.settings.AiInferenceSettings
import dev.terashima.yomitorirss.feature.settings.AiModelBenchmarkComparison
import dev.terashima.yomitorirss.feature.settings.AiModelStatus
import dev.terashima.yomitorirss.feature.settings.AiSummaryProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
) {
  val application = LocalContext.current.applicationContext as YomitoriApplication
  val aiModelRepository = application.container.aiModelRepository
  val inferenceSettings by aiModelRepository.inferenceSettings.collectAsState(AiInferenceSettings())
  val selectedModel = models.firstOrNull(AiModelStatus::selected)
  val scope = rememberCoroutineScope()
  var benchmarkRunning by remember { mutableStateOf(false) }
  var benchmarkResult by remember { mutableStateOf<AiModelBenchmarkComparison?>(null) }
  var benchmarkError by remember { mutableStateOf<String?>(null) }
  var contextBenchmarkResult by remember { mutableStateOf<AiContextBenchmarkReport?>(null) }
  var contextBenchmarkError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(
    selectedModel?.id,
    inferenceSettings.backend,
    inferenceSettings.speculativeDecodingEnabled,
  ) {
    contextBenchmarkResult = null
    contextBenchmarkError = null
    contextBenchmarkResult = withContext(Dispatchers.IO) {
      aiModelRepository.lastContextBenchmark()
    }
  }

  fun clearBenchmark() {
    benchmarkResult = null
    benchmarkError = null
    contextBenchmarkError = null
  }

  dev.terashima.yomitorirss.feature.settings.ModelManagerDialog(
    supported = supported,
    models = models,
    inferenceBackend = inferenceBackend,
    thinkingEnabled = thinkingEnabled,
    speculativeDecodingEnabled = inferenceSettings.speculativeDecodingEnabled,
    contextSizeMode = inferenceSettings.contextSizeMode,
    effectiveContextTokens = selectedModel?.contextTokens,
    benchmarkRunning = benchmarkRunning,
    benchmarkResult = benchmarkResult,
    benchmarkError = benchmarkError,
    contextBenchmarkResult = contextBenchmarkResult,
    contextBenchmarkError = contextBenchmarkError,
    progressModelId = progressModelId,
    progressText = progressText,
    onDismiss = onDismiss,
    onBackendChange = { backend ->
      clearBenchmark()
      contextBenchmarkResult = null
      onBackendChange(backend)
    },
    onThinkingChange = onThinkingChange,
    onSpeculativeDecodingChange = { enabled ->
      clearBenchmark()
      contextBenchmarkResult = null
      aiModelRepository.setSpeculativeDecodingEnabled(enabled)
    },
    onContextSizeChange = { mode ->
      clearBenchmark()
      aiModelRepository.setContextSizeMode(mode)
    },
    onRunBenchmark = {
      if (!benchmarkRunning) {
        benchmarkRunning = true
        clearBenchmark()
        scope.launch {
          runCatching { aiModelRepository.benchmarkSelectedModel() }
            .onSuccess { benchmarkResult = it }
            .onFailure { error -> benchmarkError = error.userMessage() }
          benchmarkRunning = false
        }
      }
    },
    onRunContextBenchmark = {
      if (!benchmarkRunning) {
        benchmarkRunning = true
        clearBenchmark()
        scope.launch {
          runCatching { aiModelRepository.benchmarkSelectedModelContexts() }
            .onSuccess { contextBenchmarkResult = it }
            .onFailure { error -> contextBenchmarkError = error.userMessage() }
          benchmarkRunning = false
        }
      }
    },
    onDownload = onDownload,
    onSelect = { modelId ->
      clearBenchmark()
      contextBenchmarkResult = null
      onSelect(modelId)
    },
    onDelete = { modelId ->
      clearBenchmark()
      contextBenchmarkResult = null
      onDelete(modelId)
    },
  )
}

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

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
