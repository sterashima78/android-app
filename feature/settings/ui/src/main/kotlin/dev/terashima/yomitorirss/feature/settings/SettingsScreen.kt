package dev.terashima.yomitorirss.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

private enum class SettingsLocalOverlay {
  MODELS,
  CHAT_GPT_DEBUG,
  AI_EXECUTION_SETTINGS,
}

@Composable
fun SettingsFeatureScreen(
  modifier: Modifier,
  aiSettingsViewModel: AiSettingsViewModel,
  initialBackgroundFetchWifiOnly: Boolean,
  onBackgroundFetchWifiOnlyChange: (Boolean) -> Unit,
  biometricLockEnabled: Boolean,
  onBiometricLockEnabledChange: (Boolean) -> Unit,
  onOpenSummaryPrompt: () -> Unit,
  onOpenAiTaskQueue: () -> Unit,
  onOpenDriveBackup: () -> Unit,
  onExportBackup: () -> Unit,
  onImportBackup: () -> Unit,
  onOpenWebServer: () -> Unit,
) {
  val aiState by aiSettingsViewModel.state.collectAsState()
  var localOverlay by remember { mutableStateOf<SettingsLocalOverlay?>(null) }
  var backgroundFetchWifiOnly by remember(initialBackgroundFetchWifiOnly) {
    mutableStateOf(initialBackgroundFetchWifiOnly)
  }

  SettingsContent(
    modifier = modifier,
    backgroundFetchWifiOnly = backgroundFetchWifiOnly,
    onBackgroundFetchWifiOnlyChange = { wifiOnly ->
      backgroundFetchWifiOnly = wifiOnly
      onBackgroundFetchWifiOnlyChange(wifiOnly)
    },
    biometricLockEnabled = biometricLockEnabled,
    onBiometricLockEnabledChange = onBiometricLockEnabledChange,
    onOpenModels = {
      aiSettingsViewModel.prepareModelManager()
      localOverlay = SettingsLocalOverlay.MODELS
    },
    onOpenChatGptDebug = {
      aiSettingsViewModel.prepareChatGptDebug()
      localOverlay = SettingsLocalOverlay.CHAT_GPT_DEBUG
    },
    onOpenAiExecutionSettings = {
      aiSettingsViewModel.prepareChatGptDebug()
      localOverlay = SettingsLocalOverlay.AI_EXECUTION_SETTINGS
    },
    onOpenSummaryPrompt = onOpenSummaryPrompt,
    onOpenAiTaskQueue = onOpenAiTaskQueue,
    onOpenDriveBackup = onOpenDriveBackup,
    onExportBackup = onExportBackup,
    onImportBackup = onImportBackup,
    onOpenWebServer = onOpenWebServer,
  )

  when (localOverlay) {
    SettingsLocalOverlay.MODELS -> ModelManagerDialog(
      supported = aiState.supported,
      models = aiState.models,
      inferenceBackend = aiState.inferenceBackend,
      thinkingEnabled = aiState.thinkingEnabled,
      speculativeDecodingEnabled = aiState.speculativeDecodingEnabled,
      contextSizeMode = aiState.contextSizeMode,
      effectiveContextTokens = aiState.models.firstOrNull(AiModelStatus::selected)?.contextTokens,
      benchmarkRunning = aiState.benchmarkRunning,
      benchmarkResult = aiState.benchmarkResult,
      benchmarkError = aiState.benchmarkError,
      contextBenchmarkResult = aiState.contextBenchmarkResult,
      contextBenchmarkError = aiState.contextBenchmarkError,
      progressModelId = aiState.downloadProgress?.modelId,
      progressText = aiState.downloadProgress?.let {
        val percent = if (it.totalBytes > 0) it.downloadedBytes * 100 / it.totalBytes else 0
        "${it.phase} $percent%"
      },
      onDismiss = { localOverlay = null },
      onBackendChange = aiSettingsViewModel::setInferenceBackend,
      onThinkingChange = aiSettingsViewModel::setThinkingEnabled,
      onSpeculativeDecodingChange = aiSettingsViewModel::setSpeculativeDecodingEnabled,
      onContextSizeChange = aiSettingsViewModel::setContextSizeMode,
      onRunBenchmark = aiSettingsViewModel::runModelBenchmark,
      onRunContextBenchmark = aiSettingsViewModel::runContextBenchmark,
      onDownload = aiSettingsViewModel::downloadModel,
      onSelect = aiSettingsViewModel::selectModel,
      onDelete = aiSettingsViewModel::deleteModel,
    )

    SettingsLocalOverlay.CHAT_GPT_DEBUG -> ChatGptDebugDialog(
      state = aiState,
      onDismiss = { localOverlay = null },
      onStartLogin = aiSettingsViewModel::startChatGptLogin,
      onPollLogin = aiSettingsViewModel::pollChatGptLogin,
      onLogout = aiSettingsViewModel::logoutChatGpt,
      onRefreshModels = aiSettingsViewModel::refreshChatGptModels,
      onSelectModel = aiSettingsViewModel::selectChatGptModel,
      onPromptChange = aiSettingsViewModel::setChatGptPrompt,
      onRunInference = aiSettingsViewModel::runChatGptDebugInference,
    )

    SettingsLocalOverlay.AI_EXECUTION_SETTINGS -> AiExecutionSettingsScreen(
      state = aiState,
      onDismiss = { localOverlay = null },
      onSummaryProviderChange = aiSettingsViewModel::setSummaryExecutionProvider,
      onKnowledgeProviderChange = aiSettingsViewModel::setKnowledgeExecutionProvider,
    )

    null -> Unit
  }
}
