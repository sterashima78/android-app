package dev.terashima.yomitorirss.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.terashima.yomitorirss.feature.backup.BackupViewModel
import dev.terashima.yomitorirss.feature.backup.GoogleDriveBackupDialog
import dev.terashima.yomitorirss.feature.bookmark.BookmarkViewModel
import dev.terashima.yomitorirss.feature.navigation.MainTab
import dev.terashima.yomitorirss.feature.settings.AiModelStatus
import dev.terashima.yomitorirss.feature.settings.AiSettingsViewModel
import dev.terashima.yomitorirss.feature.settings.ModelManagerDialog
import dev.terashima.yomitorirss.feature.settings.SettingsScreen
import dev.terashima.yomitorirss.feature.x.XViewerCssRepository
import dev.terashima.yomitorirss.feature.x.XViewerCssSettingsSheet
import java.time.LocalDate

@Composable
internal fun SettingsRoute(
  modifier: Modifier,
  bookmarkViewModel: BookmarkViewModel,
  backupViewModel: BackupViewModel,
  aiSettingsViewModel: AiSettingsViewModel,
  xViewerCssRepository: XViewerCssRepository,
  onOpenWebServer: () -> Unit,
  onNavigate: (MainTab) -> Unit,
) {
  val bookmarkState by bookmarkViewModel.state.collectAsState()
  val backupState by backupViewModel.state.collectAsState()
  val aiState by aiSettingsViewModel.state.collectAsState()
  var showModels by remember { mutableStateOf(false) }
  var showSummaryPrompt by remember { mutableStateOf(false) }
  var showBackup by remember { mutableStateOf(false) }
  var showXCssSettings by remember { mutableStateOf(false) }

  val exportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip"),
  ) { uri -> uri?.toString()?.let(backupViewModel::exportBackup) }
  val importLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(backupViewModel::importBackup) }
  val bookmarkCsvImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(bookmarkViewModel::importCsv) }
  val bookmarkHtmlImportLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument(),
  ) { uri -> uri?.toString()?.let(bookmarkViewModel::importHtml) }
  val backupFolderLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocumentTree(),
  ) { uri -> uri?.toString()?.let(backupViewModel::configureGoogleDrive) }

  LaunchedEffect(backupState.restoreCompleted) {
    if (backupState.restoreCompleted) {
      onNavigate(MainTab.INTEGRATED)
      backupViewModel.consumeRestoreCompleted()
    }
  }
  LaunchedEffect(bookmarkState.importCompleted) {
    if (bookmarkState.importCompleted) {
      onNavigate(MainTab.SAVED)
      bookmarkViewModel.consumeImportCompleted()
    }
  }

  SettingsScreen(
    modifier = modifier,
    tagCount = bookmarkState.tags.size,
    onImportBookmarkCsv = {
      bookmarkCsvImportLauncher.launch(
        arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain"),
      )
    },
    onImportBookmarkHtml = {
      bookmarkHtmlImportLauncher.launch(
        arrayOf("text/html", "application/xhtml+xml", "text/plain"),
      )
    },
    onOpenXCss = { showXCssSettings = true },
    onOpenModels = {
      aiSettingsViewModel.prepareModelManager()
      showModels = true
    },
    onOpenSummaryPrompt = { showSummaryPrompt = true },
    onOpenDriveBackup = {
      backupViewModel.refreshStatus()
      showBackup = true
    },
    onExportBackup = {
      exportLauncher.launch("mosaic-backup-${LocalDate.now()}.zip")
    },
    onImportBackup = {
      importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
    },
    onOpenWebServer = onOpenWebServer,
  )

  if (showXCssSettings) {
    XViewerCssSettingsSheet(
      repository = xViewerCssRepository,
      onDismiss = { showXCssSettings = false },
    )
  }
  if (showModels) {
    ModelManagerDialog(
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
      onDismiss = { showModels = false },
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
  }
  if (showSummaryPrompt) {
    SummaryPromptDialog(
      prompt = aiState.summaryPrompt,
      onDismiss = { showSummaryPrompt = false },
      onSave = {
        showSummaryPrompt = false
        aiSettingsViewModel.updateSummaryPrompt(it)
      },
      onReset = {
        showSummaryPrompt = false
        aiSettingsViewModel.resetSummaryPrompt()
      },
    )
  }
  if (showBackup) {
    GoogleDriveBackupDialog(
      state = backupState,
      onDismiss = { showBackup = false },
      onSelectFolder = {
        val initialUri = backupState.folderUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        backupFolderLauncher.launch(initialUri)
      },
      onBackupNow = backupViewModel::backupToGoogleDriveNow,
      onDisable = backupViewModel::disableGoogleDrive,
    )
  }
}
