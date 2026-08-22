package dev.terashima.yomitorirss.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HealthPeriod(val label: String) {
  DAY("日"),
  WEEK("週"),
  MONTH("月"),
}

data class HealthDateRange(
  val startDate: LocalDate,
  val endDateExclusive: LocalDate,
) {
  init {
    require(startDate < endDateExclusive) { "HealthDateRange must not be empty" }
  }
}

sealed interface HealthUiState {
  data object Loading : HealthUiState
  data object PermissionRequired : HealthUiState
  data object HistoryPermissionRequired : HealthUiState
  data object HistoryUnsupported : HealthUiState
  data object Unavailable : HealthUiState
  data object ProviderUpdateRequired : HealthUiState
  data class Content(
    val period: HealthPeriod,
    val selectedDate: LocalDate,
    val range: HealthDateRange,
    val overview: HealthOverview,
    val refreshedAt: Instant,
    val canMoveNext: Boolean,
  ) : HealthUiState
  data class Error(val message: String) : HealthUiState
}

class HealthViewModel(
  private val repository: HealthRepository,
  private val clock: Clock = Clock.systemDefaultZone(),
  private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
  private val _state = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
  val state: StateFlow<HealthUiState> = _state.asStateFlow()

  private var selectedPeriod = HealthPeriod.DAY
  private var selectedDate = currentDate()
  private var loadJob: Job? = null

  init {
    refresh()
  }

  fun selectPeriod(period: HealthPeriod) {
    if (selectedPeriod == period && _state.value is HealthUiState.Content) return
    selectedPeriod = period
    refresh()
  }

  fun selectDate(date: LocalDate) {
    val clamped = if (date > currentDate()) currentDate() else date
    if (selectedDate == clamped && _state.value is HealthUiState.Content) return
    selectedDate = clamped
    refresh()
  }

  fun movePrevious() {
    selectedDate = selectedPeriod.shift(selectedDate, -1)
    refresh()
  }

  fun moveNext() {
    val today = currentDate()
    val candidate = selectedPeriod.shift(selectedDate, 1)
    val candidateRange = selectedPeriod.dateRange(candidate)
    if (candidateRange.startDate > today) return
    selectedDate = if (candidate > today) today else candidate
    refresh()
  }

  fun goToday() {
    selectedDate = currentDate()
    refresh()
  }

  fun refresh() {
    loadJob?.cancel()
    loadJob = viewModelScope.launch {
      _state.value = HealthUiState.Loading
      try {
        when (repository.availability()) {
          HealthAvailability.UNAVAILABLE -> {
            _state.value = HealthUiState.Unavailable
            return@launch
          }
          HealthAvailability.PROVIDER_UPDATE_REQUIRED -> {
            _state.value = HealthUiState.ProviderUpdateRequired
            return@launch
          }
          HealthAvailability.AVAILABLE -> Unit
        }

        if (!repository.hasRequiredPermissions()) {
          _state.value = HealthUiState.PermissionRequired
          return@launch
        }

        val now = Instant.now(clock)
        val today = LocalDate.ofInstant(now, zoneId)
        val range = selectedPeriod.dateRange(selectedDate)
        if (range.requiresHistory(today)) {
          when (repository.historyAccess()) {
            HealthHistoryAccess.AVAILABLE -> Unit
            HealthHistoryAccess.PERMISSION_REQUIRED -> {
              _state.value = HealthUiState.HistoryPermissionRequired
              return@launch
            }
            HealthHistoryAccess.UNSUPPORTED -> {
              _state.value = HealthUiState.HistoryUnsupported
              return@launch
            }
          }
        }

        val start = range.startDate.atStartOfDay(zoneId).toInstant()
        val naturalEnd = range.endDateExclusive.atStartOfDay(zoneId).toInstant()
        val end = minOf(naturalEnd, now)
        if (start >= end) {
          _state.value = HealthUiState.Error("未来の期間は表示できません")
          return@launch
        }

        _state.value = HealthUiState.Content(
          period = selectedPeriod,
          selectedDate = selectedDate,
          range = range,
          overview = repository.readOverview(start, end),
          refreshedAt = Instant.now(clock),
          canMoveNext = range.endDateExclusive <= today,
        )
      } catch (error: CancellationException) {
        throw error
      } catch (_: SecurityException) {
        _state.value = HealthUiState.PermissionRequired
      } catch (error: Exception) {
        _state.value = HealthUiState.Error(error.message ?: "ヘルスデータを読み込めませんでした")
      }
    }
  }

  fun onPermissionResult() = refresh()

  private fun currentDate(): LocalDate = LocalDate.ofInstant(Instant.now(clock), zoneId)

  class Factory(private val repository: HealthRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HealthViewModel(repository) as T
  }
}

internal fun HealthPeriod.dateRange(anchor: LocalDate): HealthDateRange = when (this) {
  HealthPeriod.DAY -> HealthDateRange(anchor, anchor.plusDays(1))
  HealthPeriod.WEEK -> {
    val start = anchor.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    HealthDateRange(start, start.plusWeeks(1))
  }
  HealthPeriod.MONTH -> {
    val start = anchor.withDayOfMonth(1)
    HealthDateRange(start, start.plusMonths(1))
  }
}

internal fun HealthPeriod.shift(anchor: LocalDate, amount: Long): LocalDate = when (this) {
  HealthPeriod.DAY -> anchor.plusDays(amount)
  HealthPeriod.WEEK -> anchor.plusWeeks(amount)
  HealthPeriod.MONTH -> anchor.plusMonths(amount)
}

internal fun HealthDateRange.requiresHistory(today: LocalDate): Boolean =
  startDate.isBefore(today.minusDays(30))
