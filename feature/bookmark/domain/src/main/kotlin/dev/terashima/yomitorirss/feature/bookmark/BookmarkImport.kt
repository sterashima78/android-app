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

/** Writes only the Curation-owned bookmark state and tag associations required by import. */
interface BookmarkImportWriter {
  suspend fun saveBookmark(articleId: String, savedAt: String): Boolean
  suspend fun addTags(articleId: String, tagNames: List<String>)
}

/** Cross-context import workflow. Content creation and Curation persistence remain owner-specific. */
class ImportBookmarksUseCase(
  private val source: BookmarkImportSource,
  private val articleGateway: BookmarkArticleGateway,
  private val writer: BookmarkImportWriter,
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
      val articleId = articleGateway.findOrCreateImportedArticle(
        url = entry.url,
        title = entry.title,
        sourceTitle = entry.sourceTitle,
        createdAt = entry.createdAt,
        identityPrefix = format.identityPrefix,
      )
      if (writer.saveBookmark(articleId, entry.createdAt)) {
        added += 1
      } else {
        duplicates += 1
      }
      writer.addTags(articleId, entry.tagNames)
    }

    onChanged()
    return BookmarkImportResult(
      added = added,
      duplicates = duplicates,
      skipped = batch.skipped,
    )
  }
}
