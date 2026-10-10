package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchScheduler
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationBatchStatus
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationScheduler
import dev.terashima.yomitorirss.feature.library.SmbMetadataNormalizationStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryBackupRestoreInitializerTest {
  private lateinit var context: Context
  private lateinit var database: YomitoriDatabase

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
    database = YomitoriDatabase.create(
      context,
      DatabaseSchema(
        version = 1,
        contributions = listOf(
          DatabaseSchemaContribution(
            owner = "library-backup-restore-test",
            createSchema = { db ->
              db.execSQL(
                """
                  CREATE TABLE library_items(
                    source TEXT NOT NULL,
                    source_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    thumbnail_url TEXT,
                    PRIMARY KEY(source, source_id)
                  )
                """.trimIndent(),
              )
              ensureSmbCoverPrefetchQueueSchema(db)
              ensureSmbMetadataNormalizationSchema(db)
            },
          ),
        ),
      ),
    )
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `復元後はSMBのローカル表紙参照と旧キューを破棄する`() {
    insertBook(LibrarySource.SMB, "smb-local", "file:///data/user/0/app/cache/smb-book-covers/cover.jpg")
    insertBook(LibrarySource.SMB, "smb-remote", "https://example.invalid/cover.jpg")
    insertBook(LibrarySource.KINDLE, "kindle", "https://example.invalid/kindle.jpg")
    database.writableDatabase.insertOrThrow(
      "smb_cover_prefetch_queue",
      null,
      ContentValues().apply {
        put("source_id", "smb-local")
        put("title", "SMB local")
        put("status", "COMPLETED")
        put("downloaded_bytes", 0L)
        put("total_bytes", 0L)
        putNull("message")
        put("updated_at", 1L)
      },
    )

    LibraryBackupRestoreInitializer(DatabaseConnection(database)).initialize()

    assertNull(thumbnailUrl(LibrarySource.SMB, "smb-local"))
    assertEquals("https://example.invalid/cover.jpg", thumbnailUrl(LibrarySource.SMB, "smb-remote"))
    assertEquals("https://example.invalid/kindle.jpg", thumbnailUrl(LibrarySource.KINDLE, "kindle"))
    assertEquals(0, queueCount())
  }

  @Test
  fun `実行中batchの表紙待ちを先に再キューしてから解析workerを登録する`() {
    insertBook(LibrarySource.SMB, "smb-waiting-cover", null)
    insertNormalizationBatch("batch-running", SmbMetadataNormalizationBatchStatus.RUNNING)
    insertNormalizationItem(
      batchId = "batch-running",
      sourceId = "smb-waiting-cover",
      status = SmbMetadataNormalizationStatus.WAITING_FOR_COVER,
    )
    val scheduled = mutableListOf<String>()

    LibraryBackupRestoreInitializer(
      database = DatabaseConnection(database),
      normalizationScheduler = RecordingNormalizationScheduler(scheduled),
      coverPrefetchScheduler = RecordingCoverPrefetchScheduler(scheduled),
    ).initialize()

    assertEquals(listOf("cover-prefetch", "normalization"), scheduled)
    assertEquals(1, queueCount())
    assertEquals("PENDING", queueStatus("smb-waiting-cover"))
  }

  @Test
  fun `実行中batchに表紙待ちがなければ解析workerだけを登録する`() {
    insertBook(LibrarySource.SMB, "smb-queued", null)
    insertNormalizationBatch("batch-running", SmbMetadataNormalizationBatchStatus.RUNNING)
    insertNormalizationItem(
      batchId = "batch-running",
      sourceId = "smb-queued",
      status = SmbMetadataNormalizationStatus.QUEUED,
    )
    val scheduled = mutableListOf<String>()

    LibraryBackupRestoreInitializer(
      database = DatabaseConnection(database),
      normalizationScheduler = RecordingNormalizationScheduler(scheduled),
      coverPrefetchScheduler = RecordingCoverPrefetchScheduler(scheduled),
    ).initialize()

    assertEquals(listOf("normalization"), scheduled)
    assertEquals(0, queueCount())
  }

  @Test
  fun `完了batchは表紙先読みも解析workerも再登録しない`() {
    insertBook(LibrarySource.SMB, "smb-completed", null)
    insertNormalizationBatch("batch-completed", SmbMetadataNormalizationBatchStatus.COMPLETED)
    insertNormalizationItem(
      batchId = "batch-completed",
      sourceId = "smb-completed",
      status = SmbMetadataNormalizationStatus.WAITING_FOR_COVER,
    )
    val scheduled = mutableListOf<String>()

    LibraryBackupRestoreInitializer(
      database = DatabaseConnection(database),
      normalizationScheduler = RecordingNormalizationScheduler(scheduled),
      coverPrefetchScheduler = RecordingCoverPrefetchScheduler(scheduled),
    ).initialize()

    assertEquals(emptyList<String>(), scheduled)
    assertEquals(0, queueCount())
  }

  private fun insertNormalizationBatch(batchId: String, status: SmbMetadataNormalizationBatchStatus) {
    val now = System.currentTimeMillis()
    database.writableDatabase.insertOrThrow(
      SMB_METADATA_NORMALIZATION_BATCH_TABLE,
      null,
      ContentValues().apply {
        put("batch_id", batchId)
        put("status", status.name)
        put("created_at", now)
        put("updated_at", now)
      },
    )
  }

  private fun insertNormalizationItem(
    batchId: String,
    sourceId: String,
    status: SmbMetadataNormalizationStatus,
  ) {
    val now = System.currentTimeMillis()
    database.writableDatabase.insertOrThrow(
      SMB_METADATA_NORMALIZATION_ITEM_TABLE,
      null,
      ContentValues().apply {
        put("batch_id", batchId)
        put("source_id", sourceId)
        put("original_file_name", "$sourceId.epub")
        put("input_size", 1L)
        put("input_modified_at", now)
        put("status", status.name)
        putNull("proposed_file_name")
        putNull("metadata_json")
        putNull("reanalysis_context")
        putNull("error")
        put("created_at", now)
        put("updated_at", now)
      },
    )
  }

  private fun insertBook(source: LibrarySource, sourceId: String, thumbnailUrl: String?) {
    database.writableDatabase.insertOrThrow(
      "library_items",
      null,
      ContentValues().apply {
        put("source", source.name)
        put("source_id", sourceId)
        put("title", sourceId)
        if (thumbnailUrl == null) putNull("thumbnail_url") else put("thumbnail_url", thumbnailUrl)
      },
    )
  }

  private fun thumbnailUrl(source: LibrarySource, sourceId: String): String? =
    database.readableDatabase.rawQuery(
      "SELECT thumbnail_url FROM library_items WHERE source = ? AND source_id = ?",
      arrayOf(source.name, sourceId),
    ).use { cursor ->
      check(cursor.moveToFirst())
      if (cursor.isNull(0)) null else cursor.getString(0)
    }

  private fun queueCount(): Int = database.readableDatabase.rawQuery(
    "SELECT COUNT(*) FROM smb_cover_prefetch_queue",
    null,
  ).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getInt(0)
  }

  private fun queueStatus(sourceId: String): String? = database.readableDatabase.rawQuery(
    "SELECT status FROM smb_cover_prefetch_queue WHERE source_id = ?",
    arrayOf(sourceId),
  ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

  private class RecordingCoverPrefetchScheduler(
    private val scheduled: MutableList<String>,
  ) : SmbCoverPrefetchScheduler {
    override fun enqueue() {
      scheduled += "cover-prefetch"
    }

    override fun reschedule() = Unit
  }

  private class RecordingNormalizationScheduler(
    private val scheduled: MutableList<String>,
  ) : SmbMetadataNormalizationScheduler {
    override fun kick() {
      scheduled += "normalization"
    }

    override suspend fun cancel() = Unit

    override fun setResumeOnChargingScheduled(enabled: Boolean) = Unit
  }
}
