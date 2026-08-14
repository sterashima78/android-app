package dev.terashima.yomitorirss

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import org.junit.After
import org.junit.Assert.assertEquals
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

    assertEquals(12, db.version)
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
        "mail_accounts",
        "mail_labels",
        "mail_threads",
        "mail_messages",
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
  fun `version 9 database upgrades through feature migrations`() {
    context.openOrCreateDatabase(YomitoriDatabase.DB_NAME, Context.MODE_PRIVATE, null).use { legacy ->
      legacy.execSQL(
        "CREATE TABLE feeds(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,feed_url TEXT NOT NULL UNIQUE,site_url TEXT,etag TEXT,last_modified TEXT,last_fetched_at TEXT,last_error TEXT,created_at TEXT NOT NULL)",
      )
      legacy.version = 9
    }

    val db = openDatabase().writableDatabase

    assertEquals(12, db.version)
    assertTrue(columnNames(db, "feeds").contains("folder_id"))
    assertTrue(columnNames(db, "summary_tasks").contains("progress_stage"))
    assertTrue(columnNames(db, "summary_tasks").contains("progress_current"))
    assertTrue(columnNames(db, "summary_tasks").contains("progress_total"))
  }

  private fun openDatabase(): YomitoriDatabase = YomitoriDatabase.create(context, appDatabaseSchema).also {
    database = it
  }

  private fun columnNames(
    db: android.database.sqlite.SQLiteDatabase,
    table: String,
  ): Set<String> = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    buildSet {
      while (cursor.moveToNext()) add(cursor.getString(nameIndex))
    }
  }
}
