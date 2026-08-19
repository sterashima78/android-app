package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarkContentQueryTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var query: DefaultBookmarkContentQuery

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE bookmarks(article_id TEXT PRIMARY KEY,saved_at TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bookmark_folders(id TEXT PRIMARY KEY,system_kind TEXT)")
        db.execSQL("CREATE TABLE article_folders(article_id TEXT PRIMARY KEY,folder_id TEXT NOT NULL)")
      }
      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    query = DefaultBookmarkContentQuery(DatabaseConnection(helper))
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `BookmarkとReadLaterをCuration所有tableから問い合わせる`() {
    val db = helper.writableDatabase
    db.execSQL("INSERT INTO bookmarks(article_id,saved_at) VALUES('saved','now'),('later','now')")
    db.execSQL("INSERT INTO bookmark_folders(id,system_kind) VALUES('read-later','read_later')")
    db.execSQL("INSERT INTO article_folders(article_id,folder_id) VALUES('later','read-later')")

    val ids = setOf("saved", "later", "other")

    assertEquals(setOf("saved", "later"), query.bookmarkedContentIds(ids))
    assertEquals(setOf("later"), query.readLaterContentIds(ids))
    assertEquals(setOf("saved", "later"), query.protectedContentIds(ids))
  }
}
