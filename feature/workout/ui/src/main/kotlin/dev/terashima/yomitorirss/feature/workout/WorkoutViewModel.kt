package dev.terashima.yomitorirss.feature.workout

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.ceil

private fun WorkoutUiState.clearSetDetails(): WorkoutUiState = copy(
  memo = "",
  rpe = null,
  formQuality = null,
  loadKind = null,
  loadValue = "",
  restSeconds = "",
)

class WorkoutViewModel(
  private val repository: WorkoutRepository,
  private val historyExporter: WorkoutHistoryExporter,
  private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
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
      try {
        val loaded = repository.load().rolloverTo(LocalDate.now().toString(), now)
        val selected = loaded.menuExercises().firstOrNull()?.id.orEmpty()
        // Publish an editable state only after the initial persistence succeeds.
        repository.save(loaded)
        _state.value = WorkoutUiState(
          initialized = true,
          snapshot = loaded,
          selectedExerciseId = selected,
          amount = initialAmount(loaded, selected),
          stepCount = loaded.lastStepCounts[selected]?.toString().orEmpty(),
        )
      } catch (error: CancellationException) {
        throw error
      } catch (error: Exception) {
        // Do not initialize an editable empty state after a failed load.
        _state.update { it.copy(loadError = "保存済みデータを読み込めませんでした。元の記録は変更されていません。") }
      }
    }
    ticker = viewModelScope.launch {
      while (isActive) {
        delay(250)
        tick(elapsedRealtimeMillis())
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
        rpe = null,
        formQuality = null,
        loadKind = null,
        loadValue = "",
        restSeconds = "",
        stepCount = snapshot.lastStepCounts[id]?.toString().orEmpty(),
      )
    }
  }

  fun selectMenu(id: String) {
    val current = _state.value.snapshot
    val menu = current.menus.firstOrNull { it.id == id } ?: return
    val snapshot = current.copy(today = current.today.copy(menu = menu))
    val selected = snapshot.menuExercises().firstOrNull()?.id.orEmpty()
    updateSnapshot(snapshot)
    _state.update {
      it.copy(
        selectedExerciseId = selected,
        amount = initialAmount(snapshot, selected),
        stepCount = snapshot.lastStepCounts[selected]?.toString().orEmpty(),
        menuMessage = "「${menu.name}」を今日のメニューにしました",
      ).clearSetDetails()
    }
  }

  fun importMenu(raw: String, saveAsPreset: Boolean = false, source: WorkoutMenuSource = WorkoutMenuSource.IMPORTED) {
    val draft = runCatching { parseWorkoutMenuImport(raw) }.getOrElse { error ->
      _state.update { it.copy(menuMessage = error.message ?: "メニューを読み取れませんでした") }
      return
    }
    val current = _state.value.snapshot
    val exercises = current.exercises.toMutableList()
    val items = mutableListOf<WorkoutMenuItem>()
    val seenExerciseIds = mutableSetOf<String>()
    for (imported in draft.exercises) {
      val exercise = exercises.firstOrNull { candidate ->
        imported.id?.let { it == candidate.id } == true || candidate.name.equals(imported.name, ignoreCase = true)
      } ?: WorkoutExercise(
        id = imported.id ?: UUID.randomUUID().toString(),
        name = imported.name,
        targetSets = imported.targetSets,
        unit = imported.unit,
        type = imported.type,
      ).also(exercises::add)
      if (!seenExerciseIds.add(exercise.id)) {
        _state.update { it.copy(menuMessage = "同じ種目がメニュー内に重複しています: ${exercise.name}") }
        return
      }
      items += WorkoutMenuItem(
        exerciseId = exercise.id,
        targetSets = imported.targetSets,
        targets = imported.targets,
      )
    }
    val menu = WorkoutMenu(
      id = UUID.randomUUID().toString(),
      name = draft.name,
      items = items,
      source = source,
    )
    val presets = if (saveAsPreset) current.menus + menu.copy(source = WorkoutMenuSource.PRESET) else current.menus
    val snapshot = current.copy(
      exercises = exercises,
      menus = presets,
      today = current.today.copy(menu = menu),
    )
    val selected = snapshot.menuExercises().firstOrNull()?.id.orEmpty()
    updateSnapshot(snapshot)
    _state.update {
      it.copy(
        selectedExerciseId = selected,
        amount = initialAmount(snapshot, selected),
        stepCount = snapshot.lastStepCounts[selected]?.toString().orEmpty(),
        selectedTab = WorkoutTab.WORKOUT,
        menuMessage = if (saveAsPreset) "「${menu.name}」を保存して今日のメニューにしました" else "「${menu.name}」を今日のメニューにしました",
      ).clearSetDetails()
    }
  }

  fun saveTodayMenuAsPreset(name: String) {
    val current = _state.value.snapshot
    val sourceMenu = current.today.menu ?: current.effectiveMenu()
    val title = name.trim().ifEmpty { sourceMenu.name }
    val preset = sourceMenu.copy(
      id = UUID.randomUUID().toString(),
      name = title,
      source = WorkoutMenuSource.PRESET,
    )
    val snapshot = current.copy(menus = current.menus + preset)
    updateSnapshot(snapshot)
    _state.update { it.copy(menuMessage = "「$title」をプリセットに保存しました") }
  }

  fun removeMenu(id: String) {
    val current = _state.value.snapshot
    if (current.menus.size <= 1) return
    val menus = current.menus.filterNot { it.id == id }
    val today = if (current.today.menu?.id == id) current.today.copy(menu = menus.firstOrNull()) else current.today
    val snapshot = current.copy(menus = menus, today = today)
    updateSnapshot(snapshot)
    val selected = snapshot.menuExercises().firstOrNull()?.id.orEmpty()
    _state.update { it.copy(selectedExerciseId = selected, amount = initialAmount(snapshot, selected)).clearSetDetails() }
  }

  fun updateAmount(value: String) = _state.update { it.copy(amount = value.filter(Char::isDigit).take(5)) }
  fun updateMemo(value: String) = _state.update { it.copy(memo = value.take(240)) }
  fun updateStepCount(value: String) = _state.update { it.copy(stepCount = value.filter(Char::isDigit).take(6)) }
  fun updateRpe(value: Int?) {
    if (value != null && value !in 1..10) return
    _state.update { it.copy(rpe = value) }
  }
  fun updateFormQuality(value: WorkoutFormQuality?) = _state.update { it.copy(formQuality = value) }
  fun updateLoadKind(value: WorkoutLoadKind?) =
    _state.update { it.copy(loadKind = value, loadValue = "") }
  fun updateLoadValue(value: String) = _state.update { current ->
    val next = when (current.loadKind) {
      WorkoutLoadKind.ADDED_WEIGHT, WorkoutLoadKind.ASSISTED_WEIGHT ->
        value.filter { it.isDigit() || it == '.' }.take(12)
      else -> value.take(100)
    }
    current.copy(loadValue = next)
  }
  fun updateRestSeconds(value: String) =
    _state.update { it.copy(restSeconds = value.filter(Char::isDigit).take(5)) }

  fun reuseLastLoad() {
    val current = _state.value
    val id = current.activeExercise?.id ?: return
    val previous = current.snapshot.today.sets.lastOrNull { it.exerciseId == id && it.load != null }
      ?: current.snapshot.history.asSequence()
        .flatMap { it.sets.asReversed().asSequence() }
        .firstOrNull { it.exerciseId == id && it.load != null }
      ?: return
    _state.update {
      it.copy(
        loadKind = previous.load?.kind,
        loadValue = previous.load?.value.orEmpty(),
      )
    }
  }

  fun adjustAmount(delta: Int) {
    val value = (_state.value.amount.toIntOrNull() ?: 0) + delta
    _state.update { it.copy(amount = value.coerceAtLeast(0).toString()) }
  }

  fun startWorkout() {
    val current = _state.value.snapshot
    if (current.today.startedAt != null) return
    updateSnapshot(current.copy(today = current.today.copy(startedAt = nowIso(), menu = current.effectiveMenu())))
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
      menu = current.today.menu ?: current.effectiveMenu(),
    )
    val nextSnapshot = current.copy(
      today = WorkoutDay(date = LocalDate.now().toString()),
      history = (listOf(history) + current.history).take(50),
    )
    _state.update {
      val selected = nextSnapshot.menuExercises().firstOrNull()?.id.orEmpty()
      it.copy(
        snapshot = nextSnapshot,
        selectedExerciseId = selected,
        amount = initialAmount(nextSnapshot, selected),
        exportMessage = null,
        exportPermissionRequired = false,
      ).clearSetDetails()
    }
    resetTimers()
    viewModelScope.launch {
      val result = runCatching {
        repository.save(nextSnapshot)
        historyExporter.export(history)
      }.getOrDefault(WorkoutExportResult.FAILED)
      updateExportResult(result)
    }
  }

  fun onExportPermissionResult(granted: Boolean) {
    if (!granted) {
      _state.update {
        it.copy(
          exportMessage = "端末内に保存しました。Health Connect への書き込み権限が必要です。",
          exportPermissionRequired = true,
        )
      }
      return
    }
    val history = _state.value.snapshot.history.firstOrNull() ?: return
    _state.update {
      it.copy(
        exportMessage = "Health Connect への書き込みを再試行しています…",
        exportPermissionRequired = false,
      )
    }
    viewModelScope.launch {
      val result = runCatching { historyExporter.export(history) }
        .getOrDefault(WorkoutExportResult.FAILED)
      updateExportResult(result)
    }
  }

  fun resetToday() {
    val current = _state.value.snapshot
    val snapshot = current.copy(today = WorkoutDay(date = LocalDate.now().toString()))
    updateSnapshot(snapshot)
    val selected = snapshot.menuExercises().firstOrNull()?.id.orEmpty()
    _state.update { it.copy(selectedExerciseId = selected, amount = initialAmount(snapshot, selected)).clearSetDetails() }
    resetTimers()
  }

  fun recordSet() {
    val ui = _state.value
    val exercise = ui.activeExercise ?: return
    val amount = ui.amount.toIntOrNull()?.coerceAtLeast(0) ?: return
    if (amount <= 0 || !ui.detailsValid) return
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
    _state.update {
      it.copy(
        memo = "", rpe = null, formQuality = null, restSeconds = "",
        amount = initialAmount(it.snapshot, exercise.id),
      )
    }
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
    val snapshot = ui.snapshot.copy(today = ui.snapshot.today.copy(sets = sets))
    updateSnapshot(snapshot)
    _state.update { it.copy(amount = initialAmount(snapshot, id)) }
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
    val current = _state.value.snapshot
    val sourceMenu = current.today.menu ?: current.effectiveMenu()
    val todayMenu = sourceMenu.copy(
      items = sourceMenu.items + WorkoutMenuItem(
        exerciseId = exercise.id,
        targetSets = exercise.targetSets,
      ),
    )
    val snapshot = current.copy(
      exercises = current.exercises + exercise,
      today = current.today.copy(menu = todayMenu),
    )
    updateSnapshot(snapshot)
    _state.update {
      it.copy(
        selectedExerciseId = exercise.id,
        amount = initialAmount(snapshot, exercise.id),
        stepCount = snapshot.lastStepCounts[exercise.id]?.toString().orEmpty(),
        menuMessage = "種目「${exercise.name}」を登録し、今日のメニューに追加しました。",
      ).clearSetDetails()
    }
  }

  fun removeExercise(id: String) {
    val current = _state.value.snapshot
    val exercises = current.exercises.filterNot { it.id == id }
    val menus = current.menus.map { menu -> menu.copy(items = menu.items.filterNot { it.exerciseId == id }) }
      .filter { it.items.isNotEmpty() }
      .ifEmpty { listOf(defaultWorkoutMenu(exercises)) }
    val currentTodayMenu = current.today.menu
    val todayMenu = currentTodayMenu?.copy(items = currentTodayMenu.items.filterNot { it.exerciseId == id })
      ?.takeIf { it.items.isNotEmpty() }
    val snapshot = current.copy(
      exercises = exercises,
      menus = menus,
      today = current.today.copy(menu = todayMenu),
    )
    updateSnapshot(snapshot)
    val next = snapshot.menuExercises().firstOrNull()?.id.orEmpty()
    _state.update { it.copy(selectedExerciseId = next, amount = initialAmount(snapshot, next)).clearSetDetails() }
  }

  fun restoreDefaultExercises() {
    val exercises = defaultWorkoutExercises()
    val menu = defaultWorkoutMenu(exercises)
    val current = _state.value.snapshot
    val snapshot = current.copy(
      exercises = exercises,
      menus = listOf(menu),
      today = current.today.copy(menu = menu),
    )
    updateSnapshot(snapshot)
    val selected = exercises.first().id
    _state.update { it.copy(selectedExerciseId = selected, amount = initialAmount(snapshot, selected)).clearSetDetails() }
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
    intervalDeadlineMillis = elapsedRealtimeMillis() + remaining * 1000L
    _state.update { it.copy(intervalRemainingSeconds = remaining, intervalRunning = true) }
  }

  fun pauseInterval() {
    tick(elapsedRealtimeMillis())
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
    plankStartedMillis = elapsedRealtimeMillis()
    _state.update { it.copy(plankRunning = true) }
  }

  fun pausePlank() {
    tick(elapsedRealtimeMillis())
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
    if (exercise.type != WorkoutExerciseType.PLANK || ui.plankSeconds <= 0 || !ui.detailsValid) return
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
    _state.update { it.copy(memo = "", rpe = null, formQuality = null, restSeconds = "") }
    resetPlank()
    resetInterval()
    startInterval()
  }

  fun startStepUp() {
    if (_state.value.stepUpRunning) return
    stepUpBaseSeconds = _state.value.stepUpSeconds
    stepUpStartedMillis = elapsedRealtimeMillis()
    _state.update { it.copy(stepUpRunning = true) }
  }

  fun pauseStepUp() {
    tick(elapsedRealtimeMillis())
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
    if (exercise.type != WorkoutExerciseType.STEP_UP || ui.stepUpSeconds <= 0 || !ui.detailsValid) return
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
    _state.update { it.copy(memo = "", rpe = null, formQuality = null, restSeconds = "") }
    resetStepUp()
  }

  private fun appendSet(set: WorkoutSet, lastAmount: Int, lastSteps: Int? = null) {
    val ui = _state.value
    if (!ui.detailsValid) return
    val completedSet = set.copy(
      rpe = ui.rpe,
      formQuality = ui.formQuality,
      load = ui.selectedLoad(),
      restSeconds = ui.restSeconds.toIntOrNull(),
    )
    val current = ui.snapshot
    val startedAt = current.today.startedAt ?: set.startedAt ?: set.recordedAt
    val lastStepCounts = if (lastSteps == null) current.lastStepCounts else current.lastStepCounts + (set.exerciseId to lastSteps)
    updateSnapshot(
      current.copy(
        today = current.today.copy(startedAt = startedAt, menu = current.today.menu ?: current.effectiveMenu(), sets = current.today.sets + completedSet),
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
    val exercise = snapshot.menuExercises().firstOrNull { it.id == exerciseId }
      ?: snapshot.exercises.firstOrNull { it.id == exerciseId }
    val completed = snapshot.today.sets.count { it.exerciseId == exerciseId }
    val planned = snapshot.menuItem(exerciseId)?.targets?.getOrNull(completed)
    val fallback = if (exercise?.unit == WorkoutUnit.SECONDS) 30 else 10
    return (planned ?: snapshot.lastAmounts[exerciseId] ?: fallback).toString()
  }

  private fun setStartIso(recordedAt: String, unit: WorkoutUnit, amount: Int): String {
    val durationSeconds = if (unit == WorkoutUnit.SECONDS) amount.coerceAtLeast(1) else 1
    return OffsetDateTime.parse(recordedAt).minusSeconds(durationSeconds.toLong()).toString()
  }

  private fun updateExportResult(result: WorkoutExportResult) {
    _state.update {
      it.copy(
        exportMessage = exportMessage(result),
        exportPermissionRequired = result == WorkoutExportResult.PERMISSION_REQUIRED,
      )
    }
  }

  private fun exportMessage(result: WorkoutExportResult): String = when (result) {
    WorkoutExportResult.EXPORTED -> "Health Connect にワークアウトを書き込みました"
    WorkoutExportResult.PERMISSION_REQUIRED -> "端末内に保存しました。Health Connect への書き込み権限が必要です。"
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