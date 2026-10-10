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
      assertEquals("基本メニュー", exporter.exported.single().menu?.name)
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

  @Test
  fun `詳細は任意で保存でき明示操作で前回値を再利用できる`() = runTest(dispatcher) {
    val repository = FakeWorkoutRepository(newWorkoutSnapshot(LocalDate.now().toString()), mutableListOf())
    val exporter = FakeWorkoutHistoryExporter(ArrayDeque(), mutableListOf())
    val viewModel = WorkoutViewModel(repository, exporter, elapsedRealtimeMillis = { 0L })
    try {
      runCurrent()
      viewModel.updateLoadKind(WorkoutLoadKind.ADDED_WEIGHT)
      assertFalse(viewModel.state.value.detailsValid)
      viewModel.updateLoadValue("5.5")
      viewModel.updateRpe(7)
      viewModel.updateFormQuality(WorkoutFormQuality.STABLE)
      viewModel.updateRestSeconds("90")
      assertTrue(viewModel.state.value.detailsValid)

      viewModel.recordSet()
      runCurrent()
      val recorded = viewModel.state.value.snapshot.today.sets.single()
      assertEquals(7, recorded.rpe)
      assertEquals(WorkoutFormQuality.STABLE, recorded.formQuality)
      assertEquals(WorkoutLoad(WorkoutLoadKind.ADDED_WEIGHT, "5.5"), recorded.load)
      assertEquals(90, recorded.restSeconds)
      assertEquals(null, viewModel.state.value.rpe)

      viewModel.reuseLastLoad()
      assertEquals(null, viewModel.state.value.rpe)
      assertEquals(null, viewModel.state.value.formQuality)
      assertEquals("5.5", viewModel.state.value.loadValue)
      assertEquals("", viewModel.state.value.restSeconds)

      val other = viewModel.state.value.snapshot.exercises.first { it.id != recorded.exerciseId }
      viewModel.selectExercise(other.id)
      assertEquals(null, viewModel.state.value.rpe)
      assertEquals(null, viewModel.state.value.loadKind)
    } finally {
      viewModel.viewModelScope.cancel()
    }
  }

  @Test
  fun `初期読み込みが失敗した場合は空状態を保存せず操作を無効にする`() = runTest(dispatcher) {
    val events = mutableListOf<String>()
    val repository = object : WorkoutRepository {
      override suspend fun load(): WorkoutSnapshot = throw IllegalStateException("invalid payload")
      override suspend fun save(snapshot: WorkoutSnapshot) { events += "saved" }
    }
    val viewModel = WorkoutViewModel(repository, FakeWorkoutHistoryExporter(ArrayDeque(), events))
    try {
      runCurrent()
      assertFalse(viewModel.state.value.initialized)
      assertTrue(viewModel.state.value.loadError.orEmpty().contains("変更されていません"))
      assertTrue(events.isEmpty())
    } finally {
      viewModel.viewModelScope.cancel()
    }
  }

  @Test
  fun `直近セットの負荷が未入力ならさらに前の記録から再利用する`() = runTest(dispatcher) {
    val original = snapshotWithCompletedSet()
    val recorded = original.today.sets.single()
    val snapshot = original.copy(
      today = original.today.copy(sets = listOf(
        recorded.copy(load = WorkoutLoad(WorkoutLoadKind.ADDED_WEIGHT, "5")),
        recorded.copy(id = "newer", load = null),
      )),
    )
    val repository = FakeWorkoutRepository(snapshot, mutableListOf())
    val viewModel = WorkoutViewModel(repository, FakeWorkoutHistoryExporter(ArrayDeque(), mutableListOf()))
    try {
      runCurrent()
      viewModel.reuseLastLoad()
      assertEquals(WorkoutLoadKind.ADDED_WEIGHT, viewModel.state.value.loadKind)
      assertEquals("5", viewModel.state.value.loadValue)
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
