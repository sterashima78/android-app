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
  fun `version2のstateはversion4へ読み込み履歴メニューを未設定として扱う`() = runTest {
    val raw = JSONObject().apply {
      put("version", 2)
      put("exercises", JSONArray())
      put("menus", JSONArray())
      put("today", JSONObject().put("date", "2026-09-22").put("sets", JSONArray()))
      put(
        "history",
        JSONArray().put(
          JSONObject()
            .put("id", "history-v2")
            .put("date", "2026-09-21")
            .put("startedAt", JSONObject.NULL)
            .put("finishedAt", "2026-09-21T08:30:00+09:00")
            .put("sets", JSONArray()),
        ),
      )
    }.toString()
    preferences().edit().putString(STATE_KEY, raw).commit()

    val loaded = DefaultWorkoutRepository(context).load()

    assertEquals(4, loaded.version)
    assertEquals(1, loaded.history.size)
    assertEquals(null, loaded.history.single().menu)
  }

  @Test
  fun `version3は完了履歴のメニューsnapshotを保存して読み込める`() = runTest {
    val repository = DefaultWorkoutRepository(context)
    val base = newWorkoutSnapshot("2026-09-22")
    val menu = base.menus.single()
    val original = base.copy(
      history = listOf(
        WorkoutHistory(
          id = "history-v3",
          date = "2026-09-21",
          startedAt = null,
          finishedAt = "2026-09-21T08:30:00+09:00",
          sets = emptyList(),
          menu = menu,
        ),
      ),
    )

    repository.save(original)
    val loaded = repository.load()

    assertEquals(4, loaded.version)
    assertEquals(menu, loaded.history.single().menu)
    assertEquals(original.exercises, loaded.exercises)
    assertEquals(original.menus, loaded.menus)
    assertEquals(original.today, loaded.today)
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
  fun `version3のセット詳細は未入力として読み込む`() = runTest {
    val raw = JSONObject().apply {
      put("version", 3)
      put("exercises", JSONArray())
      put("today", JSONObject().put("date", "2026-09-22").put(
        "sets",
        JSONArray().put(
          JSONObject()
            .put("id", "legacy")
            .put("exerciseId", "push-up")
            .put("exerciseName", "腕立て伏せ")
            .put("unit", "REPS")
            .put("type", "REPS")
            .put("amount", 10)
            .put("recordedAt", "2026-09-22T08:00:00+09:00"),
        ),
      ))
    }.toString()
    preferences().edit().putString(STATE_KEY, raw).commit()

    val loaded = DefaultWorkoutRepository(context).load()

    assertEquals(4, loaded.version)
    val set = loaded.today.sets.single()
    assertEquals(null, set.rpe)
    assertEquals(null, set.formQuality)
    assertEquals(null, set.load)
    assertEquals(null, set.restSeconds)
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
