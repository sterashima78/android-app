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

    assertEquals(34, db.version)
    assertTrue("content_type" in columnNames(db, "feed_folders"))
    assertTrue("content_type" in columnNames(db, "feeds"))
    assertTrue("custom_title" in columnNames(db, "feeds"))
    assertTrue("timeout_seconds" in columnNames(db, "rss_web_scraping_rules"))
    assertTrue("content_type" in columnNames(db, "articles"))
    assertFalse("saved_at" in columnNames(db, "articles"))
    assertTrue("timeout_seconds" in columnNames(db, "web_library_metadata_extractors"))
    assertTrue("function_code" in columnNames(db, "video_providers"))
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
        "video_items",
        "video_playback_state",
        "video_smb_sources",
        "video_folders",
        "video_saved_items",
        "video_web_extractor_rules",
        "video_providers",
        "video_subscriptions",
        "video_provider_items",
        "podcast_sources",
        "podcast_programs",
        "podcast_episodes",
        "podcast_episode_articles",
        "podcast_consumed_articles",
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

    assertEquals(34, db.version)
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

    assertEquals(34, db.version)
    assertEquals(1, countRows(db, "video_items", "id=?", arrayOf("legacy-video")))
    assertEquals(0, countRows(db, "video_folders", "1=1", emptyArray()))
    assertEquals(0, countRows(db, "video_saved_items", "1=1", emptyArray()))
  }

  @Test
  fun `version 30 subscription data migrates to Video provider state`() {
    val previousSchema = DatabaseSchema(
      version = 30,
      contributions = listOf(
        DatabaseSchemaContribution(
          owner = "legacy-v30",
          createSchema = { db ->
            db.execSQL(
              """
                CREATE TABLE channels(
                  channel_id TEXT PRIMARY KEY NOT NULL,
                  title TEXT NOT NULL,
                  channel_url TEXT NOT NULL,
                  added_at INTEGER NOT NULL
                )
              """.trimIndent(),
            )
            db.execSQL(
              """
                CREATE TABLE videos(
                  video_id TEXT PRIMARY KEY NOT NULL,
                  channel_id TEXT NOT NULL,
                  title TEXT NOT NULL,
                  video_url TEXT NOT NULL,
                  published_at INTEGER NOT NULL,
                  is_read INTEGER NOT NULL DEFAULT 0,
                  is_watch_later INTEGER NOT NULL DEFAULT 0
                )
              """.trimIndent(),
            )
          },
        ),
      ),
    )
    val legacy = YomitoriDatabase.create(context, previousSchema)
    legacy.writableDatabase.insertOrThrow(
      "channels",
      null,
      ContentValues().apply {
        put("channel_id", "channel-1")
        put("title", "Channel")
        put("channel_url", "https://example.invalid/channel-1")
        put("added_at", 100L)
      },
    )
    legacy.writableDatabase.insertOrThrow(
      "videos",
      null,
      ContentValues().apply {
        put("video_id", "video-unread")
        put("channel_id", "channel-1")
        put("title", "Unread")
        put("video_url", "https://example.invalid/video-unread")
        put("published_at", 200L)
        put("is_read", 0)
        put("is_watch_later", 0)
      },
    )
    legacy.writableDatabase.insertOrThrow(
      "videos",
      null,
      ContentValues().apply {
        put("video_id", "video-read")
        put("channel_id", "channel-1")
        put("title", "Read")
        put("video_url", "https://example.invalid/video-read")
        put("published_at", 150L)
        put("is_read", 1)
        put("is_watch_later", 0)
      },
    )
    legacy.close()

    val db = openDatabase().writableDatabase

    assertEquals(34, db.version)
    assertEquals(1, countRows(db, "video_providers", "provider_type=?", arrayOf("YOUTUBE")))
    assertEquals(1, countRows(db, "video_subscriptions", "source_id=?", arrayOf("channel-1")))
    assertEquals(2, countRows(db, "video_provider_items", "provider_id=?", arrayOf("youtube")))
    assertEquals(1, countRows(db, "video_provider_items", "is_read=0", emptyArray()))
    assertEquals(1, countRows(db, "video_provider_items", "is_read=1", emptyArray()))
    assertEquals(2, countRows(db, "video_items", "source=?", arrayOf("SERVICE")))
    assertTrue("function_code" in columnNames(db, "video_providers"))
  }

  @Test
  fun `version 31 provider data gains custom function column without losing state`() {
    val previousSchema = DatabaseSchema(
      version = 31,
      contributions = listOf(
        DatabaseSchemaContribution(
          owner = "legacy-v31",
          createSchema = { db ->
            db.execSQL(
              """
                CREATE TABLE video_providers(
                  id TEXT PRIMARY KEY NOT NULL,
                  provider_type TEXT NOT NULL UNIQUE,
                  name TEXT NOT NULL,
                  enabled INTEGER NOT NULL DEFAULT 1,
                  created_at INTEGER NOT NULL,
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
      "video_providers",
      null,
      ContentValues().apply {
        put("id", "builtin-provider")
        put("provider_type", "YOUTUBE")
        put("name", "Built-in Provider")
        put("enabled", 1)
        put("created_at", 100L)
        put("updated_at", 200L)
      },
    )
    legacy.close()

    val db = openDatabase().writableDatabase

    assertEquals(34, db.version)
    assertTrue("function_code" in columnNames(db, "video_providers"))
    assertEquals(1, countRows(db, "video_providers", "id=?", arrayOf("builtin-provider")))
    assertEquals(
      "YOUTUBE",
      singleString(db, "SELECT provider_type FROM video_providers WHERE id = ?", arrayOf("builtin-provider")),
    )
  }

  @Test
  fun `version 32 database adds Podcast tables without losing Video provider state`() {
    val previousSchema = DatabaseSchema(
      version = 32,
      contributions = listOf(
        DatabaseSchemaContribution(
          owner = "legacy-v32",
          createSchema = { db ->
            db.execSQL(
              """
                CREATE TABLE video_providers(
                  id TEXT PRIMARY KEY NOT NULL,
                  provider_type TEXT NOT NULL UNIQUE,
                  name TEXT NOT NULL,
                  enabled INTEGER NOT NULL DEFAULT 1,
                  function_code TEXT,
                  created_at INTEGER NOT NULL,
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
      "video_providers",
      null,
      ContentValues().apply {
        put("id", "custom-provider")
        put("provider_type", "CUSTOM")
        put("name", "Custom Provider")
        put("enabled", 1)
        put("function_code", "return null")
        put("created_at", 100L)
        put("updated_at", 200L)
      },
    )
    legacy.close()

    val db = openDatabase().writableDatabase

    assertEquals(34, db.version)
    assertEquals(1, countRows(db, "video_providers", "id=?", arrayOf("custom-provider")))
    assertEquals(
      "return null",
      singleString(db, "SELECT function_code FROM video_providers WHERE id = ?", arrayOf("custom-provider")),
    )
    assertTrue("podcast_sources" in tableNames(db))
    assertTrue("podcast_programs" in tableNames(db))
    assertTrue("podcast_episodes" in tableNames(db))
    assertTrue("podcast_episode_articles" in tableNames(db))
    assertTrue("podcast_consumed_articles" in tableNames(db))
  }

  @Test
  fun `version 33 Podcast feed selection migrates to owned sources`() {
    val previousSchema = DatabaseSchema(
      version = 33,
      contributions = listOf(
        DatabaseSchemaContribution(
          owner = "legacy-v33",
          createSchema = { db ->
            db.execSQL("CREATE TABLE feeds(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,feed_url TEXT NOT NULL)")
            db.execSQL("CREATE TABLE articles(id TEXT PRIMARY KEY NOT NULL,feed_id TEXT,identity_key TEXT NOT NULL)")
            db.execSQL(
              "CREATE TABLE podcast_programs(" +
                "id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,feed_ids TEXT NOT NULL,provider TEXT NOT NULL," +
                "schedule_enabled INTEGER NOT NULL DEFAULT 0,schedule_hour INTEGER NOT NULL DEFAULT 7," +
                "schedule_minute INTEGER NOT NULL DEFAULT 0,max_articles INTEGER NOT NULL DEFAULT 12)",
            )
            db.execSQL(
              "CREATE TABLE podcast_episodes(" +
                "id TEXT PRIMARY KEY NOT NULL,program_id TEXT NOT NULL,title TEXT NOT NULL,created_at INTEGER NOT NULL," +
                "status TEXT NOT NULL,script TEXT,error_message TEXT)",
            )
            db.execSQL(
              "CREATE TABLE podcast_episode_articles(" +
                "episode_id TEXT NOT NULL,position INTEGER NOT NULL,article_id TEXT NOT NULL,feed_id TEXT NOT NULL," +
                "title TEXT NOT NULL,source_title TEXT,published_at INTEGER,feed_content TEXT NOT NULL," +
                "PRIMARY KEY(episode_id,position))",
            )
            db.execSQL(
              "CREATE TABLE podcast_consumed_articles(" +
                "program_id TEXT NOT NULL,article_id TEXT NOT NULL,episode_id TEXT NOT NULL,consumed_at INTEGER NOT NULL," +
                "PRIMARY KEY(program_id,article_id))",
            )
          },
        ),
      ),
    )
    val legacy = YomitoriDatabase.create(context, previousSchema)
    val db = legacy.writableDatabase
    db.insertOrThrow(
      "feeds",
      null,
      ContentValues().apply {
        put("id", "feed-1")
        put("title", "ニュース")
        put("feed_url", "https://example.invalid/feed.xml")
      },
    )
    db.insertOrThrow(
      "articles",
      null,
      ContentValues().apply {
        put("id", "article-1")
        put("feed_id", "feed-1")
        put("identity_key", "entry-1")
      },
    )
    db.insertOrThrow(
      "podcast_programs",
      null,
      ContentValues().apply {
        put("id", "program-1")
        put("name", "朝のニュース")
        put("feed_ids", "feed-1")
        put("provider", "LOCAL")
        put("schedule_enabled", 0)
        put("schedule_hour", 7)
        put("schedule_minute", 0)
        put("max_articles", 12)
      },
    )
    db.insertOrThrow(
      "podcast_episodes",
      null,
      ContentValues().apply {
        put("id", "episode-1")
        put("program_id", "program-1")
        put("title", "朝のニュース")
        put("created_at", 100L)
        put("status", "READY")
      },
    )
    db.insertOrThrow(
      "podcast_episode_articles",
      null,
      ContentValues().apply {
        put("episode_id", "episode-1")
        put("position", 0)
        put("article_id", "article-1")
        put("feed_id", "feed-1")
        put("title", "記事")
        put("feed_content", "本文")
      },
    )
    db.insertOrThrow(
      "podcast_consumed_articles",
      null,
      ContentValues().apply {
        put("program_id", "program-1")
        put("article_id", "article-1")
        put("episode_id", "episode-1")
        put("consumed_at", 100L)
      },
    )
    legacy.close()

    val migrated = openDatabase().writableDatabase

    assertEquals(34, migrated.version)
    assertEquals(
      "ニュース",
      singleString(migrated, "SELECT name FROM podcast_sources WHERE id=?", arrayOf("feed-1")),
    )
    assertEquals(
      "https://example.invalid/feed.xml",
      singleString(migrated, "SELECT feed_url FROM podcast_sources WHERE id=?", arrayOf("feed-1")),
    )
    assertEquals(
      "feed-1",
      singleString(migrated, "SELECT source_ids FROM podcast_programs WHERE id=?", arrayOf("program-1")),
    )
    assertFalse("feed_ids" in columnNames(migrated, "podcast_programs"))
    assertEquals(
      "feed-1:entry-1",
      singleString(migrated, "SELECT article_id FROM podcast_episode_articles WHERE episode_id=?", arrayOf("episode-1")),
    )
    assertEquals(
      "feed-1:entry-1",
      singleString(migrated, "SELECT article_id FROM podcast_consumed_articles WHERE program_id=?", arrayOf("program-1")),
    )
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
