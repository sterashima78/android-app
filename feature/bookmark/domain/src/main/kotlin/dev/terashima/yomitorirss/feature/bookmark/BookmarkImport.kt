package dev.terashima.yomitorirss.feature.bookmark

enum class BookmarkImportFormat(
  val identityPrefix: String,
) {
  CSV("csv"),
  HTML("html"),
}

data class BookmarkImportEntry(
  val title: String,
  val url: String,
  val createdAt: String,
  val sourceTitle: String,
  val tagNames: List<String>,
)

data class BookmarkImportBatch(
  val entries: List<BookmarkImportEntry>,
  val skipped: Int,
)

/** Reads an external bookmark document and converts it into Curation-neutral import entries. */
interface BookmarkImportSource {
  suspend fun read(documentUri: String, format: BookmarkImportFormat): BookmarkImportBatch
}

/** Writes only the Curation-owned tag associations required by bookmark import. */
interface BookmarkImportTagWriter {
  suspend fun addTags(articleId: String, tagNames: List<String>)
}

/**
 * Application service for bookmark import.
 *
 * Parsing belongs to [BookmarkImportSource], Content creation/save is delegated to
 * [BookmarkArticleGateway], and Curation tag persistence is delegated to
 * [BookmarkImportTagWriter]. The use case owns only the cross-boundary workflow.
 */
class ImportBookmarksUseCase(
  private val source: BookmarkImportSource,
  private val articleGateway: BookmarkArticleGateway,
  private val tagWriter: BookmarkImportTagWriter,
  private val onChanged: () -> Unit = {},
) {
  suspend operator fun invoke(
    documentUri: String,
    format: BookmarkImportFormat,
  ): BookmarkImportResult {
    val batch = source.read(documentUri, format)
    var added = 0
    var duplicates = 0

    batch.entries.forEach { entry ->
      val imported = articleGateway.importSavedArticle(
        url = entry.url,
        title = entry.title,
        sourceTitle = entry.sourceTitle,
        createdAt = entry.createdAt,
        identityPrefix = format.identityPrefix,
      )
      if (imported.added) added += 1
      if (imported.duplicate) duplicates += 1
      tagWriter.addTags(imported.articleId, entry.tagNames)
    }

    onChanged()
    return BookmarkImportResult(
      added = added,
      duplicates = duplicates,
      skipped = batch.skipped,
    )
  }
}
