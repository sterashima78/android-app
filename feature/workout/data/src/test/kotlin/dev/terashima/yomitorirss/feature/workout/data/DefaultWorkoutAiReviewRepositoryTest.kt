package dev.terashima.yomitorirss.feature.workout.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.feature.workout.WorkoutAiProvider
import dev.terashima.yomitorirss.feature.workout.WorkoutAiReview
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultWorkoutAiReviewRepositoryTest {
  private lateinit var context: Context
  private lateinit var repository: DefaultWorkoutAiReviewRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("workout_ai", Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
    repository = DefaultWorkoutAiReviewRepository(context)
  }

  @Test
  fun `同じ日付のレビューは最新内容で置き換える`() = runTest {
    repository.save(review("2026-09-29", "2026-09-29T08:00:00+09:00", "最初"))
    repository.save(review("2026-09-29", "2026-09-29T09:00:00+09:00", "更新後"))

    val reviews = repository.loadAll()

    assertEquals(1, reviews.size)
    assertEquals("更新後", reviews.single().content)
    assertEquals("2026-09-29T09:00:00+09:00", reviews.single().generatedAt)
  }

  @Test
  fun `レビューは新しい日付順に読み込める`() = runTest {
    repository.save(review("2026-09-27", "2026-09-27T08:00:00+09:00", "古い"))
    repository.save(review("2026-09-30", "2026-09-30T08:00:00+09:00", "新しい"))
    repository.save(review("2026-09-29", "2026-09-29T08:00:00+09:00", "中間"))

    assertEquals(
      listOf("2026-09-30", "2026-09-29", "2026-09-27"),
      repository.loadAll().map { it.date },
    )
  }

  private fun review(date: String, generatedAt: String, content: String) = WorkoutAiReview(
    date = date,
    generatedAt = generatedAt,
    provider = WorkoutAiProvider.LOCAL,
    content = content,
  )
}
