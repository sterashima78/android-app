package dev.terashima.yomitorirss.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.terashima.yomitorirss.YomitoriApplication
import dev.terashima.yomitorirss.feature.settings.AiInferenceBackend
import dev.terashima.yomitorirss.feature.settings.AiInferenceSettings
import dev.terashima.yomitorirss.feature.settings.AiModelBenchmarkComparison
import dev.terashima.yomitorirss.feature.settings.AiModelStatus
import dev.terashima.yomitorirss.feature.settings.AiSummaryProgress
import kotlinx.coroutines.launch

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
  val scope = rememberCoroutineScope()
  var benchmarkRunning by remember { mutableStateOf(false) }
  var benchmarkResult by remember { mutableStateOf<AiModelBenchmarkComparison?>(null) }
  var benchmarkError by remember { mutableStateOf<String?>(null) }

  fun clearBenchmark() {
    benchmarkResult = null
    benchmarkError = null
  }

  dev.terashima.yomitorirss.feature.settings.ModelManagerDialog(
    supported = supported,
    models = models,
    inferenceBackend = inferenceBackend,
    thinkingEnabled = thinkingEnabled,
    speculativeDecodingEnabled = inferenceSettings.speculativeDecodingEnabled,
    benchmarkRunning = benchmarkRunning,
    benchmarkResult = benchmarkResult,
    benchmarkError = benchmarkError,
    progressModelId = progressModelId,
    progressText = progressText,
    onDismiss = onDismiss,
    onBackendChange = { backend ->
      clearBenchmark()
      onBackendChange(backend)
    },
    onThinkingChange = onThinkingChange,
    onSpeculativeDecodingChange = aiModelRepository::setSpeculativeDecodingEnabled,
    onRunBenchmark = {
      if (!benchmarkRunning) {
        benchmarkRunning = true
        clearBenchmark()
        scope.launch {
          runCatching { aiModelRepository.benchmarkSelectedModel() }
            .onSuccess { benchmarkResult = it }
            .onFailure { error ->
              benchmarkError = generateSequence(error) { it.cause }
                .mapNotNull(Throwable::message)
                .firstOrNull(String::isNotBlank)
                ?: error.javaClass.simpleName
            }
          benchmarkRunning = false
        }
      }
    },
    onDownload = onDownload,
    onSelect = { modelId ->
      clearBenchmark()
      onSelect(modelId)
    },
    onDelete = { modelId ->
      clearBenchmark()
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
