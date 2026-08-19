package dev.terashima.yomitorirss.feature.summary.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SummaryContentRetentionProtectionQueryTest {
  private lateinit var helper: SQLiteOpenHelper

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE article_summaries(article_id TEXT PRIMARY KEY NOT NULL)")
        db.execSQL("CREATE TABLE summary_tasks(article_id TEXT PRIMARY KEY NOT NULL,state TEXT NOT NULL)")
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `保存済み要約と実行中タスクだけを削除保護対象として返す`() {
    insertSummary("summary")
    insertTask("queued", "queued")
    insertTask("running", "running")
    insertTask("failed", "failed")
    val query = SummaryContentRetentionProtectionQuery(DatabaseConnection(helper))

    val actual = query.protectedContentIds(setOf("summary", "queued", "running", "failed", "missing"))

    assertEquals(setOf("summary", "queued", "running"), actual)
  }

  @Test
  fun `大量のContent候補もSQLite変数上限を超えずに処理する`() {
    val candidates = (1..600).mapTo(linkedSetOf()) { "article-$it" }
    insertSummary("article-600")
    val query = SummaryContentRetentionProtectionQuery(DatabaseConnection(helper))

    assertEquals(setOf("article-600"), query.protectedContentIds(candidates))
  }

  private fun insertSummary(articleId: String) {
    helper.writableDatabase.insertOrThrow(
      "article_summaries",
      null,
      ContentValues().apply { put("article_id", articleId) },
    )
  }

  private fun insertTask(articleId: String, state: String) {
    helper.writableDatabase.insertOrThrow(
      "summary_tasks",
      null,
      ContentValues().apply {
        put("article_id", articleId)
        put("state", state)
      },
    )
  }
}
