package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.article.data.articleDatabaseSchema
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
@Config(sdk = [35])
class SummaryPersistenceTest {
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
        contributions = listOf(testFeedSchema, testArticleSchema, summaryDatabaseSchema),
      ),
    )
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `表示上限を超える待機タスクも集計する`() {
    repeat(206) { index ->
      val articleId = "article-$index"
      insertArticle(articleId)
      database.enqueueSummaryTask(articleId, forceRefresh = false)
    }
    val completed = checkNotNull(database.claimNextSummaryTask())
    database.completeRunningSummaryTask(completed.articleId)

    val tasks = database.listSummaryTaskItems()
    val counts = database.countSummaryQueueTasks()

    assertEquals(200, tasks.size)
    assertEquals(200, tasks.count { it.task.state == SUMMARY_QUEUED })
    assertEquals(205, counts.queued)
    assertEquals(0, counts.running)
    assertEquals(0, counts.stopped)
  }

  @Test
  fun `失敗した保存済みブックマークだけを一括で待機に戻す`() {
    insertArticle("saved-failed", bookmarked = true)
    database.enqueueSummaryTask("saved-failed", forceRefresh = false)
    database.markSummaryTaskFailed("saved-failed", "generation failed")

    insertArticle("unsaved-failed")
    database.enqueueSummaryTask("unsaved-failed", forceRefresh = false)
    database.markSummaryTaskFailed("unsaved-failed", "generation failed")

    insertArticle("saved-stopped", bookmarked = true)
    database.enqueueSummaryTask("saved-stopped", forceRefresh = false)
    database.stopSummaryTask("saved-stopped")

    val retried = database.retryFailedBookmarkSummaryTasks()

    assertEquals(1, retried)
    assertEquals(SUMMARY_QUEUED, database.findSummaryTask("saved-failed")?.state)
    assertEquals(null, database.findSummaryTask("saved-failed")?.error)
    assertEquals(SUMMARY_FAILED, database.findSummaryTask("unsaved-failed")?.state)
    assertEquals(SUMMARY_STOPPED, database.findSummaryTask("saved-stopped")?.state)
  }

  @Test
  fun `記事本文を準備してから推論タスクをclaimする`() {
    insertArticle("article")
    database.enqueueSummaryTask("article", forceRefresh = false)

    assertEquals("article", database.nextSummaryArticleForContentFetch()?.id)
    assertNull(database.claimNextInferenceReadySummaryTask())

    assertTrue(database.savePreparedSummaryArticleContentIfQueued("article", "prepared body"))
    assertEquals("prepared body", database.findPreparedSummaryArticleContent("article")?.content)

    val claimed = checkNotNull(database.claimNextInferenceReadySummaryTask())
    assertEquals("article", claimed.articleId)
    assertEquals(SUMMARY_RUNNING, claimed.state)

    database.completeRunningSummaryTask("article")
    assertNull(database.findPreparedSummaryArticleContent("article"))
  }

  @Test
  fun `既存要約があれば本文取得なしで推論ステージへ進める`() {
    insertArticle("cached")
    database.saveSummary("cached", "cached summary", "model-cache-key")
    database.enqueueSummaryTask("cached", forceRefresh = false)

    assertNull(database.nextSummaryArticleForContentFetch())
    assertEquals("cached", database.claimNextInferenceReadySummaryTask()?.articleId)
  }

  @Test
  fun `強制再要約では既存要約があっても本文を取得し直す`() {
    insertArticle("refresh")
    database.saveSummary("refresh", "old summary", "old-cache-key")
    database.enqueueSummaryTask("refresh", forceRefresh = true)

    assertEquals("refresh", database.nextSummaryArticleForContentFetch()?.id)
    assertNull(database.claimNextInferenceReadySummaryTask())
  }

  @Test
  fun `推論失敗後の再開では準備済み本文を再利用する`() {
    insertArticle("retry")
    database.enqueueSummaryTask("retry", forceRefresh = false)
    database.savePreparedSummaryArticleContentIfQueued("retry", "prepared body")
    checkNotNull(database.claimNextInferenceReadySummaryTask())
    database.failRunningSummaryTask("retry", "generation failed")

    assertEquals("prepared body", database.findPreparedSummaryArticleContent("retry")?.content)
    assertTrue(database.resumeSummaryTask("retry"))
    assertNull(database.nextSummaryArticleForContentFetch())
    assertEquals("retry", database.claimNextInferenceReadySummaryTask()?.articleId)
  }

  private fun insertArticle(id: String, bookmarked: Boolean = false) {
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
        if (bookmarked) {
          put("saved_at", "2026-08-16T00:00:00Z")
        } else {
          putNull("saved_at")
        }
        put("source_title", "test")
        put("source_feed_url", "")
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

    val testArticleSchema = DatabaseSchemaContribution(
      owner = articleDatabaseSchema.owner,
      createSchema = articleDatabaseSchema.createSchema,
    )
  }
}
