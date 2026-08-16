package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseSchema
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
        contributions = listOf(articleDatabaseSchema, summaryDatabaseSchema),
      ),
    )
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `200件を超える未完了タスクもすべて取得する`() {
    repeat(206) { index ->
      val articleId = "article-$index"
      insertArticle(articleId)
      database.enqueueSummaryTask(articleId, forceRefresh = false)
    }
    val completed = checkNotNull(database.claimNextSummaryTask())
    database.completeRunningSummaryTask(completed.articleId)

    val tasks = database.listSummaryTaskItems()

    assertEquals(205, tasks.size)
    assertEquals(205, tasks.count { it.task.state == SUMMARY_QUEUED })
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
        putNull("saved_at")
        put("source_title", "test")
        put("source_feed_url", "")
      },
    )
  }
}
