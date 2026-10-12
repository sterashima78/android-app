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
  fun `互換baseline外のversion2は拒否して元のpayloadを保持する`() = runTest {
    val raw = JSONObject().put("version", 2).put("exercises", JSONArray()).toString()
    preferences().edit().putString(STATE_KEY, raw).commit()

    try {
      DefaultWorkoutRepository(context).load()
      fail("UnsupportedWorkoutStateVersionException was expected")
    } catch (error: UnsupportedWorkoutStateVersionException) {
      assertEquals(2, error.version)
    }

    assertEquals(raw, preferences().getString(STATE_KEY, null))
  }

  @Test
  fun `version3の記録と履歴メニューをversion4へ安全に読み替える`() = runTest {
    val menu = JSONObject()
      .put("id", "menu-1")
      .put("name", "予定メニュー")
      .put("source", "PRESET")
      .put(
        "items",
        JSONArray().put(
          JSONObject().put("exerciseId", "push-up").put("targetSets", 1)
            .put("targets", JSONArray().put(12)),
        ),
      )
    val set = JSONObject()
      .put("id", "set-1")
      .put("exerciseId", "push-up")
      .put("exerciseName", "腕立て伏せ")
      .put("unit", "REPS")
      .put("type", "REPS")
      .put("amount", 12)
      .put("recordedAt", "2026-10-08T08:15:00+09:00")
    val raw = JSONObject()
      .put("version", 3)
      .put(
        "exercises",
        JSONArray().put(
          JSONObject().put("id", "push-up").put("name", "腕立て伏せ")
            .put("targetSets", 3).put("unit", "REPS").put("type", "REPS"),
        ),
      )
      .put("menus", JSONArray().put(menu))
      .put("today", JSONObject().put("date", "2026-10-09").put("sets", JSONArray().put(set)))
      .put(
        "history",
        JSONArray().put(
          JSONObject().put("id", "history-1").put("date", "2026-10-08")
            .put("startedAt", JSONObject.NULL)
            .put("finishedAt", "2026-10-08T08:30:00+09:00")
            .put("sets", JSONArray().put(set))
            .put("menu", menu),
        ),
      )
      .put("lastAmounts", JSONObject().put("push-up", 12))
      .put("lastStepCounts", JSONObject().put("push-up", 3))
      .toString()
    preferences().edit().putString(STATE_KEY, raw).commit()

    val repository = DefaultWorkoutRepository(context)
    val loaded = repository.load()

    assertEquals(4, loaded.version)
    assertEquals("push-up", loaded.exercises.single().id)
    assertEquals(listOf(12), loaded.menus.single().items.single().targets)
    assertEquals(12, loaded.today.sets.single().amount)
    assertEquals("2026-10-08", loaded.history.single().date)
    assertEquals(12, loaded.history.single().sets.single().amount)
    assertEquals(loaded.menus.single(), loaded.history.single().menu)
    assertEquals(12, loaded.lastAmounts["push-up"])
    assertEquals(3, loaded.lastStepCounts["push-up"])
    assertEquals(null, loaded.today.sets.single().rpe)
    assertEquals(null, loaded.today.sets.single().formQuality)
    assertEquals(null, loaded.today.sets.single().load)
    assertEquals(null, loaded.today.sets.single().restSeconds)
    // Calendar reads must not mutate the persisted state.
    assertEquals(raw, preferences().getString(STATE_KEY, null))

    // The next normal write converges to the current format without losing history.
    repository.save(loaded)
    assertEquals(4, JSONObject(preferences().getString(STATE_KEY, null)!!).getInt("version"))
    assertEquals(loaded.history, repository.load().history)
  }

  @Test
  fun `不完全なversion3の保存データは置換せずに保護する`() = runTest {
    val raw = JSONObject().put("version", 3).put("exercises", JSONArray()).toString()
    preferences().edit().putString(STATE_KEY, raw).commit()

    try {
      DefaultWorkoutRepository(context).load()
      fail("CorruptWorkoutStateException was expected")
    } catch (_: CorruptWorkoutStateException) {
      // A recognized legacy version does not bypass payload validation.
    }

    assertEquals(raw, preferences().getString(STATE_KEY, null))
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
  fun `version4の履歴が不正な場合は要素を捨てずpayloadを保全する`() = runTest {
    val raw = JSONObject().apply {
      put("version", 4)
      put("exercises", JSONArray())
      put("menus", JSONArray())
      put("today", JSONObject().put("date", "2026-10-10").put("sets", JSONArray()))
      put("history", JSONArray().put("invalid history entry"))
    }.toString()
    preferences().edit().putString(STATE_KEY, raw).commit()

    try {
      DefaultWorkoutRepository(context).load()
      fail("CorruptWorkoutStateException was expected")
    } catch (_: CorruptWorkoutStateException) {
      // Keep the complete original snapshot rather than silently dropping invalid entries.
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
