package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.LibrarySyncResult
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchSnapshot
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import java.io.File
import java.util.Locale

class CleaningSmbLibraryRepository private constructor(
  context: Context,
  private val database: DatabaseConnection,
  private val delegate: SmbLibraryRepository,
) : SmbLibraryRepository by delegate {
  private val appContext = context.applicationContext
  private val coverPrefetchQueue = SmbCoverPrefetchQueueStore(database)
  private val coverPrefetchRuntimeInspector = SmbCoverPrefetchRuntimeInspector(appContext)
  private val coverCacheCoordinator = SmbCoverCacheCoordinator(appContext, database)

  constructor(
    context: Context,
    database: DatabaseConnection,
  ) : this(
    context = context,
    database = database,
    delegate = DefaultSmbLibraryRepository(context, database),
  )

  override suspend fun sync(): LibrarySyncResult {
    val result = delegate.sync()
    val redundantSourceIds = redundantSmbSourceIds(deduplicationCandidates())
    val cleanedResult = if (redundantSourceIds.isEmpty()) {
      result
    } else {
      database.transaction {
        redundantSourceIds.forEach { sourceId -> deleteSmbBookMetadata(sourceId) }
      }
      redundantSourceIds.forEach { sourceId ->
        File(appContext.cacheDir, "smb-books/$sourceId").deleteRecursively()
      }
      deleteSmbBookCovers(appContext, redundantSourceIds)

      result.copy(
        importedCount = (result.importedCount - redundantSourceIds.size).coerceAtLeast(0),
      )
    }
    coverCacheCoordinator.trim()
    coverPrefetchQueue.enqueueMissing()
    return cleanedResult
  }

  override suspend fun renameBook(
    book: LibraryBook,
    newFileName: String,
  ): LibraryBook {
    val renamed = delegate.renameBook(book, newFileName)
    coverCacheCoordinator.trim()
    coverPrefetchQueue.enqueueMissing()
    return renamed
  }

  override suspend fun deleteBook(book: LibraryBook) {
    delegate.deleteBook(book)
    coverPrefetchQueue.enqueueMissing()
  }

  override suspend fun coverPrefetchSnapshot(): SmbCoverPrefetchSnapshot {
    val queueSnapshot = coverPrefetchQueue.snapshot()
    return queueSnapshot.copy(
      runtime = coverPrefetchRuntimeInspector.snapshot(queueSnapshot.hasActiveWork),
    )
  }

  override suspend fun enqueueMissingCoverPrefetch(): Int =
    coverPrefetchQueue.enqueueMissing(retrySkipped = true)

  override suspend fun retryFailedCoverPrefetch(): Int =
    coverPrefetchQueue.retryFailed()

  override suspend fun deleteServer(serverId: String) {
    val sourceIds = sourceIdsForServer(serverId)
    delegate.deleteServer(serverId)

    val remainingServers = delegate.servers()
    database.transaction {
      sourceIds.forEach { sourceId -> deleteSmbBookMetadata(sourceId) }

      if (remainingServers.isEmpty()) {
        delete("library_sources", "source = ?", arrayOf(LibrarySource.SMB.name))
      } else {
        update(
          "library_sources",
          ContentValues().apply {
            put("account_label", "${remainingServers.size}台のSMBサーバ")
          },
          "source = ?",
          arrayOf(LibrarySource.SMB.name),
        )
      }
    }

    sourceIds.forEach { sourceId ->
      File(appContext.cacheDir, "smb-books/$sourceId").deleteRecursively()
    }
    deleteSmbBookCovers(appContext, sourceIds)
    coverPrefetchQueue.enqueueMissing()
  }

  private fun deduplicationCandidates(): List<SmbLibraryDeduplicationCandidate> = database.readable.rawQuery(
    "SELECT source_id, title, info_url FROM library_items WHERE source = ?",
    arrayOf(LibrarySource.SMB.name),
  ).use { cursor ->
    val sourceIdIndex = cursor.getColumnIndexOrThrow("source_id")
    val titleIndex = cursor.getColumnIndexOrThrow("title")
    val infoUrlIndex = cursor.getColumnIndexOrThrow("info_url")
    buildList {
      while (cursor.moveToNext()) {
        if (cursor.isNull(infoUrlIndex)) continue
        val uri = runCatching { Uri.parse(cursor.getString(infoUrlIndex)) }.getOrNull() ?: continue
        if (uri.scheme != "yomitori" || uri.host != "smb-book" || uri.path != "/open") continue
        val path = uri.getQueryParameter("path")?.takeIf(String::isNotBlank) ?: continue
        val size = uri.getQueryParameter("size")?.toLongOrNull()?.takeIf { it >= 0L } ?: continue
        val modifiedAt = uri.getQueryParameter("modified")?.toLongOrNull() ?: continue
        val format = uri.getQueryParameter("format")?.takeIf(String::isNotBlank) ?: continue
        add(
          SmbLibraryDeduplicationCandidate(
            sourceId = cursor.getString(sourceIdIndex),
            title = cursor.getString(titleIndex),
            path = path,
            size = size,
            modifiedAt = modifiedAt,
            format = format,
          ),
        )
      }
    }
  }

  private fun SQLiteDatabase.deleteSmbBookMetadata(sourceId: String) {
    listOf(
      "library_items",
      "hidden_library_items",
      "library_item_series",
      "library_item_series_exclusions",
    ).forEach { table ->
      delete(
        table,
        "source = ? AND source_id = ?",
        arrayOf(LibrarySource.SMB.name, sourceId),
      )
    }
  }

  private fun sourceIdsForServer(serverId: String): List<String> = database.readable.rawQuery(
    "SELECT source_id, info_url FROM library_items WHERE source = ?",
    arrayOf(LibrarySource.SMB.name),
  ).use { cursor ->
    val sourceIdIndex = cursor.getColumnIndexOrThrow("source_id")
    val infoUrlIndex = cursor.getColumnIndexOrThrow("info_url")
    buildList {
      while (cursor.moveToNext()) {
        if (cursor.isNull(infoUrlIndex)) continue
        val uri = runCatching { Uri.parse(cursor.getString(infoUrlIndex)) }.getOrNull() ?: continue
        if (uri.scheme != "yomitori" || uri.host != "smb-book" || uri.path != "/open") continue
        if (uri.getQueryParameter("serverId") == serverId) {
          add(cursor.getString(sourceIdIndex))
        }
      }
    }
  }
}

internal data class SmbLibraryDeduplicationCandidate(
  val sourceId: String,
  val title: String,
  val path: String,
  val size: Long,
  val modifiedAt: Long,
  val format: String,
)

internal fun redundantSmbSourceIds(
  candidates: List<SmbLibraryDeduplicationCandidate>,
): List<String> {
  val seen = mutableSetOf<SmbLibraryDuplicateKey>()
  return candidates
    .sortedWith(
      compareBy<SmbLibraryDeduplicationCandidate>(
        { smbPathDepth(it.path) },
        { it.path.length },
        { it.path.lowercase(Locale.ROOT) },
        { it.sourceId },
      ),
    )
    .mapNotNull { candidate ->
      if (seen.add(candidate.duplicateKey())) null else candidate.sourceId
    }
}

private data class SmbLibraryDuplicateKey(
  val title: String,
  val size: Long,
  val modifiedAt: Long,
  val format: String,
)

private fun SmbLibraryDeduplicationCandidate.duplicateKey(): SmbLibraryDuplicateKey =
  SmbLibraryDuplicateKey(
    title = title.trim().lowercase(Locale.ROOT),
    size = size,
    modifiedAt = modifiedAt,
    format = format.trim().uppercase(Locale.ROOT),
  )

private fun smbPathDepth(path: String): Int = path.count { it == '\\' || it == '/' }
