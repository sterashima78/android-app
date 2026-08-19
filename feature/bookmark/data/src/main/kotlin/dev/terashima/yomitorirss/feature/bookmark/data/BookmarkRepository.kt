package dev.terashima.yomitorirss.feature.bookmark.data

import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkArticleGateway
import dev.terashima.yomitorirss.feature.bookmark.BookmarkFolder
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.bookmark.Tag
import dev.terashima.yomitorirss.feature.bookmark.YOUTUBE_FOLDER_NAME
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

class DefaultBookmarkRepository(
  database: DatabaseConnection,
  private val articleRepository: ArticleRepository,
  private val articleGateway: BookmarkArticleGateway,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier(),
  private val onBookmarkAdded: suspend (articleId: String) -> Unit = {},
) : BookmarkRepository {
  private val stateStore = BookmarkStateStore(database)
  private val readStore = BookmarkReadStore(database)
  private val tagStore = BookmarkTagStore(database)
  private val folderStore = BookmarkFolderStore(database)
  private val associationStore = BookmarkAssociationStore(database)
  override val changes: StateFlow<Long> = dataChanges.version

  override suspend fun listSavedArticles(tagId: String?, folderId: String?): List<BookmarkedArticle> =
    compose(readStore.listSavedRecords(tagId, folderId), limit = 500)

  override suspend fun listAllSavedArticles(): List<BookmarkedArticle> =
    compose(readStore.listAllSavedRecords())

  override suspend fun listReadLaterArticles(): List<BookmarkedArticle> =
    compose(readStore.listReadLaterRecords())

  override suspend fun isBookmarked(articleId: String): Boolean = stateStore.isBookmarked(articleId)

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
    require(stateStore.isBookmarked(articleId)) { "ブックマークされていない記事です" }
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

  override suspend fun deleteUnusedTags(): Int = tagStore.deleteUnusedTags().also { deletedCount ->
    if (deletedCount > 0) dataChanges.notifyChanged()
  }

  override suspend fun replaceArticleTags(articleId: String, tagIds: Set<String>) {
    associationStore.replaceArticleTags(articleId, tagIds)
    dataChanges.notifyChanged()
  }

  override suspend fun saveAndReadArticle(articleId: String) {
    val wasBookmarked = stateStore.isBookmarked(articleId)
    articleGateway.markRead(articleId)
    stateStore.save(articleId)
    dataChanges.notifyChanged()
    notifyNewBookmark(articleId, wasBookmarked)
  }

  override suspend fun markReadLater(articleId: String) {
    val wasBookmarked = stateStore.isBookmarked(articleId)
    articleGateway.markRead(articleId)
    stateStore.save(articleId)
    associationStore.addReadLater(articleId)
    dataChanges.notifyChanged()
    notifyNewBookmark(articleId, wasBookmarked)
  }

  override suspend fun unsaveArticle(articleId: String) {
    stateStore.unsave(articleId)
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
  ): BookmarkSaveResult = saveSharedArticleInternal(url, title, sourceTitle, folderId = null)

  override suspend fun saveSharedArticleToFolder(
    url: String,
    title: String,
    sourceTitle: String,
    folderId: String,
  ): BookmarkSaveResult = saveSharedArticleInternal(url, title, sourceTitle, folderId)

  private suspend fun saveSharedArticleInternal(
    url: String,
    title: String,
    sourceTitle: String,
    folderId: String?,
  ): BookmarkSaveResult {
    val articleId = articleGateway.findOrCreateSharedArticle(url, title, sourceTitle)
    val added = stateStore.save(articleId)
    if (folderId != null) associationStore.moveArticleToFolder(articleId, folderId)
    dataChanges.notifyChanged()
    if (added) notifyNewBookmark(articleId, wasBookmarked = false)
    return if (added) BookmarkSaveResult.ADDED else BookmarkSaveResult.ALREADY_BOOKMARKED
  }

  private suspend fun compose(records: List<BookmarkRecord>, limit: Int? = null): List<BookmarkedArticle> {
    if (records.isEmpty()) return emptyList()
    val articles = articleRepository.findArticles(records.map(BookmarkRecord::articleId)).associateBy { it.id }
    val composed = records.mapNotNull { record ->
      articles[record.articleId]?.let { article ->
        BookmarkedArticle(
          article = article,
          savedAt = record.savedAt,
          tags = record.tags,
          folder = record.folder,
        )
      }
    }.sortedByDescending { it.article.publishedAt }
    return limit?.let(composed::take) ?: composed
  }

  private fun requireUserFolderName(name: String) {
    require(normalizeFolderName(name) != normalizeFolderName(YOUTUBE_FOLDER_NAME)) {
      "YouTubeはシステムフォルダ名として予約されています"
    }
  }

  private suspend fun notifyNewBookmark(articleId: String, wasBookmarked: Boolean) {
    if (wasBookmarked || !stateStore.isBookmarked(articleId)) return
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
