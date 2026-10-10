package dev.terashima.yomitorirss.feature.workout.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.feature.workout.WorkoutDay
import dev.terashima.yomitorirss.feature.workout.WorkoutExerciseType
import dev.terashima.yomitorirss.feature.workout.WorkoutFormQuality
import dev.terashima.yomitorirss.feature.workout.WorkoutLoad
import dev.terashima.yomitorirss.feature.workout.WorkoutLoadKind
import dev.terashima.yomitorirss.feature.workout.WorkoutSet
import dev.terashima.yomitorirss.feature.workout.WorkoutUnit
import dev.terashima.yomitorirss.feature.workout.WorkoutHistory
import dev.terashima.yomitorirss.feature.workout.newWorkoutSnapshot
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultWorkoutRepositoryTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
      .edit()
      .clear()
      .commit()
  }

  @Test
  fun `versionなしの旧stateはunsupportedとして保持する`() = runTest {
    val raw = JSONObject().apply {
      put("exercises", JSONArray())
    }.toString()
    preferences().edit().putString(STATE_KEY, raw).commit()

    try {
      DefaultWorkoutRepository(context).load()
      fail("UnsupportedWorkoutStateVersionException was expected")
    } catch (error: UnsupportedWorkoutStateVersionException) {
      assertEquals(0, error.version)
    }

    assertEquals(raw, preferences().getString(STATE_KEY, null))
  }

  @Test
  fun `version1の旧stateはunsupportedとして保持する`() = runTest {
    val raw = JSONObject().apply {
      put("version", 1)
      put("exercises", JSONArray())
    }.toString()
    preferences().edit().putString(STATE_KEY, raw).commit()

    try {
      DefaultWorkoutRepository(context).load()
      fail("UnsupportedWorkoutStateVersionException was expected")
    } catch (error: UnsupportedWorkoutStateVersionException) {
      assertEquals(1, error.version)
    }

    assertEquals(raw, preferences().getString(STATE_KEY, null))
  }

  @Test
  fun `互換baseline外のversion2と3は拒否して元のpayloadを保持する`() = runTest {
    for (version in listOf(2, 3)) {
      val raw = JSONObject().put("version", version).put("exercises", JSONArray()).toString()
      preferences().edit().putString(STATE_KEY, raw).commit()
      try {
        DefaultWorkoutRepository(context).load()
        fail("UnsupportedWorkoutStateVersionException was expected for version $version")
      } catch (error: UnsupportedWorkoutStateVersionException) {
        assertEquals(version, error.version)
      }
      assertEquals(raw, preferences().getString(STATE_KEY, null))
    }
  }

  @Test
  fun `version4はRPEとフォームと負荷と休憩を往復できる`() = runTest {
    val repository = DefaultWorkoutRepository(context)
    val set = WorkoutSet(
      id = "set-1",
      exerciseId = "pull",
      exerciseName = "斜め懸垂",
      unit = WorkoutUnit.REPS,
      type = WorkoutExerciseType.REPS,
      amount = 10,
      recordedAt = "2026-09-22T08:00:00+09:00",
      rpe = 7,
      formQuality = WorkoutFormQuality.UNSTABLE,
      load = WorkoutLoad(WorkoutLoadKind.BODY_ANGLE, "中程度の傾斜"),
      restSeconds = 90,
    )
    val original = newWorkoutSnapshot("2026-09-22").copy(
      today = WorkoutDay("2026-09-22", sets = listOf(set)),
      history = listOf(
        WorkoutHistory(
          id = "past-set",
          date = "2026-09-21",
          startedAt = null,
          finishedAt = "2026-09-21T08:00:00+09:00",
          sets = listOf(set.copy(load = WorkoutLoad(WorkoutLoadKind.ADDED_WEIGHT, "5.5"))),
        ),
      ),
    )

    repository.save(original)
    val loaded = repository.load()

    assertEquals(4, loaded.version)
    assertEquals(original.today.sets, loaded.today.sets)
    assertEquals(original.history.single().sets, loaded.history.single().sets)
  }

  @Test
  fun `破損したJSONを空状態へ置換せず元のpayloadを保護する`() = runTest {
    val raw = "{invalid-json"
    preferences().edit().putString(STATE_KEY, raw).commit()

    try {
      DefaultWorkoutRepository(context).load()
      fail("CorruptWorkoutStateException was expected")
    } catch (error: CorruptWorkoutStateException) {
      assertEquals(true, error.cause != null)
    }
    assertEquals(raw, preferences().getString(STATE_KEY, null))
  }

  @Test
  fun `version4で必須フィールドが欠けている場合は元のpayloadを保護する`() = runTest {
    val raw = JSONObject().put("version", 4).put("exercises", JSONArray()).toString()
    preferences().edit().putString(STATE_KEY, raw).commit()

    try {
      DefaultWorkoutRepository(context).load()
      fail("CorruptWorkoutStateException was expected")
    } catch (_: CorruptWorkoutStateException) {
      // Deliberately do not initialize a replacement snapshot.
    }
    assertEquals(raw, preferences().getString(STATE_KEY, null))
  }

  @Test
  fun `未知versionは現行形式として解釈せず保存済みpayloadも変更しない`() = runTest {
    val raw = JSONObject().apply {
      put("version", 99)
      put("exercises", JSONArray())
      put("menus", JSONArray())
    }.toString()
    preferences().edit().putString(STATE_KEY, raw).commit()

    try {
      DefaultWorkoutRepository(context).load()
      fail("UnsupportedWorkoutStateVersionException was expected")
    } catch (error: UnsupportedWorkoutStateVersionException) {
      assertEquals(99, error.version)
    }

    assertEquals(raw, preferences().getString(STATE_KEY, null))
  }

  private fun preferences() =
    context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  private companion object {
    const val PREFERENCES_NAME = "workout"
    const val STATE_KEY = "state_v1"
  }
}
