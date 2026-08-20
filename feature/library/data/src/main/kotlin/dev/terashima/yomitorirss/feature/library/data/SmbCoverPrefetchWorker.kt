package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchItem
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchScheduler
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchSnapshot
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class WorkManagerSmbCoverPrefetchScheduler(
  context: Context,
) : SmbCoverPrefetchScheduler {
  private val appContext = context.applicationContext

  override fun kick() {
    val request = OneTimeWorkRequestBuilder<SmbCoverPrefetchWorker>()
      .setConstraints(
        Constraints.Builder()
          .setRequiredNetworkType(NetworkType.UNMETERED)
          .build(),
      )
      .addTag(SmbCoverPrefetchWorker.WORK_TAG)
      .build()
    WorkManager.getInstance(appContext).enqueueUniqueWork(
      SmbCoverPrefetchWorker.WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request,
    )
  }
}

class SmbCoverPrefetchWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val database = YomitoriDatabase.create(applicationContext)
    val connection = DatabaseConnection(database)
    val queue = SmbCoverPrefetchQueueStore(connection)
    val repository = DefaultSmbLibraryRepository(applicationContext, connection)
    var current: SmbCoverPrefetchQueueEntry? = null

    try {
      queue.requeueInterrupted()
      DataChangeNotifier.shared.notifyChanged()

      while (true) {
        currentCoroutineContext().ensureActive()
        val item = queue.claimNext() ?: break
        current = item
        DataChangeNotifier.shared.notifyChanged()

        try {
          var lastReportedBytes = 0L
          val outcome = repository.prefetchCover(item.sourceId) { downloadedBytes, totalBytes ->
            if (
              downloadedBytes == totalBytes ||
              downloadedBytes - lastReportedBytes >= PROGRESS_PERSIST_STEP_BYTES
            ) {
              queue.updateProgress(item.sourceId, downloadedBytes, totalBytes)
              lastReportedBytes = downloadedBytes
              DataChangeNotifier.shared.notifyChanged()
            }
          }
          when (outcome) {
            is SmbCoverPrefetchOutcome.Completed -> queue.complete(item.sourceId)
            is SmbCoverPrefetchOutcome.Skipped -> queue.skip(item.sourceId, outcome.reason)
          }
        } catch (cancelled: CancellationException) {
          queue.requeue(item.sourceId)
          DataChangeNotifier.shared.notifyChanged()
          throw cancelled
        } catch (error: Throwable) {
          queue.fail(item.sourceId, error.userMessage())
        }
        DataChangeNotifier.shared.notifyChanged()
        current = null
      }
      Result.success()
    } catch (cancelled: CancellationException) {
      current?.let { queue.requeue(it.sourceId) }
      DataChangeNotifier.shared.notifyChanged()
      throw cancelled
    } catch (_: Throwable) {
      Result.retry()
    } finally {
      database.close()
    }
  }

  companion object {
    internal const val WORK_NAME = "smb-library-cover-prefetch"
    internal const val WORK_TAG = "smb-library-cover-prefetch"
    private const val PROGRESS_PERSIST_STEP_BYTES = 1024L * 1024L
  }
}

internal class SmbCoverPrefetchQueueStore(
  private val database: DatabaseConnection,
) {
  fun snapshot(): SmbCoverPrefetchSnapshot {
    ensureSchema()
    val counts = mutableMapOf<SmbCoverPrefetchStatus, Int>()
    database.readable.rawQuery(
      "SELECT status, COUNT(*) FROM $TABLE GROUP BY status",
      null,
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val status = runCatching { SmbCoverPrefetchStatus.valueOf(cursor.getString(0)) }.getOrNull()
          ?: continue
        counts[status] = cursor.getInt(1)
      }
    }

    val items = database.readable.rawQuery(
      """
        SELECT source_id, title, status, downloaded_bytes, total_bytes, message, updated_at
        FROM $TABLE
        ORDER BY
          CASE status
            WHEN 'RUNNING' THEN 0
            WHEN 'PENDING' THEN 1
            WHEN 'FAILED' THEN 2
            WHEN 'SKIPPED' THEN 3
            ELSE 4
          END,
          updated_at DESC,
          title COLLATE NOCASE
        LIMIT $VISIBLE_ITEM_LIMIT
      """.trimIndent(),
      null,
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          val status = runCatching { SmbCoverPrefetchStatus.valueOf(cursor.getString(2)) }.getOrNull()
            ?: continue
          add(
            SmbCoverPrefetchItem(
              sourceId = cursor.getString(0),
              title = cursor.getString(1),
              status = status,
              downloadedBytes = cursor.getLong(3),
              totalBytes = cursor.getLong(4),
              message = if (cursor.isNull(5)) null else cursor.getString(5),
              updatedAtEpochMillis = cursor.getLong(6),
            ),
          )
        }
      }
    }

    return SmbCoverPrefetchSnapshot(
      items = items,
      pendingCount = counts[SmbCoverPrefetchStatus.PENDING] ?: 0,
      runningCount = counts[SmbCoverPrefetchStatus.RUNNING] ?: 0,
      failedCount = counts[SmbCoverPrefetchStatus.FAILED] ?: 0,
      completedCount = counts[SmbCoverPrefetchStatus.COMPLETED] ?: 0,
      skippedCount = counts[SmbCoverPrefetchStatus.SKIPPED] ?: 0,
    )
  }

  fun enqueueMissing(): Int {
    ensureSchema()
    pruneMissingBooks()
    val candidates = database.readable.rawQuery(
      """
        SELECT source_id, title
        FROM library_items
        WHERE source = ? AND (thumbnail_url IS NULL OR thumbnail_url = '')
        ORDER BY title COLLATE NOCASE, source_id
      """.trimIndent(),
      arrayOf(LibrarySource.SMB.name),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(cursor.getString(0) to cursor.getString(1))
        }
      }
    }

    var enqueued = 0
    val now = System.currentTimeMillis()
    database.transaction {
      candidates.forEach { (sourceId, title) ->
        val existingStatus = rawQuery(
          "SELECT status FROM $TABLE WHERE source_id = ? LIMIT 1",
          arrayOf(sourceId),
        ).use { cursor ->
          if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        if (existingStatus == SmbCoverPrefetchStatus.PENDING.name ||
          existingStatus == SmbCoverPrefetchStatus.RUNNING.name ||
          existingStatus == SmbCoverPrefetchStatus.FAILED.name
        ) {
          return@forEach
        }

        insertWithOnConflict(
          TABLE,
          null,
          ContentValues().apply {
            put("source_id", sourceId)
            put("title", title)
            put("status", SmbCoverPrefetchStatus.PENDING.name)
            put("downloaded_bytes", 0L)
            put("total_bytes", 0L)
            putNull("message")
            put("updated_at", now)
          },
          android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
        )
        enqueued += 1
      }
    }
    trimHistory()
    return enqueued
  }

  fun retryFailed(): Int {
    ensureSchema()
    val now = System.currentTimeMillis()
    val updated = database.writable.update(
      TABLE,
      ContentValues().apply {
        put("status", SmbCoverPrefetchStatus.PENDING.name)
        put("downloaded_bytes", 0L)
        put("total_bytes", 0L)
        putNull("message")
        put("updated_at", now)
      },
      "status = ?",
      arrayOf(SmbCoverPrefetchStatus.FAILED.name),
    )
    return updated
  }

  fun requeueInterrupted() {
    ensureSchema()
    database.writable.update(
      TABLE,
      ContentValues().apply {
        put("status", SmbCoverPrefetchStatus.PENDING.name)
        put("updated_at", System.currentTimeMillis())
      },
      "status = ?",
      arrayOf(SmbCoverPrefetchStatus.RUNNING.name),
    )
  }

  fun claimNext(): SmbCoverPrefetchQueueEntry? {
    ensureSchema()
    var claimed: SmbCoverPrefetchQueueEntry? = null
    database.transaction {
      val next = rawQuery(
        """
          SELECT source_id, title
          FROM $TABLE
          WHERE status = ?
          ORDER BY updated_at, title COLLATE NOCASE, source_id
          LIMIT 1
        """.trimIndent(),
        arrayOf(SmbCoverPrefetchStatus.PENDING.name),
      ).use { cursor ->
        if (!cursor.moveToFirst()) null else SmbCoverPrefetchQueueEntry(cursor.getString(0), cursor.getString(1))
      } ?: return@transaction

      val updated = update(
        TABLE,
        ContentValues().apply {
          put("status", SmbCoverPrefetchStatus.RUNNING.name)
          put("downloaded_bytes", 0L)
          put("total_bytes", 0L)
          putNull("message")
          put("updated_at", System.currentTimeMillis())
        },
        "source_id = ? AND status = ?",
        arrayOf(next.sourceId, SmbCoverPrefetchStatus.PENDING.name),
      )
      if (updated == 1) claimed = next
    }
    return claimed
  }

  fun updateProgress(sourceId: String, downloadedBytes: Long, totalBytes: Long) {
    ensureSchema()
    database.writable.update(
      TABLE,
      ContentValues().apply {
        put("downloaded_bytes", downloadedBytes.coerceAtLeast(0L))
        put("total_bytes", totalBytes.coerceAtLeast(0L))
        put("updated_at", System.currentTimeMillis())
      },
      "source_id = ? AND status = ?",
      arrayOf(sourceId, SmbCoverPrefetchStatus.RUNNING.name),
    )
  }

  fun complete(sourceId: String) = finish(sourceId, SmbCoverPrefetchStatus.COMPLETED, null)

  fun skip(sourceId: String, reason: String) = finish(sourceId, SmbCoverPrefetchStatus.SKIPPED, reason)

  fun fail(sourceId: String, message: String) = finish(sourceId, SmbCoverPrefetchStatus.FAILED, message)

  fun requeue(sourceId: String) = finish(sourceId, SmbCoverPrefetchStatus.PENDING, null)

  private fun finish(sourceId: String, status: SmbCoverPrefetchStatus, message: String?) {
    ensureSchema()
    database.writable.update(
      TABLE,
      ContentValues().apply {
        put("status", status.name)
        if (message == null) putNull("message") else put("message", message)
        put("updated_at", System.currentTimeMillis())
      },
      "source_id = ?",
      arrayOf(sourceId),
    )
    if (status == SmbCoverPrefetchStatus.COMPLETED || status == SmbCoverPrefetchStatus.SKIPPED) {
      trimHistory()
    }
  }

  private fun pruneMissingBooks() {
    database.writable.execSQL(
      """
        DELETE FROM $TABLE
        WHERE source_id NOT IN (
          SELECT source_id FROM library_items WHERE source = ?
        )
      """.trimIndent(),
      arrayOf(LibrarySource.SMB.name),
    )
  }

  private fun trimHistory() {
    ensureSchema()
    database.writable.execSQL(
      """
        DELETE FROM $TABLE
        WHERE status IN ('COMPLETED', 'SKIPPED')
          AND source_id NOT IN (
            SELECT source_id
            FROM $TABLE
            WHERE status IN ('COMPLETED', 'SKIPPED')
            ORDER BY updated_at DESC
            LIMIT $HISTORY_LIMIT
          )
      """.trimIndent(),
    )
  }

  private fun ensureSchema() {
    database.writable.execSQL(
      """
        CREATE TABLE IF NOT EXISTS $TABLE(
          source_id TEXT PRIMARY KEY NOT NULL,
          title TEXT NOT NULL,
          status TEXT NOT NULL,
          downloaded_bytes INTEGER NOT NULL DEFAULT 0,
          total_bytes INTEGER NOT NULL DEFAULT 0,
          message TEXT,
          updated_at INTEGER NOT NULL
        )
      """.trimIndent(),
    )
    database.writable.execSQL(
      "CREATE INDEX IF NOT EXISTS idx_smb_cover_prefetch_status ON $TABLE(status, updated_at)",
    )
  }

  private companion object {
    const val TABLE = "smb_cover_prefetch_queue"
    const val VISIBLE_ITEM_LIMIT = 200
    const val HISTORY_LIMIT = 200
  }
}

internal data class SmbCoverPrefetchQueueEntry(
  val sourceId: String,
  val title: String,
)

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
