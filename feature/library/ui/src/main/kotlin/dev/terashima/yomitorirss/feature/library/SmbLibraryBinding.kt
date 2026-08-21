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
    normalizationBusy = binding.state.smbMetadataNormalizationBusy,
    normalization = binding.state.smbMetadataNormalization,
    onSync = binding.onSync,
    onSave = binding.onSave,
    onDelete = binding.onDelete,
    onEnqueueCovers = binding.onEnqueueCovers,
    onRetryFailedCovers = binding.onRetryFailedCovers,
    onStartMetadataNormalization = binding.onStartMetadataNormalization,
    onApplyMetadataCandidate = binding.onApplyMetadataCandidate,
    onDeferMetadataCandidate = binding.onDeferMetadataCandidate,
    onRejectMetadataCandidate = binding.onRejectMetadataCandidate,
    onReopenMetadataCandidate = binding.onReopenMetadataCandidate,
    onRetryMetadataCandidate = binding.onRetryMetadataCandidate,
  )
}
