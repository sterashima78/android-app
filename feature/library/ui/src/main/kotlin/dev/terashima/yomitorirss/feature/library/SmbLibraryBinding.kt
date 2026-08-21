package dev.terashima.yomitorirss.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

internal data class SmbLibraryUiBinding(
  val state: LibraryUiState,
  val onSync: () -> Unit,
  val onSave: (SmbServerSettings, String?) -> Unit,
  val onDelete: (String) -> Unit,
  val onEnqueueCovers: () -> Unit,
  val onRetryFailedCovers: () -> Unit,
  val onRescheduleCovers: () -> Unit,
  val onStartMetadataNormalization: () -> Unit,
  val onApplyMetadataCandidate: (String, String, SmbBookMetadataProposal) -> Unit,
  val onDeferMetadataCandidate: (String) -> Unit,
  val onRejectMetadataCandidate: (String) -> Unit,
  val onReopenMetadataCandidate: (String) -> Unit,
  val onRetryMetadataCandidate: (String) -> Unit,
)

internal val LocalSmbLibraryUiBinding = staticCompositionLocalOf<SmbLibraryUiBinding?> { null }

@Composable
internal fun SmbLibrarySettingsFromBinding() {
  val binding = LocalSmbLibraryUiBinding.current ?: return
  SmbLibrarySettingsSection(
    servers = binding.state.smbServers,
    busy = binding.state.smbSettingsBusy,
    syncing = binding.state.smbSyncing,
    coverPrefetchBusy = binding.state.smbCoverPrefetchBusy,
    coverPrefetch = binding.state.smbCoverPrefetch,
    onSync = binding.onSync,
    onSave = binding.onSave,
    onDelete = binding.onDelete,
    onEnqueueCovers = binding.onEnqueueCovers,
    onRetryFailedCovers = binding.onRetryFailedCovers,
    onRescheduleCovers = binding.onRescheduleCovers,
  )
  SmbMetadataNormalizationSettingsSection(
    enabled = binding.state.smbServers.isNotEmpty() && !binding.state.smbSyncing,
    busy = binding.state.smbMetadataNormalizationBusy,
    snapshot = binding.state.smbMetadataNormalization,
    onStart = binding.onStartMetadataNormalization,
    onApply = binding.onApplyMetadataCandidate,
    onDefer = binding.onDeferMetadataCandidate,
    onReject = binding.onRejectMetadataCandidate,
    onReopen = binding.onReopenMetadataCandidate,
    onRetry = binding.onRetryMetadataCandidate,
  )
}
