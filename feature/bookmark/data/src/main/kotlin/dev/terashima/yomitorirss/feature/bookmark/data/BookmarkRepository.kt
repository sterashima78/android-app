package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkArticleGateway
import dev.terashima.yomitorirss.feature.bookmark.BookmarkFolder
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportResult
import dev.terashima.yomitorirss.feature.bookmark.BookmarkRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.bookmark.Tag
import dev.terashima.yomitorirss.feature.bookmark.YOUTUBE_FOLDER_KIND
import dev.terashima.yomitorirss.feature.bookmark.YOUTUBE_FOLDER_NAME
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

class DefaultBookmarkRepository(
  private val database: DatabaseConnection,
  private val articleGateway: BookmarkArticleGateway,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier(),
  private val onBookmarkAdded: suspend (articleId: String) -> Unit = {},
) : BookmarkRepository {
  private val store = BookmarkStore(database)
  override val changes: StateFlow<Long> = dataChanges.version

  override suspend fun listSavedArticles(tagId: String?, folderId: String?): List<BookmarkedArticle> =
    store.listSavedArticles(tagId, folderId)

  override suspend fun listAllSavedArticles(): List<BookmarkedArticle> = store.listAllSavedArticles()

  override suspend fun listReadLaterArticles(): List<BookmarkedArticle> = store.listReadLaterArticles()

  override suspend fun isBookmarked(articleId: String): Boolean = articleGateway.isBookmarked(articleId)

  override suspend fun listFolders(): List<BookmarkFolder> {
    ensureYouTubeFolder()
    return store.listFolders()
  }

  override suspend fun listTags(): List<Tag> = store.listTags()

  override suspend fun createFolder(name: String) {
    requireUserFolderName(name)
    store.createFolder(name)
    dataChanges.notifyChanged()
  }

  override suspend fun renameFolder(folderId: String, name: String) {
    requireUserFolderName(name)
    store.renameFolder(folderId, name)
    dataChanges.notifyChanged()
  }

  override suspend fun deleteFolder(folderId: String) {
    store.deleteFolder(folderId)
    dataChanges.notifyChanged()
  }

  override suspend fun moveArticleToFolder(articleId: String, folderId: String?) {
    require(articleGateway.isBookmarked(articleId)) { "ブックマークされていない記事です" }
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
    val wasBookmarked = articleGateway.isBookmarked(articleId)
    articleGateway.saveAndRead(articleId)
    dataChanges.notifyChanged()
    notifyNewBookmark(articleId, wasBookmarked)
  }

  override suspend fun markReadLater(articleId: String) {
    val wasBookmarked = articleGateway.isBookmarked(articleId)
    articleGateway.saveAndRead(articleId)
    store.addReadLater(articleId)
    dataChanges.notifyChanged()
    notifyNewBookmark(articleId, wasBookmarked)
  }

  override suspend fun unsaveArticle(articleId: String) {
    articleGateway.unsave(articleId)
    store.clearArticleAssociations(articleId)
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
    if (folderId != null) store.moveArticleToFolder(saved.articleId, folderId)
    dataChanges.notifyChanged()
    if (saved.result == BookmarkSaveResult.ADDED) {
      notifyNewBookmark(saved.articleId, wasBookmarked = false)
    }
    return saved.result
  }

  private fun ensureYouTubeFolder() {
    val normalizedName = normalizeFolderName(YOUTUBE_FOLDER_NAME)
    database.transaction {
      rawQuery(
        "SELECT id,name,system_kind FROM bookmark_folders WHERE normalized_name=? LIMIT 1",
        arrayOf(normalizedName),
      ).use { cursor ->
        if (cursor.moveToFirst()) {
          val id = cursor.getString(0)
          val name = cursor.getString(1)
          val systemKind = if (cursor.isNull(2)) null else cursor.getString(2)
          require(systemKind == null || systemKind == YOUTUBE_FOLDER_KIND) {
            "YouTubeフォルダ名は別のシステムフォルダで使用されています"
          }
          if (name != YOUTUBE_FOLDER_NAME || systemKind != YOUTUBE_FOLDER_KIND) {
            update(
              "bookmark_folders",
              ContentValues().apply {
                put("name", YOUTUBE_FOLDER_NAME)
                put("normalized_name", normalizedName)
                put("system_kind", YOUTUBE_FOLDER_KIND)
              },
              "id=?",
              arrayOf(id),
            )
          }
          return@transaction
        }
      }

      rawQuery(
        "SELECT id FROM bookmark_folders WHERE system_kind=? LIMIT 1",
        arrayOf(YOUTUBE_FOLDER_KIND),
      ).use { cursor ->
        if (cursor.moveToFirst()) {
          update(
            "bookmark_folders",
            ContentValues().apply {
              put("name", YOUTUBE_FOLDER_NAME)
              put("normalized_name", normalizedName)
            },
            "id=?",
            arrayOf(cursor.getString(0)),
          )
          return@transaction
        }
      }

      insertOrThrow(
        "bookmark_folders",
        null,
        ContentValues().apply {
          put("id", UUID.randomUUID().toString())
          put("name", YOUTUBE_FOLDER_NAME)
          put("normalized_name", normalizedName)
          put("system_kind", YOUTUBE_FOLDER_KIND)
          put("created_at", Instant.now().toString())
        },
      )
    }
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

class DefaultBookmarkImportRepository(
  context: Context,
  database: DatabaseConnection,
  private val articleGateway: BookmarkArticleGateway,
  private val dataChanges: DataChangeNotifier,
) : BookmarkImportRepository {
  private val appContext = context.applicationContext
  private val store = BookmarkStore(database)

  override suspend fun importBookmarkCsv(documentUri: String): BookmarkImportResult {
    val parsed = openReader(documentUri, "CSVファイルを開けませんでした", ::parseBookmarkCsv)
    return importEntries(
      entries = parsed.entries.map { entry ->
        ImportedBookmarkEntry(entry.title, entry.url, entry.createdAt, entry.sourceTitle, entry.tagNames)
      },
      skipped = parsed.skippedRows,
      identityPrefix = "csv",
    ).also { dataChanges.notifyChanged() }
  }

  override suspend fun importBookmarkHtml(documentUri: String): BookmarkImportResult {
    val parsed = openReader(documentUri, "HTMLファイルを開けませんでした", ::parseBookmarkHtml)
    return importEntries(
      entries = parsed.entries.map { entry ->
        ImportedBookmarkEntry(entry.title, entry.url, entry.createdAt, entry.sourceTitle, entry.tagNames)
      },
      skipped = parsed.skippedEntries,
      identityPrefix = "html",
    ).also { dataChanges.notifyChanged() }
  }

  private suspend fun importEntries(
    entries: List<ImportedBookmarkEntry>,
    skipped: Int,
    identityPrefix: String,
  ): BookmarkImportResult {
    var added = 0
    var duplicates = 0
    entries.forEach { entry ->
      val imported = articleGateway.importSavedArticle(
        url = entry.url,
        title = entry.title,
        sourceTitle = entry.sourceTitle,
        createdAt = entry.createdAt,
        identityPrefix = identityPrefix,
      )
      if (imported.added) added += 1
      if (imported.duplicate) duplicates += 1
      store.addImportedTags(imported.articleId, entry.tagNames)
    }
    return BookmarkImportResult(added = added, duplicates = duplicates, skipped = skipped)
  }

  private fun <T> openReader(
    documentUri: String,
    errorMessage: String,
    block: (java.io.Reader) -> T,
  ): T = appContext.contentResolver.openInputStream(Uri.parse(documentUri))
    ?.bufferedReader(Charsets.UTF_8)
    ?.use(block)
    ?: error(errorMessage)
}

private fun normalizeFolderName(name: String): String =
  name.trim().replace(Regex("\\s+"), " ").lowercase()
