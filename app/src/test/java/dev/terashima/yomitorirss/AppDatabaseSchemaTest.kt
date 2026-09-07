package dev.terashima.yomitorirss

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    assertEquals(30, db.version)
    assertTrue("content_type" in columnNames(db, "feed_folders"))
    assertTrue("content_type" in columnNames(db, "feeds"))
    assertTrue("custom_title" in columnNames(db, "feeds"))
    assertTrue("timeout_seconds" in columnNames(db, "rss_web_scraping_rules"))
    assertTrue("content_type" in columnNames(db, "articles"))
    assertFalse("saved_at" in columnNames(db, "articles"))
    assertTrue("timeout_seconds" in columnNames(db, "web_library_metadata_extractors"))
    assertEquals(
      setOf(
        "feed_folders",
        "feeds",
        "rss_web_scraping_rules",
        "articles",
        "bookmarks",
        "tags",
        "article_tags",
        "bookmark_folders",
        "article_folders",
        "article_summaries",
        "summary_tasks",
        "summary_article_content",
        "knowledge_pages",
        "knowledge_page_sources",
        "mail_accounts",
        "mail_labels",
        "mail_threads",
        "mail_messages",
        "library_items",
        "library_sources",
        "hidden_library_items",
        "library_item_series",
        "library_item_series_exclusions",
        "library_source_series",
        "library_audible_source_series",
        "web_library_metadata_extractors",
        "smb_library_servers",
        "smb_connection_profiles",
        "smb_cover_prefetch_queue",
        "smb_metadata_normalization_batches",
        "smb_metadata_normalization_items",
        "smb_metadata_normalization_decisions",
        "library_organization_tags",
        "library_organization_collections",
        "library_item_organization_tags",
        "library_item_organization_collections",
        "library_item_reading_status",
        "library_organization_batches",
        "library_organization_batch_items",
        "asset_entries",
        "asset_categories",
        "asset_category_definitions",
        "tasks",
        "chat_sessions",
        "chat_messages",
        "channels",
        "videos",
        "video_items",
        "video_playback_state",
        "video_smb_sources",
        "video_folders",
        "video_saved_items",
        "video_web_extractor_rules",
      ),
      tableNames(db),
    )
  }

  @Test
  fun `version 28 database migrates shared SMB profile and Video location`() {
    val previousSchema = DatabaseSchema(
      version = 28,
      contributions = listOf(
        DatabaseSchemaContribution(
          owner = "legacy-v28",
          createSchema = { db ->
            db.execSQL(
              """
                CREATE TABLE smb_library_servers(
                  id TEXT PRIMARY KEY NOT NULL,
                  name TEXT NOT NULL,
                  host TEXT NOT NULL,
                  port INTEGER NOT NULL,
                  share_name TEXT NOT NULL,
                  root_path TEXT NOT NULL,
                  username TEXT NOT NULL,
                  domain_name TEXT NOT NULL,
                  updated_at INTEGER NOT NULL
                )
              """.trimIndent(),
            )
            db.execSQL(
              """
                CREATE TABLE video_items(
                  id TEXT PRIMARY KEY NOT NULL,
                  source TEXT NOT NULL,
                  source_id TEXT NOT NULL,
                  title TEXT NOT NULL,
                  page_url TEXT,
                  thumbnail_url TEXT,
                  duration_ms INTEGER,
                  size_bytes INTEGER,
                  mime_type TEXT,
                  updated_at INTEGER NOT NULL,
                  UNIQUE(source, source_id)
                )
              """.trimIndent(),
            )
            db.execSQL(
              """
                CREATE TABLE video_playback_state(
                  video_id TEXT PRIMARY KEY NOT NULL,
                  position_ms INTEGER NOT NULL,
                  duration_ms INTEGER NOT NULL,
                  last_played_at INTEGER NOT NULL,
                  completed INTEGER NOT NULL DEFAULT 0
                )
              """.trimIndent(),
            )
            db.execSQL(
              """
                CREATE TABLE video_web_extractor_rules(
                  id TEXT PRIMARY KEY NOT NULL,
                  url_pattern TEXT NOT NULL,
                  title_function TEXT,
                  thumbnail_function TEXT,
                  playback_function TEXT,
                  timeout_seconds INTEGER NOT NULL DEFAULT 15,
                  updated_at INTEGER NOT NULL
                )
              """.trimIndent(),
            )
          },
        ),
      ),
    )
    val legacy = YomitoriDatabase.create(context, previousSchema)
    legacy.writableDatabase.insertOrThrow(
      "smb_library_servers",
      null,
      ContentValues().apply {
        put("id", "legacy-server")
        put("name", "Home NAS")
        put("host", "nas.example.invalid")
        put("port", 445)
        put("share_name", "media")
        put("root_path", "videos")
        put("username", "reader")
        put("domain_name", "")
        put("updated_at", 1234L)
      },
    )
    legacy.close()

    val db = openDatabase().writableDatabase

    assertEquals(30, db.version)
    assertEquals(1, countRows(db, "smb_connection_profiles", "id=?", arrayOf("legacy-server")))
    assertEquals(1, countRows(db, "video_smb_sources", "server_id=?", arrayOf("legacy-server")))
    assertEquals(
      "media",
      singleString(db, "SELECT share_name FROM video_smb_sources WHERE server_id = ?", arrayOf("legacy-server")),
    )
    assertEquals(
      "videos",
      singleString(db, "SELECT root_path FROM video_smb_sources WHERE server_id = ?", arrayOf("legacy-server")),
    )
    assertEquals(0, countRows(db, "video_saved_items", "1=1", emptyArray()))
  }

  @Test
  fun `version 29 database adds saved Video tables without auto-saving items`() {
    val previousSchema = DatabaseSchema(
      version = 29,
      contributions = listOf(
        DatabaseSchemaContribution(
          owner = "legacy-v29",
          createSchema = { db ->
            db.execSQL(
              """
                CREATE TABLE video_items(
                  id TEXT PRIMARY KEY NOT NULL,
                  source TEXT NOT NULL,
                  source_id TEXT NOT NULL,
                  title TEXT NOT NULL,
                  page_url TEXT,
                  thumbnail_url TEXT,
                  duration_ms INTEGER,
                  size_bytes INTEGER,
                  mime_type TEXT,
                  updated_at INTEGER NOT NULL,
                  UNIQUE(source, source_id)
                )
              """.trimIndent(),
            )
          },
        ),
      ),
    )
    val legacy = YomitoriDatabase.create(context, previousSchema)
    legacy.writableDatabase.insertOrThrow(
      "video_items",
      null,
      ContentValues().apply {
        put("id", "legacy-video")
        put("source", "WEB")
        put("source_id", "https://example.invalid/video")
        put("title", "Legacy video")
        put("page_url", "https://example.invalid/video")
        put("updated_at", 1234L)
      },
    )
    legacy.close()

    val db = openDatabase().writableDatabase

    assertEquals(30, db.version)
    assertEquals(1, countRows(db, "video_items", "id=?", arrayOf("legacy-video")))
    assertEquals(0, countRows(db, "video_folders", "1=1", emptyArray()))
    assertEquals(0, countRows(db, "video_saved_items", "1=1", emptyArray()))
  }

  @Test
  fun `tag is removed only after its last article relation is deleted`() {
    val db = openDatabase().writableDatabase
    insertArticle(db, "article-1")
    insertArticle(db, "article-2")
    insertTag(db, "tag-1", "Android")
    insertArticleTag(db, "article-1", "tag-1")
    insertArticleTag(db, "article-2", "tag-1")

    db.delete("article_tags", "article_id=?", arrayOf("article-1"))
    assertEquals(1, countRows(db, "tags", "id=?", arrayOf("tag-1")))

    db.delete("article_tags", "article_id=?", arrayOf("article-2"))
    assertEquals(0, countRows(db, "tags", "id=?", arrayOf("tag-1")))
  }

  private fun openDatabase(): YomitoriDatabase =
    YomitoriDatabase.create(context, appDatabaseSchema).also { database = it }
}

private fun insertArticle(db: SQLiteDatabase, id: String) {
  db.insertOrThrow(
    "articles",
    null,
    ContentValues().apply {
      put("id", id)
      putNull("feed_id")
      putNull("external_id")
      put("identity_key", "test:$id")
      put("url", "https://example.invalid/$id")
      put("title", id)
      put("published_at", "2026-01-01T00:00:00Z")
      put("fetched_at", "2026-01-01T00:00:00Z")
      putNull("read_at")
      put("source_title", "test")
      put("source_feed_url", "")
      putNull("content_type")
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
      put("created_at", "2026-01-01T00:00:00Z")
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

private fun columnNames(db: SQLiteDatabase, table: String): Set<String> =
  db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    buildSet {
      while (cursor.moveToNext()) add(cursor.getString(nameIndex))
    }
  }

private fun tableNames(db: SQLiteDatabase): Set<String> = db.rawQuery(
  "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name<>'android_metadata'",
  null,
).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

private fun countRows(
  db: SQLiteDatabase,
  table: String,
  selection: String,
  args: Array<String>,
): Int = db.rawQuery("SELECT COUNT(*) FROM $table WHERE $selection", args).use { cursor ->
  check(cursor.moveToFirst())
  cursor.getInt(0)
}

private fun singleString(
  db: SQLiteDatabase,
  sql: String,
  args: Array<String>,
): String = db.rawQuery(sql, args).use { cursor ->
  check(cursor.moveToFirst())
  cursor.getString(0)
}
