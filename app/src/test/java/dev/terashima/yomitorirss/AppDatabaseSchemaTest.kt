package dev.terashima.yomitorirss

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = YomitoriApplication::class)
class AppDatabaseSchemaTest {
  private lateinit var context: Context
  private var database: YomitoriDatabase? = null

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @After
  fun tearDown() {
    database?.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `fresh database composes all feature schemas`() {
    val db = openDatabase().writableDatabase

    assertEquals(16, db.version)
    assertEquals(
      setOf(
        "feed_folders",
        "feeds",
        "articles",
        "tags",
        "article_tags",
        "bookmark_folders",
        "article_folders",
        "article_summaries",
        "summary_tasks",
        "knowledge_pages",
        "knowledge_page_sources",
        "mail_accounts",
        "mail_labels",
        "mail_threads",
        "mail_messages",
        "smb_library_servers",
        "library_organization_tags",
        "library_organization_collections",
        "library_item_organization_tags",
        "library_item_organization_collections",
        "library_item_reading_status",
      ),
      db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata'",
        null,
      ).use { cursor ->
        buildSet {
          while (cursor.moveToNext()) add(cursor.getString(0))
        }
      },
    )
  }

  @Test
  fun `tag is removed only after its last article relation is deleted`() {
    val db = openDatabase().writableDatabase
    insertBookmarkedArticle(db, "article-1")
    insertBookmarkedArticle(db, "article-2")
    insertTag(db, "tag-1", "Android")
    insertArticleTag(db, "article-1", "tag-1")
    insertArticleTag(db, "article-2", "tag-1")

    db.delete("article_tags", "article_id=?", arrayOf("article-1"))
    assertEquals(1, countRows(db, "tags", "id=?", arrayOf("tag-1")))

    db.delete("article_tags", "article_id=?", arrayOf("article-2"))
    assertEquals(0, countRows(db, "tags", "id=?", arrayOf("tag-1")))
  }

  private fun openDatabase(): YomitoriDatabase = YomitoriDatabase.create(context, appDatabaseSchema).also {
    database = it
  }
}

private fun insertBookmarkedArticle(db: SQLiteDatabase, id: String) {
  db.insertOrThrow(
    "articles",
    null,
    ContentValues().apply {
      put("id", id)
      putNull("feed_id")
      putNull("external_id")
      put("identity_key", "test:$id")
      put("url", "https://example.com/$id")
      put("title", id)
      put("published_at", "2026-08-14T00:00:00Z")
      put("fetched_at", "2026-08-14T00:00:00Z")
      putNull("read_at")
      put("saved_at", "2026-08-14T00:00:00Z")
      put("source_title", "test")
      put("source_feed_url", "")
    },
  )
}

private fun insertTag(db: SQLiteDatabase, id: String, name: String) {
  db.insertOrThrow(
    "tags",
    null,
    ContentValues().apply {
      put("id", id)
      put("name", name)
      put("normalized_name", name.lowercase())
      put("created_at", "2026-08-14T00:00:00Z")
    },
  )
}

private fun insertArticleTag(db: SQLiteDatabase, articleId: String, tagId: String) {
  db.insertOrThrow(
    "article_tags",
    null,
    ContentValues().apply {
      put("article_id", articleId)
      put("tag_id", tagId)
    },
  )
}

private fun countRows(
  db: SQLiteDatabase,
  table: String,
  selection: String,
  selectionArgs: Array<String>,
): Int = db.rawQuery(
  "SELECT COUNT(*) FROM $table WHERE $selection",
  selectionArgs,
).use { cursor ->
  check(cursor.moveToFirst())
  cursor.getInt(0)
}
