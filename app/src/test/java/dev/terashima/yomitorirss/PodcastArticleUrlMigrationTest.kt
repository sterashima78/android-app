package dev.terashima.yomitorirss

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = YomitoriApplication::class)
class PodcastArticleUrlMigrationTest {
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
  fun `version 34 Podcast記事snapshotへURLとchapter列を追加して既存行を保持する`() {
    val previousSchema = DatabaseSchema(
      version = 34,
      contributions = listOf(
        DatabaseSchemaContribution(
          owner = "legacy-v34-podcast",
          createSchema = { db ->
            db.execSQL(
              "CREATE TABLE podcast_episode_articles(" +
                "episode_id TEXT NOT NULL," +
                "position INTEGER NOT NULL," +
                "article_id TEXT NOT NULL," +
                "feed_id TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "source_title TEXT," +
                "published_at INTEGER," +
                "feed_content TEXT NOT NULL," +
                "PRIMARY KEY(episode_id,position))",
            )
          },
        ),
      ),
    )
    val legacy = YomitoriDatabase.create(context, previousSchema)
    legacy.writableDatabase.insertOrThrow(
      "podcast_episode_articles",
      null,
      ContentValues().apply {
        put("episode_id", "episode-1")
        put("position", 0)
        put("article_id", "article-1")
        put("feed_id", "source-1")
        put("title", "記事")
        put("feed_content", "本文")
      },
    )
    legacy.close()

    val migrated = YomitoriDatabase.create(context, appDatabaseSchema).also { database = it }.writableDatabase

    assertEquals(36, migrated.version)
    assertTrue("article_url" in columnNames(migrated, "podcast_episode_articles"))
    assertTrue("chapter_status" in columnNames(migrated, "podcast_episode_articles"))
    assertTrue("chapter_script" in columnNames(migrated, "podcast_episode_articles"))
    assertTrue("chapter_error" in columnNames(migrated, "podcast_episode_articles"))
    migrated.rawQuery(
      "SELECT article_id,article_url,chapter_status FROM podcast_episode_articles WHERE episode_id=?",
      arrayOf("episode-1"),
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("article-1", cursor.getString(0))
      assertNull(if (cursor.isNull(1)) null else cursor.getString(1))
      assertEquals("PENDING", cursor.getString(2))
    }
  }

  @Test
  fun `version 35 READY episodeは既存原稿を保持してchapterを完了扱いにする`() {
    val previousSchema = DatabaseSchema(
      version = 35,
      contributions = listOf(
        DatabaseSchemaContribution(
          owner = "legacy-v35-podcast",
          createSchema = { db ->
            db.execSQL(
              "CREATE TABLE podcast_episodes(" +
                "id TEXT PRIMARY KEY NOT NULL," +
                "program_id TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "status TEXT NOT NULL," +
                "script TEXT," +
                "error_message TEXT)",
            )
            db.execSQL(
              "CREATE TABLE podcast_episode_articles(" +
                "episode_id TEXT NOT NULL," +
                "position INTEGER NOT NULL," +
                "article_id TEXT NOT NULL," +
                "feed_id TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "source_title TEXT," +
                "published_at INTEGER," +
                "article_url TEXT," +
                "feed_content TEXT NOT NULL," +
                "PRIMARY KEY(episode_id,position))",
            )
          },
        ),
      ),
    )
    val legacy = YomitoriDatabase.create(context, previousSchema)
    legacy.writableDatabase.insertOrThrow(
      "podcast_episodes",
      null,
      ContentValues().apply {
        put("id", "episode-ready")
        put("program_id", "program-1")
        put("title", "保存済みエピソード")
        put("created_at", 1_000L)
        put("status", "READY")
        put("script", "既存原稿")
      },
    )
    legacy.writableDatabase.insertOrThrow(
      "podcast_episode_articles",
      null,
      ContentValues().apply {
        put("episode_id", "episode-ready")
        put("position", 0)
        put("article_id", "article-ready")
        put("feed_id", "source-1")
        put("title", "記事")
        put("feed_content", "本文")
      },
    )
    legacy.close()

    val migrated = YomitoriDatabase.create(context, appDatabaseSchema).also { database = it }.writableDatabase

    assertEquals(36, migrated.version)
    migrated.rawQuery(
      "SELECT script,regeneration_status FROM podcast_episodes WHERE id=?",
      arrayOf("episode-ready"),
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("既存原稿", cursor.getString(0))
      assertTrue(cursor.isNull(1))
    }
    migrated.rawQuery(
      "SELECT chapter_status,chapter_script,chapter_error FROM podcast_episode_articles WHERE episode_id=?",
      arrayOf("episode-ready"),
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("READY", cursor.getString(0))
      assertTrue(cursor.isNull(1))
      assertTrue(cursor.isNull(2))
    }
  }
}

private fun columnNames(db: android.database.sqlite.SQLiteDatabase, table: String): Set<String> =
  db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    buildSet {
      while (cursor.moveToNext()) add(cursor.getString(nameIndex))
    }
  }
