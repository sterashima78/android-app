package dev.terashima.yomitorirss.entry

import dev.terashima.yomitorirss.composition.entry.AddedSharedWebBook
import dev.terashima.yomitorirss.composition.entry.SharedBookmarkSaveOutcome
import dev.terashima.yomitorirss.composition.entry.SharedContentEntryCapability

class IncomingIntentDependencies internal constructor(
  private val sharedContentEntry: SharedContentEntryCapability,
) {
  suspend fun saveSharedArticle(
    url: String,
    title: String,
    sourceTitle: String,
  ): SharedBookmarkSaveOutcome = sharedContentEntry.saveBookmark(url, title, sourceTitle)

  suspend fun addSharedWebBook(
    url: String,
    title: String,
  ): AddedSharedWebBook = sharedContentEntry.addWebBook(url, title)
}
