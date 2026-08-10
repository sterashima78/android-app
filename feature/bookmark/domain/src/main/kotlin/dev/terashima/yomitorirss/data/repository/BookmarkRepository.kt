package dev.terashima.yomitorirss.feature.bookmark

import kotlinx.coroutines.flow.StateFlow

interface BookmarkRepository {
  val changes: StateFlow<Long>
  suspend fun listSavedArticles(tagId: String?, folderId: String?): List<BookmarkedArticle>
  suspend fun listReadLaterArticles(): List<BookmarkedArticle>
  suspend fun isBookmarked(articleId: String): Boolean
  suspend fun listFolders(): List<BookmarkFolder>
  suspend fun listTags(): List<Tag>
  suspend fun createFolder(name: String)
  suspend fun renameFolder(folderId: String, name: String)
  suspend fun deleteFolder(folderId: String)
  suspend fun moveArticleToFolder(articleId: String, folderId: String?)
  suspend fun createTag(name: String)
  suspend fun renameTag(tagId: String, name: String)
  suspend fun deleteTag(tagId: String)
  suspend fun replaceArticleTags(articleId: String, tagIds: Set<String>)
  suspend fun saveAndReadArticle(articleId: String)
  suspend fun markReadLater(articleId: String)
  suspend fun unsaveArticle(articleId: String)
  suspend fun removeReadLater(articleId: String)
  suspend fun saveSharedArticle(url: String, title: String, sourceTitle: String): BookmarkSaveResult
}

interface BookmarkImportRepository {
  suspend fun importBookmarkCsv(documentUri: String): BookmarkImportResult
  suspend fun importBookmarkHtml(documentUri: String): BookmarkImportResult
}
