package dev.terashima.yomitorirss.feature.workout.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
  fun `version2のstateは保存後に同じ現行形式として読み込める`() = runTest {
    val repository = DefaultWorkoutRepository(context)
    val original = newWorkoutSnapshot("2026-09-22")

    repository.save(original)
    val loaded = repository.load()

    assertEquals(2, loaded.version)
    assertEquals(original.exercises, loaded.exercises)
    assertEquals(original.menus, loaded.menus)
    assertEquals(original.today, loaded.today)
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
