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

    assertEquals(27, db.version)
    assertTrue("content_type" in columnNames(db, "feed_folders"))
    assertTrue("content_type" in columnNames(db, "feeds"))
    assertTrue("custom_title" in columnNames(db, "feeds"))
    assertTrue("content_type" in columnNames(db, "articles"))
    assertFalse("saved_at" in columnNames(db, "articles"))
    assertEquals(
      setOf(
        "feed_folders",
        "feeds",
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
        "smb_library_servers",
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
      ),
      db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name<>'android_metadata'",
        null,
      ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } },
    )
  }

  @Test
  fun `version 24 bookmark state is migrated from legacy article column`() {
    val legacySchema = DatabaseSchema(
      version = 24,
      contributions = legacyContributions(
        version = 24,
        createOverrides = mapOf(
          "article" to ::createVersion24ArticleSchema,
          "bookmark" to ::createVersion24BookmarkSchema,
        ),
      ),
    )
    val legacyDatabase = YomitoriDatabase.create(context, legacySchema)
    val legacyDb = legacyDatabase.writableDatabase
    insertLegacyBookmarkedArticle(legacyDb, "migrated")
    assertFalse(tableExists(legacyDb, "bookmarks"))
    legacyDatabase.close()

    val upgraded = openDatabase().writableDatabase

    assertEquals(27, upgraded.version)
    assertTrue(tableExists(upgraded, "bookmarks"))
    assertEquals(1, countRows(upgraded, "bookmarks", "article_id=?", arrayOf("migrated")))
    assertEquals(
      "2026-08-14T00:00:00Z",
      upgraded.rawQuery("SELECT saved_at FROM bookmarks WHERE article_id=?", arrayOf("migrated")).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
      },
    )
  }

  @Test
  fun `version 23 database adds summary article content while upgrading`() {
    val legacySchema = DatabaseSchema(
      version = 23,
      contributions = legacyContributions(
        version = 23,
        createOverrides = mapOf("summary" to ::createVersion23SummarySchema),
      ),
    )
    val legacyDatabase = YomitoriDatabase.create(context, legacySchema)
    val legacyDb = legacyDatabase.writableDatabase
    insertBookmarkedArticle(legacyDb, "preserved-summary-task")
    legacyDb.insertOrThrow(
      "summary_tasks",
      null,
      ContentValues().apply {
        put("article_id", "preserved-summary-task")
        put("state", "queued")
        put("force_refresh", 0)
        put("queued_at", "2026-08-19T00:00:00Z")
      },
    )
    assertFalse(tableExists(legacyDb, "summary_article_content"))
    legacyDatabase.close()

    val upgraded = openDatabase().writableDatabase

    assertEquals(27, upgraded.version)
    assertTrue(tableExists(upgraded, "summary_article_content"))
    assertEquals(1, countRows(upgraded, "summary_tasks", "article_id=?", arrayOf("preserved-summary-task")))
  }

  @Test
  fun `version 22 database adds unified feature schemas while upgrading`() {
    val legacySchema = DatabaseSchema(
      version = 22,
      contributions = legacyContributions(version = 22).filterNot { contribution ->
        contribution.owner in setOf("task", "chat", "youtube")
      },
    )
    val legacyDatabase = YomitoriDatabase.create(context, legacySchema)
    val legacyDb = legacyDatabase.writableDatabase
    insertBookmarkedArticle(legacyDb, "preserved-article")
    assertFalse(tableExists(legacyDb, "tasks"))
    assertFalse(tableExists(legacyDb, "chat_sessions"))
    assertFalse(tableExists(legacyDb, "channels"))
    legacyDatabase.close()

    val upgraded = openDatabase().writableDatabase

    assertEquals(27, upgraded.version)
    assertTrue(tableExists(upgraded, "tasks"))
    assertTrue(tableExists(upgraded, "chat_sessions"))
    assertTrue(tableExists(upgraded, "chat_messages"))
    assertTrue(tableExists(upgraded, "channels"))
    assertTrue(tableExists(upgraded, "videos"))
    assertEquals(1, countRows(upgraded, "articles", "id=?", arrayOf("preserved-article")))
    assertEquals(1, countRows(upgraded, "bookmarks", "article_id=?", arrayOf("preserved-article")))
  }

  @Test
  fun `version 21 database adds custom feed title while upgrading`() {
    val legacySchema = DatabaseSchema(
      version = 21,
      contributions = legacyContributions(
        version = 21,
        createOverrides = mapOf("rss" to ::createVersion21RssSchema),
      ),
    )
    val legacyDatabase = YomitoriDatabase.create(context, legacySchema)
    val legacyDb = legacyDatabase.writableDatabase
    assertFalse("custom_title" in columnNames(legacyDb, "feeds"))
    legacyDatabase.close()

    val upgraded = openDatabase().writableDatabase

    assertEquals(27, upgraded.version)
    assertTrue("custom_title" in columnNames(upgraded, "feeds"))
  }

  @Test
  fun `legacy library review candidates are discarded while upgrading to current version`() {
    val legacySchema = DatabaseSchema(
      version = 17,
      contributions = legacyContributions(version = 17),
    )
    val legacyDatabase = YomitoriDatabase.create(context, legacySchema)
    val legacyDb = legacyDatabase.writableDatabase
    insertLibraryOrganizationBatch(legacyDb, "legacy-batch")
    insertLibraryOrganizationBatchItem(legacyDb, "legacy-batch", "pending", "PENDING_REVIEW")
    insertLibraryOrganizationBatchItem(legacyDb, "legacy-batch", "deferred", "DEFERRED")
    insertLibraryOrganizationBatchItem(legacyDb, "legacy-batch", "applied", "APPLIED")
    legacyDatabase.close()

    val upgraded = openDatabase().writableDatabase

    assertEquals(27, upgraded.version)
    assertTrue("content_type" in columnNames(upgraded, "feed_folders"))
    assertTrue("content_type" in columnNames(upgraded, "feeds"))
    assertTrue("custom_title" in columnNames(upgraded, "feeds"))
    assertTrue("content_type" in columnNames(upgraded, "articles"))
    assertTrue("snapshot_date" in columnNames(upgraded, "asset_entries"))
    assertTrue("category" in columnNames(upgraded, "asset_categories"))
    assertTrue("category" in columnNames(upgraded, "asset_category_definitions"))
    assertEquals(0, countRows(upgraded, "library_organization_batch_items", "status IN (?, ?)", arrayOf("PENDING_REVIEW", "DEFERRED")))
    assertEquals(1, countRows(upgraded, "library_organization_batch_items", "status = ?", arrayOf("APPLIED")))
    assertEquals(
      "COMPLETED",
      upgraded.rawQuery("SELECT status FROM library_organization_batches WHERE batch_id=?", arrayOf("legacy-batch")).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
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

  private fun openDatabase(): YomitoriDatabase = YomitoriDatabase.create(context, appDatabaseSchema).also { database = it }
}

private fun legacyContributions(
  version: Int,
  createOverrides: Map<String, (SQLiteDatabase) -> Unit> = emptyMap(),
): List<DatabaseSchemaContribution> = appDatabaseSchema.contributions.map { contribution ->
  DatabaseSchemaContribution(
    owner = contribution.owner,
    createSchema = createOverrides[contribution.owner] ?: contribution.createSchema,
    migrations = contribution.migrations.filter { migration -> migration.targetVersion <= version },
  )
}

private fun createVersion24ArticleSchema(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS articles(id TEXT PRIMARY KEY NOT NULL,feed_id TEXT REFERENCES feeds(id) ON DELETE SET NULL,external_id TEXT,identity_key TEXT NOT NULL,url TEXT NOT NULL,title TEXT NOT NULL,published_at TEXT NOT NULL,fetched_at TEXT NOT NULL,read_at TEXT,saved_at TEXT,source_title TEXT NOT NULL,source_feed_url TEXT NOT NULL,content_type TEXT)")
  db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS article_feed_identity ON articles(feed_id,identity_key) WHERE feed_id IS NOT NULL")
  db.execSQL("CREATE INDEX IF NOT EXISTS article_unread_date ON articles(read_at,published_at DESC)")
  db.execSQL("CREATE INDEX IF NOT EXISTS article_saved_date ON articles(saved_at,published_at DESC)")
  db.execSQL("CREATE INDEX IF NOT EXISTS article_read_date ON articles(read_at DESC)")
  db.execSQL("CREATE INDEX IF NOT EXISTS article_url ON articles(url)")
}

private fun createVersion24BookmarkSchema(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS tags(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,created_at TEXT NOT NULL)")
  db.execSQL("CREATE TABLE IF NOT EXISTS article_tags(article_id TEXT NOT NULL REFERENCES articles(id) ON DELETE CASCADE,tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,PRIMARY KEY(article_id,tag_id))")
  db.execSQL("CREATE TABLE IF NOT EXISTS bookmark_folders(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,system_kind TEXT,created_at TEXT NOT NULL)")
  db.execSQL("CREATE TABLE IF NOT EXISTS article_folders(article_id TEXT PRIMARY KEY NOT NULL REFERENCES articles(id) ON DELETE CASCADE,folder_id TEXT NOT NULL REFERENCES bookmark_folders(id) ON DELETE CASCADE)")
  db.execSQL("CREATE INDEX IF NOT EXISTS article_folder_folder_id ON article_folders(folder_id,article_id)")
}

private fun createVersion23SummarySchema(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS article_summaries(article_id TEXT PRIMARY KEY NOT NULL REFERENCES articles(id) ON DELETE CASCADE,summary TEXT NOT NULL,model_id TEXT NOT NULL,created_at TEXT NOT NULL)")
  db.execSQL("CREATE TABLE IF NOT EXISTS summary_tasks(article_id TEXT PRIMARY KEY NOT NULL REFERENCES articles(id) ON DELETE CASCADE,state TEXT NOT NULL,force_refresh INTEGER NOT NULL DEFAULT 0,queued_at TEXT NOT NULL,started_at TEXT,finished_at TEXT,error TEXT,progress_stage TEXT,progress_current INTEGER,progress_total INTEGER)")
  db.execSQL("CREATE INDEX IF NOT EXISTS summary_task_state ON summary_tasks(state,queued_at)")
}

private fun createVersion21RssSchema(db: SQLiteDatabase) {
  db.execSQL("CREATE TABLE IF NOT EXISTS feed_folders(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,created_at TEXT NOT NULL,content_type TEXT)")
  db.execSQL("CREATE TABLE IF NOT EXISTS feeds(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,feed_url TEXT NOT NULL UNIQUE,site_url TEXT,etag TEXT,last_modified TEXT,last_fetched_at TEXT,last_error TEXT,created_at TEXT NOT NULL,folder_id TEXT REFERENCES feed_folders(id) ON DELETE SET NULL,content_type TEXT)")
  db.execSQL("CREATE INDEX IF NOT EXISTS feeds_folder_id ON feeds(folder_id,title)")
}

private fun insertLibraryOrganizationBatch(db: SQLiteDatabase, batchId: String) {
  db.insertOrThrow(
    "library_organization_batches",
    null,
    ContentValues().apply {
      put("batch_id", batchId)
      put("status", "RUNNING")
      put("created_at", 1L)
      put("updated_at", 1L)
    },
  )
}

private fun insertLibraryOrganizationBatchItem(db: SQLiteDatabase, batchId: String, sourceId: String, status: String) {
  db.insertOrThrow(
    "library_organization_batch_items",
    null,
    ContentValues().apply {
      put("batch_id", batchId)
      put("source", "KINDLE")
      put("source_id", sourceId)
      put("status", status)
      put("tag_names_json", "[\"sample-tag\"]")
      put("collection_names_json", "[\"sample-collection\"]")
      put("reason", "sample reason")
      putNull("error")
      put("created_at", 1L)
      put("updated_at", 1L)
    },
  )
}

private fun insertBookmarkedArticle(db: SQLiteDatabase, id: String) {
  insertContentArticle(db, id)
  db.insertOrThrow(
    "bookmarks",
    null,
    ContentValues().apply {
      put("article_id", id)
      put("saved_at", "2026-08-14T00:00:00Z")
    },
  )
}

private fun insertLegacyBookmarkedArticle(db: SQLiteDatabase, id: String) {
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

private fun insertContentArticle(db: SQLiteDatabase, id: String) {
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

private fun tableExists(db: SQLiteDatabase, table: String): Boolean = db.rawQuery(
  "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
  arrayOf(table),
).use { it.moveToFirst() }

private fun columnNames(db: SQLiteDatabase, table: String): Set<String> =
  db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
    buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
  }

private fun countRows(db: SQLiteDatabase, table: String, selection: String, selectionArgs: Array<String>): Int =
  db.rawQuery("SELECT COUNT(*) FROM $table WHERE $selection", selectionArgs).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getInt(0)
  }
