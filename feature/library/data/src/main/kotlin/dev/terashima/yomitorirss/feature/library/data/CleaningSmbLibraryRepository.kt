package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import java.io.File

class CleaningSmbLibraryRepository private constructor(
  context: Context,
  private val database: DatabaseConnection,
  private val delegate: SmbLibraryRepository,
) : SmbLibraryRepository by delegate {
  private val appContext = context.applicationContext

  constructor(
    context: Context,
    database: DatabaseConnection,
  ) : this(
    context = context,
    database = database,
    delegate = DefaultSmbLibraryRepository(context, database),
  )

  override suspend fun deleteServer(serverId: String) {
    val sourceIds = sourceIdsForServer(serverId)
    delegate.deleteServer(serverId)

    val remainingServers = delegate.servers()
    database.transaction {
      sourceIds.forEach { sourceId ->
        delete(
          "library_items",
          "source = ? AND source_id = ?",
          arrayOf(LibrarySource.SMB.name, sourceId),
        )
        delete(
          "hidden_library_items",
          "source = ? AND source_id = ?",
          arrayOf(LibrarySource.SMB.name, sourceId),
        )
        delete(
          "library_item_series",
          "source = ? AND source_id = ?",
          arrayOf(LibrarySource.SMB.name, sourceId),
        )
        delete(
          "library_item_series_exclusions",
          "source = ? AND source_id = ?",
          arrayOf(LibrarySource.SMB.name, sourceId),
        )
      }

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