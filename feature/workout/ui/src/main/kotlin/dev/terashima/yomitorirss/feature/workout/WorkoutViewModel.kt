package dev.terashima.yomitorirss.feature.workout

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

enum class WorkoutTab(val label: String) {
  WORKOUT("記録"),
  TIMER("タイマー"),
  HISTORY("履歴"),
  SETTINGS("設定"),
}

data class WorkoutUiState(
  val initialized: Boolean = false,
  val snapshot: WorkoutSnapshot = newWorkoutSnapshot(""),
  val selectedExerciseId: String = "",
  val selectedTab: WorkoutTab = WorkoutTab.WORKOUT,
  val amount: String = "10",
  val memo: String = "",
  val stepCount: String = "",
  val intervalDurationSeconds: Int = 90,
  val intervalRemainingSeconds: Int = 90,
  val intervalRunning: Boolean = false,
  val intervalCompletionToken: Int = 0,
  val plankSeconds: Int = 0,
  val plankRunning: Boolean = false,
  val stepUpSeconds: Int = 0,
  val stepUpRunning: Boolean = false,
  val exportMessage: String? = null,
) {
  val activeExercise: WorkoutExercise?
    get() = snapshot.exercises.firstOrNull { it.id == selectedExerciseId } ?: snapshot.exercises.firstOrNull()

  val activeSets: List<WorkoutSet>
    get() = activeExercise?.let { exercise -> snapshot.today.sets.filter { it.exerciseId == exercise.id } }.orEmpty()
}

class WorkoutViewModel(
  private val repository: WorkoutRepository,
  private val historyExporter: WorkoutHistoryExporter,
) : ViewModel() {
  private val _state = MutableStateFlow(WorkoutUiState())
  val state: StateFlow<WorkoutUiState> = _state.asStateFlow()

  private var ticker: Job? = null
  private var intervalDeadlineMillis: Long? = null
  private var plankStartedMillis: Long? = null
  private var plankBaseSeconds = 0
  private var stepUpStartedMillis: Long? = null
  private var stepUpBaseSeconds = 0

  init {
    viewModelScope.launch {
      val now = nowIso()
      val loaded = repository.load().rolloverTo(LocalDate.now().toString(), now)
      val selected = loaded.exercises.firstOrNull()?.id.orEmpty()
      _state.value = WorkoutUiState(
        initialized = true,
        snapshot = loaded,
        selectedExerciseId = selected,
        amount = initialAmount(loaded, selected),
        stepCount = loaded.lastStepCounts[selected]?.toString().orEmpty(),
      )
      repository.save(loaded)
    }
    ticker = viewModelScope.launch {
      while (isActive) {
        delay(250)
        tick(SystemClock.elapsedRealtime())
      }
    }
  }

  fun selectTab(tab: WorkoutTab) = _state.update { it.copy(selectedTab = tab) }

  fun selectExercise(id: String) {
    val snapshot = _state.value.snapshot
    _state.update {
      it.copy(
        selectedExerciseId = id,
        amount = initialAmount(snapshot, id),
        memo = "",
        stepCount = snapshot.lastStepCounts[id]?.toString().orEmpty(),
      )
    }
  }

  fun updateAmount(value: String) = _state.update { it.copy(amount = value.filter(Char::isDigit).take(5)) }
  fun updateMemo(value: String) = _state.update { it.copy(memo = value.take(240)) }
  fun updateStepCount(value: String) = _state.update { it.copy(stepCount = value.filter(Char::isDigit).take(6)) }

  fun adjustAmount(delta: Int) {
    val value = (_state.value.amount.toIntOrNull() ?: 0) + delta
    _state.update { it.copy(amount = value.coerceAtLeast(0).toString()) }
  }

  fun startWorkout() {
    val current = _state.value.snapshot
    if (current.today.startedAt != null) return
    updateSnapshot(current.copy(today = current.today.copy(startedAt = nowIso())))
  }

  fun finishWorkout() {
    val current = _state.value.snapshot
    if (current.today.sets.isEmpty()) return
    val finishedAt = nowIso()
    val history = WorkoutHistory(
      id = UUID.randomUUID().toString(),
      date = current.today.date,
      startedAt = current.today.startedAt,
      finishedAt = finishedAt,
      sets = current.today.sets,
    )
    _state.update { it.copy(exportMessage = null) }
    updateSnapshot(
      current.copy(
        today = WorkoutDay(date = LocalDate.now().toString()),
        history = (listOf(history) + current.history).take(50),
      ),
    )
    resetTimers()
    viewModelScope.launch {
      val result = runCatching { historyExporter.export(history) }.getOrDefault(WorkoutExportResult.FAILED)
      _state.update { it.copy(exportMessage = exportMessage(result)) }
    }
  }

  fun resetToday() {
    val current = _state.value.snapshot
    updateSnapshot(current.copy(today = WorkoutDay(date = LocalDate.now().toString())))
    resetTimers()
  }

  fun recordSet() {
    val ui = _state.value
    val exercise = ui.activeExercise ?: return
    val amount = ui.amount.toIntOrNull()?.coerceAtLeast(0) ?: return
    if (amount <= 0) return
    val recordedAt = nowIso()
    appendSet(
      WorkoutSet(
        id = UUID.randomUUID().toString(),
        exerciseId = exercise.id,
        exerciseName = exercise.name,
        unit = exercise.unit,
        type = exercise.type,
        amount = amount,
        memo = ui.memo.trim(),
        recordedAt = recordedAt,
        startedAt = setStartIso(recordedAt, exercise.unit, amount),
        finishedAt = recordedAt,
      ),
      lastAmount = amount,
    )
    _state.update { it.copy(memo = "") }
    resetInterval()
    startInterval()
  }

  fun undoActiveSet() {
    val ui = _state.value
    val id = ui.activeExercise?.id ?: return
    val sets = ui.snapshot.today.sets.toMutableList()
    val index = sets.indexOfLast { it.exerciseId == id }
    if (index < 0) return
    sets.removeAt(index)
    updateSnapshot(ui.snapshot.copy(today = ui.snapshot.today.copy(sets = sets)))
  }

  fun addExercise(name: String, targetSets: Int, unit: WorkoutUnit) {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return
    val exercise = WorkoutExercise(
      id = UUID.randomUUID().toString(),
      name = trimmed,
      targetSets = targetSets.coerceAtLeast(1),
      unit = unit,
      type = inferWorkoutExerciseType(trimmed, unit),
    )
    val snapshot = _state.value.snapshot.copy(exercises = _state.value.snapshot.exercises + exercise)
    updateSnapshot(snapshot)
    selectExercise(exercise.id)
    selectTab(WorkoutTab.WORKOUT)
  }

  fun removeExercise(id: String) {
    val current = _state.value.snapshot
    val exercises = current.exercises.filterNot { it.id == id }
    updateSnapshot(current.copy(exercises = exercises))
    val next = exercises.firstOrNull()?.id.orEmpty()
    _state.update { it.copy(selectedExerciseId = next, amount = initialAmount(it.snapshot, next)) }
  }

  fun restoreDefaultExercises() {
    val current = _state.value.snapshot
    val exercises = defaultWorkoutExercises()
    updateSnapshot(current.copy(exercises = exercises))
    val selected = exercises.first().id
    _state.update { it.copy(selectedExerciseId = selected, amount = initialAmount(it.snapshot, selected)) }
  }

  fun setIntervalDuration(seconds: Int) {
    intervalDeadlineMillis = null
    _state.update {
      it.copy(
        intervalDurationSeconds = seconds.coerceAtLeast(1),
        intervalRemainingSeconds = seconds.coerceAtLeast(1),
        intervalRunning = false,
      )
    }
  }

  fun startInterval() {
    val ui = _state.value
    if (ui.intervalRunning) return
    val remaining = if (ui.intervalRemainingSeconds <= 0) ui.intervalDurationSeconds else ui.intervalRemainingSeconds
    intervalDeadlineMillis = SystemClock.elapsedRealtime() + remaining * 1000L
    _state.update { it.copy(intervalRemainingSeconds = remaining, intervalRunning = true) }
  }

  fun pauseInterval() {
    tick(SystemClock.elapsedRealtime())
    intervalDeadlineMillis = null
    _state.update { it.copy(intervalRunning = false) }
  }

  fun resetInterval() {
    intervalDeadlineMillis = null
    _state.update { it.copy(intervalRemainingSeconds = it.intervalDurationSeconds, intervalRunning = false) }
  }

  fun startPlank() {
    if (_state.value.plankRunning) return
    plankBaseSeconds = _state.value.plankSeconds
    plankStartedMillis = SystemClock.elapsedRealtime()
    _state.update { it.copy(plankRunning = true) }
  }

  fun pausePlank() {
    tick(SystemClock.elapsedRealtime())
    plankBaseSeconds = _state.value.plankSeconds
    plankStartedMillis = null
    _state.update { it.copy(plankRunning = false) }
  }

  fun resetPlank() {
    plankBaseSeconds = 0
    plankStartedMillis = null
    _state.update { it.copy(plankSeconds = 0, plankRunning = false) }
  }

  fun recordPlank() {
    val ui = _state.value
    val exercise = ui.activeExercise ?: return
    if (exercise.type != WorkoutExerciseType.PLANK || ui.plankSeconds <= 0) return
    val recordedAt = nowIso()
    appendSet(
      WorkoutSet(
        id = UUID.randomUUID().toString(),
        exerciseId = exercise.id,
        exerciseName = exercise.name,
        unit = WorkoutUnit.SECONDS,
        type = WorkoutExerciseType.PLANK,
        amount = ui.plankSeconds,
        memo = ui.memo.trim().ifEmpty { "タイマー記録" },
        recordedAt = recordedAt,
        startedAt = setStartIso(recordedAt, WorkoutUnit.SECONDS, ui.plankSeconds),
        finishedAt = recordedAt,
      ),
      lastAmount = ui.plankSeconds,
    )
    _state.update { it.copy(memo = "") }
    resetPlank()
    resetInterval()
    startInterval()
  }

  fun startStepUp() {
    if (_state.value.stepUpRunning) return
    stepUpBaseSeconds = _state.value.stepUpSeconds
    stepUpStartedMillis = SystemClock.elapsedRealtime()
    _state.update { it.copy(stepUpRunning = true) }
  }

  fun pauseStepUp() {
    tick(SystemClock.elapsedRealtime())
    stepUpBaseSeconds = _state.value.stepUpSeconds
    stepUpStartedMillis = null
    _state.update { it.copy(stepUpRunning = false) }
  }

  fun resetStepUp() {
    stepUpBaseSeconds = 0
    stepUpStartedMillis = null
    _state.update { it.copy(stepUpSeconds = 0, stepUpRunning = false) }
  }

  fun recordStepUp() {
    val ui = _state.value
    val exercise = ui.activeExercise ?: return
    if (exercise.type != WorkoutExerciseType.STEP_UP || ui.stepUpSeconds <= 0) return
    val steps = ui.stepCount.toIntOrNull()?.coerceAtLeast(0) ?: 0
    val recordedAt = nowIso()
    appendSet(
      WorkoutSet(
        id = UUID.randomUUID().toString(),
        exerciseId = exercise.id,
        exerciseName = exercise.name,
        unit = WorkoutUnit.SECONDS,
        type = WorkoutExerciseType.STEP_UP,
        amount = ui.stepUpSeconds,
        steps = steps,
        memo = ui.memo.trim(),
        recordedAt = recordedAt,
        startedAt = setStartIso(recordedAt, WorkoutUnit.SECONDS, ui.stepUpSeconds),
        finishedAt = recordedAt,
      ),
      lastAmount = ui.stepUpSeconds,
      lastSteps = steps,
    )
    _state.update { it.copy(memo = "") }
    resetStepUp()
  }

  private fun appendSet(set: WorkoutSet, lastAmount: Int, lastSteps: Int? = null) {
    val current = _state.value.snapshot
    val startedAt = current.today.startedAt ?: set.startedAt ?: set.recordedAt
    val lastStepCounts = if (lastSteps == null) current.lastStepCounts else current.lastStepCounts + (set.exerciseId to lastSteps)
    updateSnapshot(
      current.copy(
        today = current.today.copy(startedAt = startedAt, sets = current.today.sets + set),
        lastAmounts = current.lastAmounts + (set.exerciseId to lastAmount),
        lastStepCounts = lastStepCounts,
      ),
    )
  }

  private fun updateSnapshot(snapshot: WorkoutSnapshot) {
    _state.update { it.copy(snapshot = snapshot) }
    viewModelScope.launch { repository.save(snapshot) }
  }

  private fun tick(nowMillis: Long) {
    val intervalEnd = intervalDeadlineMillis
    if (_state.value.intervalRunning && intervalEnd != null) {
      val remaining = ceil((intervalEnd - nowMillis).coerceAtLeast(0) / 1000.0).toInt()
      if (remaining <= 0) {
        intervalDeadlineMillis = null
        _state.update {
          it.copy(
            intervalRemainingSeconds = 0,
            intervalRunning = false,
            intervalCompletionToken = it.intervalCompletionToken + 1,
          )
        }
      } else if (remaining != _state.value.intervalRemainingSeconds) {
        _state.update { it.copy(intervalRemainingSeconds = remaining) }
      }
    }
    plankStartedMillis?.let { started ->
      val seconds = plankBaseSeconds + ((nowMillis - started).coerceAtLeast(0) / 1000L).toInt()
      if (seconds != _state.value.plankSeconds) _state.update { it.copy(plankSeconds = seconds) }
    }
    stepUpStartedMillis?.let { started ->
      val seconds = stepUpBaseSeconds + ((nowMillis - started).coerceAtLeast(0) / 1000L).toInt()
      if (seconds != _state.value.stepUpSeconds) _state.update { it.copy(stepUpSeconds = seconds) }
    }
  }

  private fun resetTimers() {
    resetInterval()
    resetPlank()
    resetStepUp()
  }

  private fun initialAmount(snapshot: WorkoutSnapshot, exerciseId: String): String {
    val exercise = snapshot.exercises.firstOrNull { it.id == exerciseId }
    val fallback = if (exercise?.unit == WorkoutUnit.SECONDS) 30 else 10
    return (snapshot.lastAmounts[exerciseId] ?: fallback).toString()
  }

  private fun setStartIso(recordedAt: String, unit: WorkoutUnit, amount: Int): String {
    val durationSeconds = if (unit == WorkoutUnit.SECONDS) amount.coerceAtLeast(1) else 1
    return OffsetDateTime.parse(recordedAt).minusSeconds(durationSeconds.toLong()).toString()
  }

  private fun exportMessage(result: WorkoutExportResult): String = when (result) {
    WorkoutExportResult.EXPORTED -> "Health Connect にワークアウトを書き込みました"
    WorkoutExportResult.PERMISSION_REQUIRED -> "端末内に保存しました。Health Connect への書き込みは「ヘルス」画面で権限を許可すると有効になります。"
    WorkoutExportResult.UNAVAILABLE -> "端末内に保存しました。Health Connect は現在利用できません。"
    WorkoutExportResult.FAILED -> "端末内に保存しました。Health Connect への書き込みに失敗しました。"
  }

  private fun nowIso(): String = OffsetDateTime.now().toString()

  class Factory(
    private val repository: WorkoutRepository,
    private val historyExporter: WorkoutHistoryExporter,
  ) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = WorkoutViewModel(repository, historyExporter) as T
  }
}
