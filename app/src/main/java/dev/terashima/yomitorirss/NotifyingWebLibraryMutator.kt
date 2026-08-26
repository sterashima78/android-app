package dev.terashima.yomitorirss

import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataRefreshResult
import dev.terashima.yomitorirss.feature.library.WebLibraryMutator

internal class NotifyingWebLibraryMutator(
  private val delegate: WebLibraryMutator,
  private val onChanged: () -> Unit,
) : WebLibraryMutator {
  override suspend fun addWebBook(url: String, titleHint: String?): LibraryBook =
    delegate.addWebBook(url, titleHint).also { onChanged() }

  override suspend fun refreshWebBook(book: LibraryBook): LibraryBook =
    delegate.refreshWebBook(book).also { onChanged() }

  override suspend fun refreshWebBookWithReport(book: LibraryBook): WebLibraryMetadataRefreshResult =
    delegate.refreshWebBookWithReport(book).also { onChanged() }

  override suspend fun removeWebBook(book: LibraryBook) {
    delegate.removeWebBook(book)
    onChanged()
  }
}
