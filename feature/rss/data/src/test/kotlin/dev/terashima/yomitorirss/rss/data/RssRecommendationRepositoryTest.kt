package dev.terashima.yomitorirss.feature.rss.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.rss.RssRecommendationAssessment
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
}
