package dev.terashima.yomitorirss.feature.workout

import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelExportTest {
  private val dispatcher = StandardTestDispatcher()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `保存後に権限不足を表示し許可後は直近履歴を書き出し直す`() = runTest(dispatcher) {
    val events = mutableListOf<String>()
    val repository = FakeWorkoutRepository(snapshotWithCompletedSet(), events)
    val exporter = FakeWorkoutHistoryExporter(
      results = ArrayDeque(listOf(WorkoutExportResult.PERMISSION_REQUIRED, WorkoutExportResult.EXPORTED)),
      events = events,
    )
    val viewModel = WorkoutViewModel(repository, exporter)
    try {
      runCurrent()
      events.clear()

      viewModel.finishWorkout()
      runCurrent()

      val historyId = exporter.exported.single().id
      assertEquals(listOf("save:$historyId", "export:$historyId"), events)
      assertTrue(viewModel.state.value.exportPermissionRequired)
      assertTrue(viewModel.state.value.exportMessage.orEmpty().contains("書き込み権限が必要"))

      viewModel.onExportPermissionResult(granted = false)
      runCurrent()
      assertEquals(1, exporter.exported.size)
      assertTrue(viewModel.state.value.exportPermissionRequired)

      viewModel.onExportPermissionResult(granted = true)
      assertFalse(viewModel.state.value.exportPermissionRequired)
      runCurrent()

      assertEquals(2, exporter.exported.size)
      assertEquals(historyId, exporter.exported.last().id)
      assertFalse(viewModel.state.value.exportPermissionRequired)
      assertEquals("Health Connect にワークアウトを書き込みました", viewModel.state.value.exportMessage)
    } finally {
      viewModel.viewModelScope.cancel()
    }
  }

  private fun snapshotWithCompletedSet(): WorkoutSnapshot {
    val exercise = defaultWorkoutExercises().first()
    return WorkoutSnapshot(
      exercises = defaultWorkoutExercises(),
      today = WorkoutDay(
        date = LocalDate.now().toString(),
        startedAt = "2026-08-27T07:00:00+09:00",
        sets = listOf(
          WorkoutSet(
            id = "set-1",
            exerciseId = exercise.id,
            exerciseName = exercise.name,
            unit = exercise.unit,
            type = exercise.type,
            amount = 10,
            recordedAt = "2026-08-27T07:01:00+09:00",
            startedAt = "2026-08-27T07:00:59+09:00",
            finishedAt = "2026-08-27T07:01:00+09:00",
          ),
        ),
      ),
    )
  }

  private class FakeWorkoutRepository(
    private var snapshot: WorkoutSnapshot,
    private val events: MutableList<String>,
  ) : WorkoutRepository {
    override suspend fun load(): WorkoutSnapshot = snapshot

    override suspend fun save(snapshot: WorkoutSnapshot) {
      this.snapshot = snapshot
      events += "save:${snapshot.history.firstOrNull()?.id.orEmpty()}"
    }
  }

  private class FakeWorkoutHistoryExporter(
    private val results: ArrayDeque<WorkoutExportResult>,
    private val events: MutableList<String>,
  ) : WorkoutHistoryExporter {
    val exported = mutableListOf<WorkoutHistory>()

    override suspend fun export(history: WorkoutHistory): WorkoutExportResult {
      exported += history
      events += "export:${history.id}"
      return results.removeFirst()
    }
  }
}
