package dev.terashima.yomitorirss.feature.rss.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ContentSourceGateway
import dev.terashima.yomitorirss.feature.article.ContentSourceSnapshot
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.SourceContentItem
import dev.terashima.yomitorirss.feature.rss.data.network.ParsedFeed
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedStoreTransactionTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var gateway: RecordingContentGateway
  private lateinit var store: FeedStore

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
          """
            CREATE TABLE feeds(
              id TEXT PRIMARY KEY, title TEXT, custom_title TEXT, feed_url TEXT,
              site_url TEXT, etag TEXT, last_modified TEXT, last_fetched_at TEXT,
              last_error TEXT, created_at TEXT, folder_id TEXT, content_type TEXT,
              refresh_interval_minutes INTEGER
            )
          """.trimIndent(),
        )
        db.execSQL("CREATE TABLE feed_folders(id TEXT PRIMARY KEY, content_type TEXT)")
        db.execSQL("CREATE TABLE content_records(source_id TEXT PRIMARY KEY, source_title TEXT)")
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    val database = DatabaseConnection(helper)
    gateway = RecordingContentGateway(database)
    store = FeedStore(database, gateway)
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun failedContentInsertRollsBackNewFeed() {
    gateway.failAfterWrite = true

    assertThrows(IllegalStateException::class.java) {
      store.addFeed(parsed("Original"), null, null)
    }

    assertEquals(0, rowCount("feeds"))
    assertEquals(0, rowCount("content_records"))
  }

  @Test
  fun failedContentRenameRollsBackFeedRename() {
    val feed = store.addFeed(parsed("Original"), null, null)
    gateway.failAfterWrite = true

    assertThrows(IllegalStateException::class.java) {
      store.renameFeed(feed.id, "Changed")
    }

    assertNull(feedValue(feed.id, "custom_title"))
    assertEquals("Original", contentTitle(feed.id))
  }

  @Test
  fun failedContentRefreshPreservesPriorFetchMetadata() {
    val feed = store.addFeed(parsed("Original"), null, null)
    val fetchedAt = feedValue(feed.id, "last_fetched_at")
    gateway.failAfterWrite = true

    assertThrows(IllegalStateException::class.java) {
      store.updateFeedSuccess(feed, parsed("Changed"), "new-etag", null)
    }

    assertEquals("Original", feedValue(feed.id, "title"))
    assertNull(feedValue(feed.id, "etag"))
    assertEquals(fetchedAt, feedValue(feed.id, "last_fetched_at"))
    assertEquals("Original", contentTitle(feed.id))
  }

  @Test
  fun failedContentDetachRollsBackFeedDeletion() {
    val feed = store.addFeed(parsed("Original"), null, null)
    gateway.failAfterWrite = true

    assertThrows(IllegalStateException::class.java) {
      store.deleteFeed(feed.id)
    }

    assertEquals(1, rowCount("feeds"))
    assertEquals("Original", contentTitle(feed.id))
  }

  private fun parsed(title: String) = ParsedFeed(
    title = title,
    feedUrl = "https://example.invalid/feed",
    siteUrl = null,
    articles = emptyList(),
  )

  private fun rowCount(table: String): Int =
    helper.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
      check(cursor.moveToFirst())
      cursor.getInt(0)
    }

  private fun feedValue(id: String, column: String): String? =
    helper.readableDatabase.rawQuery("SELECT $column FROM feeds WHERE id=?", arrayOf(id)).use { cursor ->
      check(cursor.moveToFirst())
      if (cursor.isNull(0)) null else cursor.getString(0)
    }

  private fun contentTitle(sourceId: String): String? =
    helper.readableDatabase.rawQuery(
      "SELECT source_title FROM content_records WHERE source_id=?",
      arrayOf(sourceId),
    ).use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}

private class RecordingContentGateway(private val database: DatabaseConnection) : ContentSourceGateway {
  var failAfterWrite: Boolean = false

  override fun upsertSourceContent(
    source: ContentSourceSnapshot,
    items: List<SourceContentItem>,
    fetchedAt: String,
    insertedReadAt: String?,
  ) {
    database.write {
      insertWithOnConflict(
        "content_records",
        null,
        ContentValues().apply {
          put("source_id", source.id)
          put("source_title", source.title)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
    failIfRequested()
  }

  override fun renameSourceContent(sourceId: String, sourceTitle: String) {
    database.write {
      update(
        "content_records",
        ContentValues().apply { put("source_title", sourceTitle) },
        "source_id=?",
        arrayOf(sourceId),
      )
    }
    failIfRequested()
  }

  override fun detachSourceContent(sourceId: String, inheritedContentType: ContentType?) {
    database.write { delete("content_records", "source_id=?", arrayOf(sourceId)) }
    failIfRequested()
  }

  private fun failIfRequested() {
    if (failAfterWrite) error("Simulated content persistence failure")
  }
}
