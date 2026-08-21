package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibrarySource
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
              db.execSQL(
                """
                  CREATE TABLE smb_cover_prefetch_queue(
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
}
