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
  fun `表示上限を超える待機タスクも実行候補と集計に含める`() {
    repeat(206) { index ->
      val articleId = "article-$index"
      insertArticle(articleId)
      database.enqueueSummaryTask(articleId, forceRefresh = false)
    }

    assertEquals(206, database.listSummaryContentFetchCandidates().size)

    val completed = checkNotNull(database.claimNextSummaryTask())
    database.completeRunningSummaryTask(completed.articleId)

    val tasks = database.listSummaryTasks()
    val counts = database.countSummaryQueueTasks()

    assertEquals(200, tasks.size)
    assertEquals(200, tasks.count { it.state == SUMMARY_QUEUED })
    assertEquals(205, counts.queued)
    assertEquals(0, counts.running)
    assertEquals(0, counts.stopped)
  }

  @Test
  fun `指定した失敗タスクだけを待機へ戻す`() {
    insertArticle("retry")
    insertArticle("leave")
    database.enqueueSummaryTask("retry", forceRefresh = false)
    database.enqueueSummaryTask("leave", forceRefresh = false)
    database.markSummaryTaskFailed("retry", "generation failed")
    database.markSummaryTaskFailed("leave", "generation failed")

    val retried = database.requeueFailedSummaryTasks(setOf("retry"))

    assertEquals(1, retried)
    assertEquals(SUMMARY_QUEUED, database.findSummaryTask("retry")?.state)
    assertEquals(SUMMARY_FAILED, database.findSummaryTask("leave")?.state)
  }

  @Test
  fun `一時的なクラウド失敗は失敗扱いにせず待機へ戻す`() {
    insertArticle("cloud-retry")
    database.enqueueSummaryTask("cloud-retry", forceRefresh = false)
    checkNotNull(database.claimNextSummaryTask())
    database.updateRunningSummaryTaskProgress("cloud-retry", SUMMARY_PROGRESS_CLOUD_GENERATING_SUMMARY)

    assertTrue(
      database.requeueRunningSummaryTaskForRetry(
        "cloud-retry",
        "ChatGPT / Codex が一時的に利用できません。自動的に再試行します",
      ),
    )

    val retried = checkNotNull(database.findSummaryTask("cloud-retry"))
    assertEquals(SUMMARY_QUEUED, retried.state)
    assertNull(retried.startedAt)
    assertNull(retried.finishedAt)
    assertNull(retried.progressStage)
    assertEquals("ChatGPT / Codex が一時的に利用できません。自動的に再試行します", retried.error)
  }

  @Test
  fun `記事本文を準備してから推論タスクをclaimする`() {
    insertArticle("article")
    database.enqueueSummaryTask("article", forceRefresh = false)

    assertEquals("article", database.listSummaryContentFetchCandidates().single().articleId)
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

    assertTrue(database.listSummaryContentFetchCandidates().isEmpty())
    assertEquals("cached", database.claimNextInferenceReadySummaryTask()?.articleId)
  }

  @Test
  fun `強制再要約では既存要約があっても本文を取得し直す`() {
    insertArticle("refresh")
    database.saveSummary("refresh", "old summary", "old-cache-key")
    database.enqueueSummaryTask("refresh", forceRefresh = true)

    assertEquals("refresh", database.listSummaryContentFetchCandidates().single().articleId)
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
    assertTrue(database.listSummaryContentFetchCandidates().isEmpty())
    assertEquals("retry", database.claimNextInferenceReadySummaryTask()?.articleId)
  }

  @Test
  fun `キャンセルしたタスクは準備済み本文を破棄する`() {
    insertArticle("cancelled")
    database.enqueueSummaryTask("cancelled", forceRefresh = false)
    database.savePreparedSummaryArticleContentIfQueued("cancelled", "prepared body")

    assertEquals(SUMMARY_QUEUED, database.cancelSummaryTask("cancelled"))
    assertNull(database.findPreparedSummaryArticleContent("cancelled"))
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
        put("source_title", "test")
        put("source_feed_url", "")
      },
    )
  }

  private companion object {
    val testFeedSchema = DatabaseSchemaContribution(
      owner = "test-feed",
      createSchema = { db -> db.execSQL("CREATE TABLE IF NOT EXISTS feeds(id TEXT PRIMARY KEY NOT NULL)") },
    )
    val testArticleSchema = DatabaseSchemaContribution(
      owner = articleDatabaseSchema.owner,
      createSchema = articleDatabaseSchema.createSchema,
    )
  }
}
