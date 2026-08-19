package dev.terashima.yomitorirss.feature.bookmark

import kotlinx.coroutines.flow.StateFlow

interface BookmarkChangeSource {
  val changes: StateFlow<Long>
}

interface BookmarkReader : BookmarkChangeSource {
  suspend fun listSavedArticles(tagId: String?, folderId: String?): List<BookmarkedArticle>
  suspend fun listAllSavedArticles(): List<BookmarkedArticle> = listSavedArticles(tagId = null, folderId = null)
  suspend fun listReadLaterArticles(): List<BookmarkedArticle>
  suspend fun isBookmarked(articleId: String): Boolean
}

interface BookmarkCatalog {
  suspend fun listFolders(): List<BookmarkFolder>
  suspend fun listTags(): List<Tag>
  suspend fun createFolder(name: String)
  suspend fun renameFolder(folderId: String, name: String)
  suspend fun deleteFolder(folderId: String)
  suspend fun createTag(name: String)
  suspend fun renameTag(tagId: String, name: String)
  suspend fun deleteTag(tagId: String)
  suspend fun deleteUnusedTags(): Int
}

interface BookmarkMutator {
  suspend fun moveArticleToFolder(articleId: String, folderId: String?)
  suspend fun replaceArticleTags(articleId: String, tagIds: Set<String>)
  suspend fun saveAndReadArticle(articleId: String)
  suspend fun markReadLater(articleId: String)
  suspend fun unsaveArticle(articleId: String)
  suspend fun removeReadLater(articleId: String)
}

interface SharedBookmarkSaver {
  suspend fun saveSharedArticle(url: String, title: String, sourceTitle: String): BookmarkSaveResult
  suspend fun saveSharedArticleToFolder(
    url: String,
    title: String,
    sourceTitle: String,
    folderId: String,
  ): BookmarkSaveResult
}

interface BookmarkRepository : BookmarkReader, BookmarkCatalog, BookmarkMutator, SharedBookmarkSaver

/** UI-facing import entry point retained while orchestration lives in [ImportBookmarksUseCase]. */
interface BookmarkImportRepository {
  suspend fun importBookmarkCsv(documentUri: String): BookmarkImportResult
  suspend fun importBookmarkHtml(documentUri: String): BookmarkImportResult
}
