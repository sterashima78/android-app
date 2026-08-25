package dev.terashima.yomitorirss.feature.bookmark

/** Content の検索・作成・閲覧状態更新だけを要求する Curation -> Content command port。 */
interface BookmarkArticleGateway {
  suspend fun markRead(articleId: String)

  suspend fun findOrCreateSharedArticle(
    url: String,
    title: String,
    sourceTitle: String,
  ): String

  suspend fun findOrCreateImportedArticle(
    url: String,
    title: String,
    sourceTitle: String,
    createdAt: String,
    identityPrefix: String,
  ): String
}

/** Curation が他 Context に公開する bookmark/read-later の named query。 */
interface BookmarkContentQuery {
  fun bookmarkedContentIds(contentIds: Set<String>): Set<String>
  fun readLaterContentIds(contentIds: Set<String>): Set<String>
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
    replaceExistingTags: Boolean = false,
  ): Boolean
}
