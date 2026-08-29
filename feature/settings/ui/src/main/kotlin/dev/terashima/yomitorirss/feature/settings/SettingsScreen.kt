package dev.terashima.yomitorirss.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRepository
import dev.terashima.yomitorirss.feature.aitaskqueue.AiTaskQueueRoute
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.backup.GoogleDriveBackupDialog
import dev.terashima.yomitorirss.feature.summary.SummaryPromptDialog

private enum class SettingsOverlay {
  MODELS,
  CHAT_GPT_DEBUG,
  AI_EXECUTION_SETTINGS,
  SUMMARY_PROMPT,
  AI_TASK_QUEUE,
  DRIVE_BACKUP,
}

@Composable
fun SettingsFeatureScreen(
  modifier: Modifier,
  backupViewModel: BackupViewModel,
  aiSettingsViewModel: AiSettingsViewModel,
  aiTaskQueueRepository: AiTaskQueueRepository,
  initialBackgroundFetchWifiOnly: Boolean,
  onBackgroundFetchWifiOnlyChange: (Boolean) -> Unit,
  biometricLockEnabled: Boolean,
  onBiometricLockEnabledChange: (Boolean) -> Unit,
  onSelectBackupFolder: (String?) -> Unit,
  onExportBackup: () -> Unit,
  onImportBackup: () -> Unit,
  onOpenWebServer: () -> Unit,
) {
  val backupState by backupViewModel.state.collectAsState()
  val aiState by aiSettingsViewModel.state.collectAsState()
  var overlay by remember { mutableStateOf<SettingsOverlay?>(null) }
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
      overlay = SettingsOverlay.MODELS
    },
    onOpenChatGptDebug = {
      aiSettingsViewModel.prepareChatGptDebug()
      overlay = SettingsOverlay.CHAT_GPT_DEBUG
    },
    onOpenAiExecutionSettings = {
      aiSettingsViewModel.prepareChatGptDebug()
      overlay = SettingsOverlay.AI_EXECUTION_SETTINGS
    },
    onOpenSummaryPrompt = { overlay = SettingsOverlay.SUMMARY_PROMPT },
    onOpenAiTaskQueue = { overlay = SettingsOverlay.AI_TASK_QUEUE },
    onOpenDriveBackup = {
      backupViewModel.refreshStatus()
      overlay = SettingsOverlay.DRIVE_BACKUP
    },
    onExportBackup = onExportBackup,
    onImportBackup = onImportBackup,
    onOpenWebServer = onOpenWebServer,
  )

  when (overlay) {
    SettingsOverlay.MODELS -> ModelManagerDialog(
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
      onDismiss = { overlay = null },
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

    SettingsOverlay.CHAT_GPT_DEBUG -> ChatGptDebugDialog(
      state = aiState,
      onDismiss = { overlay = null },
      onStartLogin = aiSettingsViewModel::startChatGptLogin,
      onPollLogin = aiSettingsViewModel::pollChatGptLogin,
      onLogout = aiSettingsViewModel::logoutChatGpt,
      onRefreshModels = aiSettingsViewModel::refreshChatGptModels,
      onSelectModel = aiSettingsViewModel::selectChatGptModel,
      onPromptChange = aiSettingsViewModel::setChatGptPrompt,
      onRunInference = aiSettingsViewModel::runChatGptDebugInference,
    )

    SettingsOverlay.AI_EXECUTION_SETTINGS -> AiExecutionSettingsScreen(
      state = aiState,
      onDismiss = { overlay = null },
      onSummaryProviderChange = aiSettingsViewModel::setSummaryExecutionProvider,
      onKnowledgeProviderChange = aiSettingsViewModel::setKnowledgeExecutionProvider,
    )

    SettingsOverlay.SUMMARY_PROMPT -> SummaryPromptDialog(
      prompt = aiState.summaryPrompt,
      onDismiss = { overlay = null },
      onSave = {
        overlay = null
        aiSettingsViewModel.updateSummaryPrompt(it)
      },
      onReset = {
        overlay = null
        aiSettingsViewModel.resetSummaryPrompt()
      },
    )

    SettingsOverlay.AI_TASK_QUEUE -> AiTaskQueueRoute(
      repository = aiTaskQueueRepository,
      onDismiss = { overlay = null },
    )

    SettingsOverlay.DRIVE_BACKUP -> GoogleDriveBackupDialog(
      state = backupState,
      onDismiss = { overlay = null },
      onSelectFolder = { onSelectBackupFolder(backupState.folderUri) },
      onBackupNow = backupViewModel::backupToGoogleDriveNow,
      onWifiOnlyChange = backupViewModel::setGoogleDriveWifiOnly,
      onDisable = backupViewModel::disableGoogleDrive,
    )

    null -> Unit
  }
}
