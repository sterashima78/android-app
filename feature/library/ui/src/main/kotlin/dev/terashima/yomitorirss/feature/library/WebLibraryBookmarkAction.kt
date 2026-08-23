package dev.terashima.yomitorirss.feature.library

import androidx.compose.runtime.staticCompositionLocalOf

internal val LocalWebLibraryMoveToBookmarkHandler =
  staticCompositionLocalOf<((LibraryBook) -> Unit)?> { null }

internal fun LibraryBook.canMoveToBookmark(): Boolean = source == LibrarySource.WEB
