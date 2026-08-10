package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.Context
import android.net.Uri
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkFolder
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportResult
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.bookmark.Tag
import kotlinx.coroutines.flow.StateFlow

class DefaultBookmarkRepository(
  database: DatabaseConnection,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier(),
) : BookmarkRepository {
  private val store = BookmarkStore(database)
  override val changes: StateFlow<Long> = dataChanges.version

  override suspend fun listSavedArticles(tagId: String?, folderId: String?): List<BookmarkedArticle> =
    store.listSavedArticles(tagId, folderId)

  override suspend fun listReadLaterArticles(): List<BookmarkedArticle> = store.listReadLaterArticles()

  override suspend fun isBookmarked(articleId: String): Boolean = store.isBookmarked(articleId)

  override suspend fun listFolders(): List<BookmarkFolder> = store.listFolders()

  override suspend fun listTags(): List<Tag> = store.listTags()

  override suspend fun createFolder(name: String) {
    store.createFolder(name)
    dataChanges.notifyChanged()
  }

  override suspend fun renameFolder(folderId: String, name: String) {
    store.renameFolder(folderId, name)
    dataChanges.notifyChanged()
  }

  override suspend fun deleteFolder(folderId: String) {
    store.deleteFolder(folderId)
    dataChanges.notifyChanged()
  }

  override suspend fun moveArticleToFolder(articleId: String, folderId: String?) {
    store.moveArticleToFolder(articleId, folderId)
    dataChanges.notifyChanged()
  }

  override suspend fun createTag(name: String) {
    store.createTag(name)
    dataChanges.notifyChanged()
  }

  override suspend fun renameTag(tagId: String, name: String) {
    store.renameTag(tagId, name)
    dataChanges.notifyChanged()
  }

  override suspend fun deleteTag(tagId: String) {
    store.deleteTag(tagId)
    dataChanges.notifyChanged()
  }

  override suspend fun replaceArticleTags(articleId: String, tagIds: Set<String>) {
    store.replaceArticleTags(articleId, tagIds)
    dataChanges.notifyChanged()
  }

  override suspend fun saveAndReadArticle(articleId: String) {
    store.saveAndReadArticle(articleId)
    dataChanges.notifyChanged()
  }

  override suspend fun markReadLater(articleId: String) {
    store.markReadLater(articleId)
    dataChanges.notifyChanged()
  }

  override suspend fun unsaveArticle(articleId: String) {
    store.unsaveArticle(articleId)
    dataChanges.notifyChanged()
  }

  override suspend fun removeReadLater(articleId: String) {
    store.removeReadLater(articleId)
    dataChanges.notifyChanged()
  }

  override suspend fun saveSharedArticle(
    url: String,
    title: String,
    sourceTitle: String,
  ): BookmarkSaveResult = store.saveSharedArticle(url, title, sourceTitle).also {
    dataChanges.notifyChanged()
  }
}

class DefaultBookmarkImportRepository(
  context: Context,
  database: DatabaseConnection,
  private val dataChanges: DataChangeNotifier,
) : BookmarkImportRepository {
  private val appContext = context.applicationContext
  private val store = BookmarkStore(database)

  override suspend fun importBookmarkCsv(documentUri: String): BookmarkImportResult =
    openReader(documentUri, "CSVファイルを開けませんでした") { reader ->
      val parsed = parseBookmarkCsv(reader)
      store.importBookmarks(
        entries = parsed.entries.map { entry ->
          ImportedBookmarkEntry(entry.title, entry.url, entry.createdAt, entry.sourceTitle, entry.tagNames)
        },
        skipped = parsed.skippedRows,
        identityPrefix = "csv",
      )
    }.also { dataChanges.notifyChanged() }

  override suspend fun importBookmarkHtml(documentUri: String): BookmarkImportResult =
    openReader(documentUri, "HTMLファイルを開けませんでした") { reader ->
      val parsed = parseBookmarkHtml(reader)
      store.importBookmarks(
        entries = parsed.entries.map { entry ->
          ImportedBookmarkEntry(entry.title, entry.url, entry.createdAt, entry.sourceTitle, entry.tagNames)
        },
        skipped = parsed.skippedEntries,
        identityPrefix = "html",
      )
    }.also { dataChanges.notifyChanged() }

  private fun <T> openReader(
    documentUri: String,
    errorMessage: String,
    block: (java.io.Reader) -> T,
  ): T = appContext.contentResolver.openInputStream(Uri.parse(documentUri))
    ?.bufferedReader(Charsets.UTF_8)
    ?.use(block)
    ?: error(errorMessage)
}
