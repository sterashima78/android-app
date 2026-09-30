package dev.terashima.yomitorirss.feature.workout.data

import android.content.Context
import dev.terashima.yomitorirss.feature.workout.WorkoutAiProvider
import dev.terashima.yomitorirss.feature.workout.WorkoutAiReview
import dev.terashima.yomitorirss.feature.workout.WorkoutAiReviewRepository
import org.json.JSONObject

class DefaultWorkoutAiReviewRepository(context: Context) : WorkoutAiReviewRepository {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  override suspend fun save(review: WorkoutAiReview) {
    val reviews = readRoot()
    reviews.put(
      review.date,
      JSONObject()
        .put("date", review.date)
        .put("generatedAt", review.generatedAt)
        .put("provider", review.provider.name)
        .put("content", review.content),
    )
    preferences.edit().putString(KEY_REVIEWS, reviews.toString()).apply()
  }

  override suspend fun loadAll(): List<WorkoutAiReview> =
    readRoot()
      .keys()
      .asSequence()
      .mapNotNull { key -> readRoot().optJSONObject(key)?.toReview() }
      .sortedWith(compareByDescending<WorkoutAiReview> { it.date }.thenByDescending { it.generatedAt })
      .toList()

  override suspend fun loadByDates(dates: Set<String>): List<WorkoutAiReview> =
    loadAll().filter { it.date in dates }

  private fun readRoot(): JSONObject =
    preferences.getString(KEY_REVIEWS, null)
      ?.let { raw -> runCatching { JSONObject(raw) }.getOrNull() }
      ?: JSONObject()

  private fun JSONObject.toReview(): WorkoutAiReview? {
    val date = optString("date").takeIf(String::isNotBlank) ?: return null
    val generatedAt = optString("generatedAt").takeIf(String::isNotBlank) ?: return null
    val provider = optString("provider")
      .let { saved -> WorkoutAiProvider.entries.firstOrNull { it.name == saved } }
      ?: return null
    val content = optString("content").takeIf(String::isNotBlank) ?: return null
    return WorkoutAiReview(
      date = date,
      generatedAt = generatedAt,
      provider = provider,
      content = content,
    )
  }

  private companion object {
    const val PREFERENCES_NAME = "workout_ai"
    const val KEY_REVIEWS = "reviews"
  }
}
