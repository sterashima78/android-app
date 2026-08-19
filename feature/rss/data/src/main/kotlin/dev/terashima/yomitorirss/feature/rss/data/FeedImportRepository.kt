package dev.terashima.yomitorirss.feature.rss.data

import android.content.Context
import android.net.Uri
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ContentSourceGateway
import dev.terashima.yomitorirss.feature.rss.FeedImportRepository
import dev.terashima.yomitorirss.feature.rss.FeedOpmlImportResult
import dev.terashima.yomitorirss.feature.rss.data.network.ParsedFeed

class DefaultFeedImportRepository(
  context: Context,
  database: DatabaseConnection,
  contentSourceGateway: ContentSourceGateway,
  private val dataChanges: DataChangeNotifier,
) : FeedImportRepository {
  private val appContext = context.applicationContext
  private val store = FeedStore(database, contentSourceGateway)

  override suspend fun importFeedOpml(documentUri: String): FeedOpmlImportResult {
    val parsed = openReader(documentUri, "OPMLファイルを開けませんでした", ::parseFeedOpml)
    val knownUrls = store.listFeeds()
      .mapNotNull { normalizedFeedUrlKey(it.feedUrl) }
      .toMutableSet()
    var added = 0
    var duplicates = parsed.duplicates
    var skipped = parsed.skipped

    parsed.feeds.forEach { entry ->
      val key = normalizedFeedUrlKey(entry.feedUrl)
      if (key == null) {
        skipped += 1
      } else if (!knownUrls.add(key)) {
        duplicates += 1
      } else {
        runCatching {
          val folderId = entry.folders
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" / ")
            ?.let(store::ensureFolder)
            ?.id
          store.addFeed(
            parsed = ParsedFeed(
              title = entry.title,
              feedUrl = entry.feedUrl,
              siteUrl = entry.siteUrl,
              articles = emptyList(),
            ),
            etag = null,
            modified = null,
            folderId = folderId,
          )
        }.onSuccess {
          added += 1
        }.onFailure {
          knownUrls.remove(key)
          skipped += 1
        }
      }
    }

    if (added > 0) dataChanges.notifyChanged()
    return FeedOpmlImportResult(added, duplicates, skipped)
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
