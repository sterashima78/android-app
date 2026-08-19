package dev.terashima.yomitorirss.feature.article.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ContentSourceSnapshot
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.SourceContentItem
import dev.terashima.yomitorirss.feature.bookmark.BookmarkContentQuery
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentSourceGatewayTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var bookmarks: FakeBookmarkContentQuery
  private lateinit var gateway: DefaultContentSourceGateway

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
          """
            CREATE TABLE articles(
              id TEXT PRIMARY KEY,
              feed_id TEXT,
              external_id TEXT,
              identity_key TEXT UNIQUE,
              url TEXT,
              title TEXT,
              published_at TEXT,
              fetched_at TEXT,
              read_at TEXT,
              source_title TEXT,
              source_feed_url TEXT,
              content_type TEXT
            )
          """.trimIndent(),
        )
      }
      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    bookmarks = FakeBookmarkContentQuery()
    gateway = DefaultContentSourceGateway(DatabaseConnection(helper), bookmarks)
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `Source ingestionはContent ownerを通して保存する`() {
    gateway.upsertSourceContent(
      source = ContentSourceSnapshot("feed-1", "Feed", "https://example.com/feed"),
      items = listOf(SourceContentItem("ext", "identity", "https://example.com/a", "Article", "2026-08-19T00:00:00Z")),
      fetchedAt = "2026-08-19T01:00:00Z",
    )

    helper.readableDatabase.rawQuery("SELECT feed_id,source_title FROM articles", null).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("feed-1", cursor.getString(0))
      assertEquals("Feed", cursor.getString(1))
    }
  }

  @Test
  fun `Source削除はBookmark Contentを保持して未保存Contentを削除する`() {
    gateway.upsertSourceContent(
      source = ContentSourceSnapshot("feed-1", "Feed", "https://example.com/feed"),
      items = listOf(
        SourceContentItem("saved", "saved", "https://example.com/saved", "Saved", "2026-08-19T00:00:00Z"),
        SourceContentItem("other", "other", "https://example.com/other", "Other", "2026-08-19T00:00:00Z"),
      ),
      fetchedAt = "2026-08-19T01:00:00Z",
    )
    val savedId = articleId("https://example.com/saved")
    bookmarks.bookmarkedIds = setOf(savedId)

    gateway.detachSourceContent("feed-1", ContentType.COMIC)

    helper.readableDatabase.rawQuery("SELECT feed_id,content_type FROM articles WHERE id=?", arrayOf(savedId)).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertTrue(cursor.isNull(0))
      assertEquals(ContentType.COMIC.name, cursor.getString(1))
    }
    assertFalse(articleExists("https://example.com/other"))
  }

  private fun articleId(url: String): String = helper.readableDatabase.rawQuery(
    "SELECT id FROM articles WHERE url=?",
    arrayOf(url),
  ).use { cursor -> check(cursor.moveToFirst()); cursor.getString(0) }

  private fun articleExists(url: String): Boolean = helper.readableDatabase.rawQuery(
    "SELECT 1 FROM articles WHERE url=? LIMIT 1",
    arrayOf(url),
  ).use { it.moveToFirst() }
}

private class FakeBookmarkContentQuery : BookmarkContentQuery {
  var bookmarkedIds: Set<String> = emptySet()
  override fun bookmarkedContentIds(contentIds: Set<String>): Set<String> = contentIds intersect bookmarkedIds
  override fun readLaterContentIds(contentIds: Set<String>): Set<String> = emptySet()
}
