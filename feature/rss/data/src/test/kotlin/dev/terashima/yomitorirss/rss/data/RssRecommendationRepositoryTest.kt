package dev.terashima.yomitorirss.feature.rss.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.rss.RssRecommendationAssessment
import dev.terashima.yomitorirss.feature.rss.RssRecommendationExecutionProvider
import dev.terashima.yomitorirss.feature.rss.RssRecommendationFeedbackKind
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTaskState
import dev.terashima.yomitorirss.feature.rss.RssRecommendationUnscoredReason
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RssRecommendationRepositoryTest {
  private lateinit var helper: SQLiteOpenHelper
  private lateinit var repository: DefaultRssRecommendationRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) = Unit
      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
    repository = DefaultRssRecommendationRepository(DatabaseConnection(helper))
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `手動条件の変更はrevisionを進めて永続化する`() {
    val initial = repository.loadPolicy()

    val updated = repository.saveManualCondition("広告記事は低くする")

    assertEquals(0L, initial.revision)
    assertEquals(1L, updated.revision)
    assertEquals("広告記事は低くする", repository.loadPolicy().manualCondition)
  }

  @Test
  fun `実行先は既定でLOCALで変更時にrevisionを進めて永続化する`() {
    assertEquals(RssRecommendationExecutionProvider.LOCAL, repository.loadPolicy().executionProvider)

    val updated = repository.setExecutionProvider(RssRecommendationExecutionProvider.CLOUD)

    assertEquals(1L, updated.revision)
    assertEquals(RssRecommendationExecutionProvider.CLOUD, repository.loadPolicy().executionProvider)
  }

  @Test
  fun `数値評価と未評価理由を別状態として保存する`() {
    repository.saveAssessments(
      mapOf(
        "a1" to RssRecommendationAssessment.Scored(
          score = 7,
          revision = 2L,
          assessedAt = 100L,
        ),
        "a2" to RssRecommendationAssessment.Unscored(
          reason = RssRecommendationUnscoredReason.INSUFFICIENT_INFORMATION,
          revision = 2L,
          assessedAt = 101L,
        ),
      ),
    )

    val loaded = repository.loadAssessments(listOf("a1", "a2"))

    assertEquals(
      RssRecommendationAssessment.Scored(7, revision = 2L, assessedAt = 100L),
      loaded["a1"],
    )
    assertEquals(
      RssRecommendationAssessment.Unscored(
        RssRecommendationUnscoredReason.INSUFFICIENT_INFORMATION,
        revision = 2L,
        assessedAt = 101L,
      ),
      loaded["a2"],
    )
  }

  @Test
  fun `除外参考は前回評価を保持し学習成功時だけ消費する`() {
    val previous = RssRecommendationAssessment.Scored(
      score = 10,
      revision = 1L,
      assessedAt = 200L,
    )
    val feedback = repository.addFeedback(
      articleId = "a1",
      title = "参考記事",
      previousAssessment = previous,
    )

    assertEquals(previous, repository.listPendingFeedback().single().previousAssessment)

    val updated = repository.applyLearnedConditionAndConsumeFeedback(
      feedbackIds = setOf(feedback.id),
      learnedCondition = "広告的な告知を低くする",
    )

    assertEquals(1L, updated.revision)
    assertEquals("広告的な告知を低くする", updated.learnedCondition)
    assertEquals(emptyList<Any>(), repository.listPendingFeedback())
  }

  @Test
  fun `優先参考を学習後も保持し次回の両条件の改善へ使える`() {
    val reference = repository.addFeedback(
      articleId = "a-priority",
      title = "技術のキャンペーン",
      previousAssessment = null,
      kind = RssRecommendationFeedbackKind.PRIORITY,
    )

    val updated = repository.applyLearnedConditionAndConsumeFeedback(
      feedbackIds = setOf(reference.id),
      learnedCondition = "販促だけを除外",
      learnedPriorityCondition = "技術的な内容を優先",
    )

    assertEquals("販促だけを除外", updated.learnedCondition)
    assertEquals("技術的な内容を優先", updated.learnedPriorityCondition)
    assertEquals(emptyList<Any>(), repository.listPendingFeedback())
    assertEquals(reference, repository.listLearningExamples().single())
    assertEquals(updated, repository.loadPolicy())
  }

  @Test
  fun `学習条件をリセットしたら同種の学習済み参考も削除する`() {
    val excluded = repository.addFeedback("exclude", "除外記事", null)
    repository.applyLearnedConditionAndConsumeFeedback(
      setOf(excluded.id), "宣伝を除外", "",
    )
    val priority = repository.addFeedback("priority", "優先記事", null, RssRecommendationFeedbackKind.PRIORITY)
    repository.applyLearnedConditionAndConsumeFeedback(
      setOf(priority.id), "宣伝を除外", "技術記事を優先",
    )
    assertEquals(2, repository.listLearningExamples().size)

    val afterPriorityReset = repository.resetLearnedPriorityCondition()
    assertEquals("", afterPriorityReset.learnedPriorityCondition)
    assertEquals("宣伝を除外", afterPriorityReset.learnedCondition)
    assertEquals(listOf(RssRecommendationFeedbackKind.EXCLUSION), repository.listLearningExamples().map { it.kind })

    val afterExclusionReset = repository.resetLearnedCondition()
    assertEquals("", afterExclusionReset.learnedCondition)
    assertEquals(emptyList<Any>(), repository.listLearningExamples())
  }

  @Test
  fun `旧データの除外参考と条件を維持し古いスコアを無効化する`() {
    val db = helper.writableDatabase
    db.execSQL(
      """
        CREATE TABLE rss_recommendation_policy(
          id INTEGER PRIMARY KEY NOT NULL CHECK(id=1),
          manual_condition TEXT NOT NULL DEFAULT '',
          learned_condition TEXT NOT NULL DEFAULT '',
          execution_provider TEXT NOT NULL DEFAULT 'LOCAL',
          revision INTEGER NOT NULL DEFAULT 0
        )
      """.trimIndent(),
    )
    db.execSQL(
      "INSERT INTO rss_recommendation_policy(id, manual_condition, learned_condition, revision) " +
        "VALUES (1, '手動の除外条件', '古い学習条件', 4)",
    )
    db.execSQL(
      """
        CREATE TABLE rss_recommendation_feedback(
          id TEXT PRIMARY KEY NOT NULL,
          article_id TEXT NOT NULL UNIQUE,
          title TEXT NOT NULL,
          previous_status TEXT,
          previous_score INTEGER,
          previous_unscored_reason TEXT,
          previous_revision INTEGER,
          previous_assessed_at INTEGER,
          created_at INTEGER NOT NULL
        )
      """.trimIndent(),
    )
    db.execSQL(
      "INSERT INTO rss_recommendation_feedback(id, article_id, title, created_at) " +
        "VALUES ('old-feedback', 'a1', '古い除外参考', 1)",
    )

    val policy = repository.loadPolicy()

    assertEquals(5L, policy.revision)
    assertEquals("手動の除外条件", policy.manualCondition)
    assertEquals("古い学習条件", policy.learnedCondition)
    assertEquals("", policy.learnedPriorityCondition)
    assertEquals(RssRecommendationFeedbackKind.EXCLUSION, repository.listPendingFeedback().single().kind)
  }

  @Test
  fun `推薦タスクは記事単位でFIFOにclaimして完了時に削除する`() {
    val first = article("a1", "記事1")
    val second = article("a2", "記事2")

    repository.enqueueTasks(listOf(first, second), revision = 3L)

    val claimed = repository.claimNextTask()
    assertEquals("a1", claimed?.articleId)
    assertEquals(RssRecommendationTaskState.RUNNING, claimed?.state)
    assertEquals(
      listOf(RssRecommendationTaskState.RUNNING, RssRecommendationTaskState.QUEUED),
      repository.listTasks().map { it.state },
    )

    repository.completeTask("a1", revision = 3L)

    assertEquals(listOf("a2"), repository.listTasks().map { it.articleId })
  }

  @Test
  fun `中断中の推薦タスクは待機状態へ戻す`() {
    repository.enqueueTasks(listOf(article("a1", "記事1")), revision = 2L)
    repository.claimNextTask()

    repository.requeueInterruptedTasks()

    val task = repository.listTasks().single()
    assertEquals(RssRecommendationTaskState.QUEUED, task.state)
    assertNull(task.startedAt)
  }

  @Test
  fun `revision変更時は古い推薦タスクを破棄する`() {
    repository.enqueueTasks(listOf(article("old", "旧記事")), revision = 1L)

    repository.enqueueTasks(listOf(article("new", "新記事")), revision = 2L)

    assertEquals(listOf("new"), repository.listTasks().map { it.articleId })
  }

  @Test
  fun `バックアップ復元後は推薦評価を残して一時キューだけ破棄する`() {
    repository.saveAssessments(
      mapOf(
        "a1" to RssRecommendationAssessment.Scored(
          score = 7,
          revision = 2L,
          assessedAt = 100L,
        ),
      ),
    )
    repository.enqueueTasks(listOf(article("a1", "記事1")), revision = 2L)

    RssRecommendationBackupRestoreInitializer(DatabaseConnection(helper)).initialize()

    assertEquals(emptyList<Any>(), repository.listTasks())
    assertEquals(7, (repository.loadAssessments(listOf("a1"))["a1"] as RssRecommendationAssessment.Scored).score)
  }

  @Test
  fun `feedbackを取り消すとpendingから削除する`() {
    val feedback = repository.addFeedback(
      articleId = "a1",
      title = "参考記事",
      previousAssessment = null,
    )

    repository.removeFeedback(feedback.id)

    assertEquals(emptyList<Any>(), repository.listPendingFeedback())
    assertNull(repository.loadAssessments(listOf("a1"))["a1"])
  }

  private fun article(id: String, title: String) = Article(
    id = id,
    feedId = "feed",
    externalId = id,
    identityKey = id,
    url = "https://example.invalid/$id",
    title = title,
    publishedAt = "2026-09-25T00:00:00Z",
    fetchedAt = "2026-09-25T00:00:00Z",
    readAt = null,
    sourceTitle = "test",
    sourceFeedUrl = "https://example.invalid/feed",
  )
}
