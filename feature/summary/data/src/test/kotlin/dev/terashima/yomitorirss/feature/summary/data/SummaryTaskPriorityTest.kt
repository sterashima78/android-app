package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.article.data.articleDatabaseSchema
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SummaryTaskPriorityTest {
  private lateinit var context: Context
  private lateinit var database: YomitoriDatabase

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
    database = YomitoriDatabase.create(
      context,
      DatabaseSchema(
        version = 1,
        contributions = listOf(
          testFeedSchema,
          articleDatabaseSchema,
          testBookmarkFolderSchema,
          summaryDatabaseSchema,
        ),
      ),
    )
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `あとで読むの要約は先に追加された通常タスクより先にclaimする`() {
    insertArticle("normal")
    insertArticle("read-later")
    database.enqueueSummaryTask("normal", forceRefresh = false)
    database.enqueueSummaryTask("read-later", forceRefresh = false)
    database.writableDatabase.update(
      "summary_tasks",
      ContentValues().apply { put("queued_at", "2026-08-16T00:00:00Z") },
      "article_id=?",
      arrayOf("normal"),
    )
    database.writableDatabase.update(
      "summary_tasks",
      ContentValues().apply { put("queued_at", "2026-08-16T00:00:01Z") },
      "article_id=?",
      arrayOf("read-later"),
    )
    markReadLater("read-later")

    assertEquals(LocalAiBackgroundTaskPriority.HIGH, database.peekNextSummaryTaskPriority())
    val claimed = checkNotNull(database.claimNextSummaryTaskByPriority())
    assertEquals("read-later", claimed.articleId)

    database.completeRunningSummaryTask(claimed.articleId)
    assertEquals(LocalAiBackgroundTaskPriority.NORMAL, database.peekNextSummaryTaskPriority())
  }

  @Test
  fun `待機中のブックマークをあとで読むへ移すと優先度が上がる`() {
    insertArticle("article")
    database.enqueueSummaryTask("article", forceRefresh = false)

    assertEquals(LocalAiBackgroundTaskPriority.NORMAL, database.peekNextSummaryTaskPriority())

    markReadLater("article")

    assertEquals(LocalAiBackgroundTaskPriority.HIGH, database.peekNextSummaryTaskPriority())
    assertEquals(setOf("article"), database.readLaterSummaryTaskIds(listOf("article")))
  }

  private fun insertArticle(id: String) {
    database.writableDatabase.insertOrThrow(
      "articles",
      null,
      ContentValues().apply {
        put("id", id)
        putNull("feed_id")
        putNull("external_id")
        put("identity_key", "test:$id")
        put("url", "https://example.com/$id")
        put("title", id)
        put("published_at", "2026-08-16T00:00:00Z")
        put("fetched_at", "2026-08-16T00:00:00Z")
        putNull("read_at")
        put("saved_at", "2026-08-16T00:00:00Z")
        put("source_title", "test")
        put("source_feed_url", "")
      },
    )
  }

  private fun markReadLater(articleId: String) {
    database.writableDatabase.insertWithOnConflict(
      "bookmark_folders",
      null,
      ContentValues().apply {
        put("id", "read-later-folder")
        put("system_kind", "read_later")
      },
      android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE,
    )
    database.writableDatabase.insertOrThrow(
      "article_folders",
      null,
      ContentValues().apply {
        put("article_id", articleId)
        put("folder_id", "read-later-folder")
      },
    )
  }

  private companion object {
    val testFeedSchema = DatabaseSchemaContribution(
      owner = "test-feed",
      createSchema = { db ->
        db.execSQL("CREATE TABLE IF NOT EXISTS feeds(id TEXT PRIMARY KEY NOT NULL)")
      },
    )

    val testBookmarkFolderSchema = DatabaseSchemaContribution(
      owner = "test-bookmark-folders",
      createSchema = { db ->
        db.execSQL("CREATE TABLE IF NOT EXISTS bookmark_folders(id TEXT PRIMARY KEY NOT NULL,system_kind TEXT)")
        db.execSQL("CREATE TABLE IF NOT EXISTS article_folders(article_id TEXT PRIMARY KEY NOT NULL REFERENCES articles(id) ON DELETE CASCADE,folder_id TEXT NOT NULL REFERENCES bookmark_folders(id) ON DELETE CASCADE)")
      },
    )
  }
}
