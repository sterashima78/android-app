package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.Tag
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookmarkTagStoreTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var database: DatabaseConnection
  private lateinit var tagStore: BookmarkTagStore
  private lateinit var associationStore: BookmarkAssociationStore

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
          "CREATE TABLE bookmarks(" +
            "article_id TEXT PRIMARY KEY," +
            "saved_at TEXT NOT NULL)",
        )
        db.execSQL(
          "CREATE TABLE article_tags(" +
            "article_id TEXT NOT NULL," +
            "tag_id TEXT NOT NULL)",
        )
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    database = DatabaseConnection(helper)
    tagStore = BookmarkTagStore(database)
    associationStore = BookmarkAssociationStore(database)
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `ブックマークに関連付いていないタグだけを一括削除する`() {
    val db = helper.writableDatabase
    db.execSQL(
      "INSERT INTO tags(id,name,normalized_name,created_at) VALUES" +
        "('used','Used','used','now')," +
        "('stale','Stale','stale','now')," +
        "('unused','Unused','unused','now')",
    )
    db.execSQL("INSERT INTO bookmarks(article_id,saved_at) VALUES('bookmarked','now')")
    db.execSQL(
      "INSERT INTO article_tags(article_id,tag_id) VALUES" +
        "('bookmarked','used')," +
        "('not-bookmarked','stale')",
    )

    val deletedCount = database.transaction {
      val associatedTagIds = associationStore.listAssociatedTagIds()
      val unusedTagIds = tagStore.listTags()
        .asSequence()
        .map(Tag::id)
        .filterNot(associatedTagIds::contains)
        .toSet()
      tagStore.deleteTags(unusedTagIds)
    }

    assertEquals(2, deletedCount)
    assertEquals(listOf("used"), tagStore.listTags().map { it.id })
  }
}
