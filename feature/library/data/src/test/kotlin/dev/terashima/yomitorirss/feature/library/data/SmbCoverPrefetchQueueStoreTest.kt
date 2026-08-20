package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.SmbCoverPrefetchStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmbCoverPrefetchQueueStoreTest {
  private lateinit var context: Context
  private lateinit var database: YomitoriDatabase
  private lateinit var queue: SmbCoverPrefetchQueueStore

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
            owner = "library-test",
            createSchema = { db ->
              db.execSQL(
                """
                  CREATE TABLE IF NOT EXISTS library_items(
                    source TEXT NOT NULL,
                    source_id TEXT NOT NULL,
                    title TEXT NOT NULL,
                    thumbnail_url TEXT,
                    PRIMARY KEY(source, source_id)
                  )
                """.trimIndent(),
              )
            },
          ),
        ),
      ),
    )
    queue = SmbCoverPrefetchQueueStore(DatabaseConnection(database))
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `未取得表紙を待機から実行中へ進めて進捗を保存できる`() {
    insertBook("book-1", "Book 1", thumbnailUrl = null)

    assertEquals(1, queue.enqueueMissing())
    assertEquals(1, queue.snapshot().pendingCount)

    val claimed = queue.claimNext()
    assertNotNull(claimed)
    assertEquals("book-1", claimed?.sourceId)

    queue.updateProgress("book-1", downloadedBytes = 3L * 1024 * 1024, totalBytes = 8L * 1024 * 1024)
    val running = queue.snapshot()
    assertEquals(1, running.runningCount)
    assertEquals(SmbCoverPrefetchStatus.RUNNING, running.items.first().status)
    assertEquals(3L * 1024 * 1024, running.items.first().downloadedBytes)
    assertEquals(8L * 1024 * 1024, running.items.first().totalBytes)
  }

  @Test
  fun `失敗した表紙先読みを一括で待機へ戻せる`() {
    insertBook("book-1", "Book 1", thumbnailUrl = null)
    queue.enqueueMissing()
    queue.claimNext()
    queue.fail("book-1", "network error")

    assertEquals(1, queue.snapshot().failedCount)
    assertEquals(1, queue.retryFailed())

    val retried = queue.snapshot()
    assertEquals(1, retried.pendingCount)
    assertEquals(0, retried.failedCount)
    assertNull(retried.items.first().message)
  }

  @Test
  fun `表紙取得済みの書籍はキューへ追加しない`() {
    insertBook("book-1", "Book 1", thumbnailUrl = "file:///cover/book-1.jpg")

    assertEquals(0, queue.enqueueMissing())
    assertEquals(0, queue.snapshot().pendingCount)
  }

  @Test
  fun `LRU削除された表紙は対象外履歴として記録できる`() {
    insertBook("book-1", "Book 1", thumbnailUrl = null)

    queue.markSkipped("book-1", "Book 1", "表紙キャッシュ上限により削除")

    val snapshot = queue.snapshot()
    assertEquals(1, snapshot.skippedCount)
    assertEquals(SmbCoverPrefetchStatus.SKIPPED, snapshot.items.first().status)
    assertEquals("表紙キャッシュ上限により削除", snapshot.items.first().message)
  }

  private fun insertBook(sourceId: String, title: String, thumbnailUrl: String?) {
    database.writableDatabase.insertOrThrow(
      "library_items",
      null,
      ContentValues().apply {
        put("source", LibrarySource.SMB.name)
        put("source_id", sourceId)
        put("title", title)
        if (thumbnailUrl == null) putNull("thumbnail_url") else put("thumbnail_url", thumbnailUrl)
      },
    )
  }
}
