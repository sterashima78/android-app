package dev.terashima.yomitorirss.feature.workout.data

import android.content.Context
import dev.terashima.yomitorirss.feature.workout.WorkoutAiProvider
import dev.terashima.yomitorirss.feature.workout.WorkoutAiSettings
import dev.terashima.yomitorirss.feature.workout.WorkoutAiSettingsRepository

class DefaultWorkoutAiSettingsRepository(context: Context) : WorkoutAiSettingsRepository {
  private val preferences = context.getSharedPreferences("workout_ai", Context.MODE_PRIVATE)

  override suspend fun loadSettings(): WorkoutAiSettings = WorkoutAiSettings(
    provider = runCatching {
      WorkoutAiProvider.valueOf(preferences.getString(KEY_PROVIDER, null).orEmpty())
    }.getOrDefault(WorkoutAiProvider.LOCAL),
    workoutPolicy = preferences.getString(KEY_POLICY, "").orEmpty(),
    menuCandidates = preferences.getString(KEY_MENU_CANDIDATES, "").orEmpty(),
  )

  override suspend fun saveSettings(settings: WorkoutAiSettings) {
    preferences.edit()
      .putString(KEY_PROVIDER, settings.provider.name)
      .putString(KEY_POLICY, settings.workoutPolicy)
      .putString(KEY_MENU_CANDIDATES, settings.menuCandidates)
      .apply()
  }

  override suspend fun loadMemo(date: String): String =
    preferences.getString(memoKey(date), "").orEmpty()

  override suspend fun saveMemo(date: String, memo: String) {
    preferences.edit().putString(memoKey(date), memo).apply()
  }

  override suspend fun loadMemos(dates: Set<String>): Map<String, String> = dates.associateWith { date ->
    preferences.getString(memoKey(date), "").orEmpty()
  }.filterValues(String::isNotBlank)

  private fun memoKey(date: String): String = "$MEMO_PREFIX$date"

  private companion object {
    const val KEY_PROVIDER = "provider"
    const val KEY_POLICY = "workout_policy"
    const val KEY_MENU_CANDIDATES = "menu_candidates"
    const val MEMO_PREFIX = "memo:"
  }
}
