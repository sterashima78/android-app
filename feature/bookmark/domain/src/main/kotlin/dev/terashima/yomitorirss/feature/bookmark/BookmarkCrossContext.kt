package dev.terashima.yomitorirss.feature.bookmark

data class BookmarkArticleSave(
  val articleId: String,
  val result: BookmarkSaveResult,
)

data class BookmarkImportedArticle(
  val articleId: String,
  val added: Boolean,
  val duplicate: Boolean,
)

/**
 * Transitional port for bookmark use cases that still store bookmark state in the Content-owned articles table.
 *
 * The implementation belongs to Article data. This port must disappear when saved_at moves to Curation-owned storage.
 */
interface BookmarkArticleGateway {
  suspend fun isBookmarked(articleId: String): Boolean
  suspend fun saveAndRead(articleId: String)
  suspend fun unsave(articleId: String)
  suspend fun saveSharedArticle(
    url: String,
    title: String,
    sourceTitle: String,
  ): BookmarkArticleSave
  suspend fun importSavedArticle(
    url: String,
    title: String,
    sourceTitle: String,
    createdAt: String,
    identityPrefix: String,
  ): BookmarkImportedArticle
}

data class BookmarkEnrichmentContext(
  val existingTagNames: List<String>,
  val existingFolderNames: List<String>,
)

interface BookmarkEnrichmentRepository {
  suspend fun context(articleId: String): BookmarkEnrichmentContext?
  suspend fun applyGeneratedMetadata(
    articleId: String,
    tagNames: List<String>,
    folderName: String?,
  ): Boolean
}

interface BookmarkEnrichmentRepositoryProvider {
  val bookmarkEnrichmentRepository: BookmarkEnrichmentRepository
}
