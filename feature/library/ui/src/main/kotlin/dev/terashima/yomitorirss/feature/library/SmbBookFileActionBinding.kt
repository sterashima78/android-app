package dev.terashima.yomitorirss.feature.library

import androidx.compose.runtime.staticCompositionLocalOf

internal data class SmbBookFileActionBinding(
  val onRename: (LibraryBook, String) -> Unit,
  val onDelete: (LibraryBook) -> Unit,
)

internal val LocalSmbBookFileActionBinding = staticCompositionLocalOf<SmbBookFileActionBinding?> { null }
