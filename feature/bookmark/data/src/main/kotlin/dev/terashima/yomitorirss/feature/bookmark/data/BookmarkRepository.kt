package dev.terashima.yomitorirss.feature.bookmark.data

import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkArticleGateway
import dev.terashima.yomitorirss.feature.bookmark.BookmarkFolder
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.bookmark.Tag
import dev.terashima.yomitorirss.feature.bookmark.YOUTUBE_FOLDER_NAME
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

class DefaultBookmarkRepository(
  database: DatabaseConnection,
  private val articleGateway: BookmarkArticleGateway,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier(),
  private val onBookmarkAdded: suspend (articleId: String) -> Unit = {},
) : BookmarkRepository {
  private val readStore = BookmarkReadStore(database)
  private val tagStore = BookmarkTagStore(database)
  private val folderStore = BookmarkFolderStore(database)
  private val associationStore = BookmarkAssociationStore(database)
  override val changes: StateFlow<Long> = dataChanges.version

  override suspend fun listSavedArticles(tagId: String?, folderId: String?): List<BookmarkedArticle> =
    readStore.listSavedArticles(tagId, folderId)

  override suspend fun listAllSavedArticles(): List<BookmarkedArticle> = readStore.listAllSavedArticles()

  override suspend fun listReadLaterArticles(): List<BookmarkedArticle> = readStore.listReadLaterArticles()

  override suspend fun isBookmarked(articleId: String): Boolean = articleGateway.isBookmarked(articleId)

  override suspend fun listFolders(): List<BookmarkFolder> {
    folderStore.ensureYouTubeFolder()
    return folderStore.listFolders()
  }

  override suspend fun listTags(): List<Tag> = tagStore.listTags()

  override suspend fun createFolder(name: String) {
    requireUserFolderName(name)
    folderStore.createFolder(name)
    dataChanges.notifyChanged()
  }

  override suspend fun renameFolder(folderId: String, name: String) {
    requireUserFolderName(name)
    folderStore.renameFolder(folderId, name)
    dataChanges.notifyChanged()
  }

  override suspend fun deleteFolder(folderId: String) {
    folderStore.deleteFolder(folderId)
    dataChanges.notifyChanged()
  }

  override suspend fun moveArticleToFolder(articleId: String, folderId: String?) {
    require(articleGateway.isBookmarked(articleId)) { "ブックマークされていない記事です" }
    associationStore.moveArticleToFolder(articleId, folderId)
    dataChanges.notifyChanged()
  }

  override suspend fun createTag(name: String) {
    tagStore.createTag(name)
    dataChanges.notifyChanged()
  }

  override suspend fun renameTag(tagId: String, name: String) {
    tagStore.renameTag(tagId, name)
    dataChanges.notifyChanged()
  }

  override suspend fun deleteTag(tagId: String) {
    tagStore.deleteTag(tagId)
    dataChanges.notifyChanged()
  }

  override suspend fun replaceArticleTags(articleId: String, tagIds: Set<String>) {
    associationStore.replaceArticleTags(articleId, tagIds)
    dataChanges.notifyChanged()
  }

  override suspend fun saveAndReadArticle(articleId: String) {
    val wasBookmarked = articleGateway.isBookmarked(articleId)
    articleGateway.saveAndRead(articleId)
    dataChanges.notifyChanged()
    notifyNewBookmark(articleId, wasBookmarked)
  }

  override suspend fun markReadLater(articleId: String) {
    val wasBookmarked = articleGateway.isBookmarked(articleId)
    articleGateway.saveAndRead(articleId)
    associationStore.addReadLater(articleId)
    dataChanges.notifyChanged()
    notifyNewBookmark(articleId, wasBookmarked)
  }

  override suspend fun unsaveArticle(articleId: String) {
    articleGateway.unsave(articleId)
    associationStore.clearArticleAssociations(articleId)
    dataChanges.notifyChanged()
  }

  override suspend fun removeReadLater(articleId: String) {
    associationStore.removeReadLater(articleId)
    dataChanges.notifyChanged()
  }

  override suspend fun saveSharedArticle(
    url: String,
    title: String,
    sourceTitle: String,
  ): BookmarkSaveResult = saveSharedArticleInternal(
    url = url,
    title = title,
    sourceTitle = sourceTitle,
    folderId = null,
  )

  override suspend fun saveSharedArticleToFolder(
    url: String,
    title: String,
    sourceTitle: String,
    folderId: String,
  ): BookmarkSaveResult = saveSharedArticleInternal(
    url = url,
    title = title,
    sourceTitle = sourceTitle,
    folderId = folderId,
  )

  private suspend fun saveSharedArticleInternal(
    url: String,
    title: String,
    sourceTitle: String,
    folderId: String?,
  ): BookmarkSaveResult {
    val saved = articleGateway.saveSharedArticle(url, title, sourceTitle)
    if (folderId != null) associationStore.moveArticleToFolder(saved.articleId, folderId)
    dataChanges.notifyChanged()
    if (saved.result == BookmarkSaveResult.ADDED) {
      notifyNewBookmark(saved.articleId, wasBookmarked = false)
    }
    return saved.result
  }

  private fun requireUserFolderName(name: String) {
    require(normalizeFolderName(name) != normalizeFolderName(YOUTUBE_FOLDER_NAME)) {
      "YouTubeはシステムフォルダ名として予約されています"
    }
  }

  private suspend fun notifyNewBookmark(articleId: String, wasBookmarked: Boolean) {
    if (wasBookmarked || !articleGateway.isBookmarked(articleId)) return
    try {
      onBookmarkAdded(articleId)
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      // ブックマーク保存の成功はAI処理の成否から独立させる。
    }
  }
}

private fun normalizeFolderName(name: String): String =
  name.trim().replace(Regex("\\s+"), " ").lowercase()
