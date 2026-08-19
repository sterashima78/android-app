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
class BookmarkTagStoreTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var store: BookmarkTagStore

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
          "CREATE TABLE tags(" +
            "id TEXT PRIMARY KEY," +
            "name TEXT NOT NULL," +
            "normalized_name TEXT NOT NULL," +
            "created_at TEXT NOT NULL)",
        )
        db.execSQL(
          "CREATE TABLE article_tags(" +
            "article_id TEXT NOT NULL," +
            "tag_id TEXT NOT NULL)",
        )
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    store = BookmarkTagStore(DatabaseConnection(helper))
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `記事に関連付いていないタグだけを一括削除する`() {
    val db = helper.writableDatabase
    db.execSQL(
      "INSERT INTO tags(id,name,normalized_name,created_at) VALUES" +
        "('used','Used','used','now')," +
        "('unused-1','Unused 1','unused 1','now')," +
        "('unused-2','Unused 2','unused 2','now')",
    )
    db.execSQL("INSERT INTO article_tags(article_id,tag_id) VALUES('article','used')")

    val deletedCount = store.deleteUnusedTags()

    assertEquals(2, deletedCount)
    assertEquals(listOf("used"), store.listTags().map { it.id })
  }
}
