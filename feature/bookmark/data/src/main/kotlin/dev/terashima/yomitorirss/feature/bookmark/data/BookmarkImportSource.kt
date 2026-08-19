package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.Context
import android.net.Uri
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkArticleGateway
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportBatch
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportEntry
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportFormat
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportRepository
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportResult
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportSource
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportTagWriter
import dev.terashima.yomitorirss.feature.bookmark.ImportBookmarksUseCase
import java.io.Reader

class DefaultBookmarkImportRepository(
  context: Context,
  database: DatabaseConnection,
  articleGateway: BookmarkArticleGateway,
  dataChanges: DataChangeNotifier,
) : BookmarkImportRepository {
  private val importBookmarks = ImportBookmarksUseCase(
    source = AndroidBookmarkImportSource(context),
    articleGateway = articleGateway,
    tagWriter = DefaultBookmarkImportTagWriter(database),
    onChanged = dataChanges::notifyChanged,
  )

  override suspend fun importBookmarkCsv(documentUri: String): BookmarkImportResult =
    importBookmarks(documentUri, BookmarkImportFormat.CSV)

  override suspend fun importBookmarkHtml(documentUri: String): BookmarkImportResult =
    importBookmarks(documentUri, BookmarkImportFormat.HTML)
}

internal class AndroidBookmarkImportSource(
  context: Context,
) : BookmarkImportSource {
  private val appContext = context.applicationContext

  override suspend fun read(
    documentUri: String,
    format: BookmarkImportFormat,
  ): BookmarkImportBatch = when (format) {
    BookmarkImportFormat.CSV -> openReader(
      documentUri = documentUri,
      errorMessage = "CSVファイルを開けませんでした",
    ) { reader ->
      val parsed = parseBookmarkCsv(reader)
      BookmarkImportBatch(
        entries = parsed.entries.map { entry ->
          BookmarkImportEntry(
            title = entry.title,
            url = entry.url,
            createdAt = entry.createdAt,
            sourceTitle = entry.sourceTitle,
            tagNames = entry.tagNames,
          )
        },
        skipped = parsed.skippedRows,
      )
    }

    BookmarkImportFormat.HTML -> openReader(
      documentUri = documentUri,
      errorMessage = "HTMLファイルを開けませんでした",
    ) { reader ->
      val parsed = parseBookmarkHtml(reader)
      BookmarkImportBatch(
        entries = parsed.entries.map { entry ->
          BookmarkImportEntry(
            title = entry.title,
            url = entry.url,
            createdAt = entry.createdAt,
            sourceTitle = entry.sourceTitle,
            tagNames = entry.tagNames,
          )
        },
        skipped = parsed.skippedEntries,
      )
    }
  }

  private fun <T> openReader(
    documentUri: String,
    errorMessage: String,
    block: (Reader) -> T,
  ): T = appContext.contentResolver.openInputStream(Uri.parse(documentUri))
    ?.bufferedReader(Charsets.UTF_8)
    ?.use(block)
    ?: error(errorMessage)
}

internal class DefaultBookmarkImportTagWriter(
  database: DatabaseConnection,
) : BookmarkImportTagWriter {
  private val associations = BookmarkAssociationStore(database)

  override suspend fun addTags(articleId: String, tagNames: List<String>) {
    associations.addImportedTags(articleId, tagNames)
  }
}
