package dev.terashima.yomitorirss

import android.content.Context
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

  private fun openDatabase(): YomitoriDatabase = YomitoriDatabase.create(context, appDatabaseSchema).also {
    database = it
  }
}
