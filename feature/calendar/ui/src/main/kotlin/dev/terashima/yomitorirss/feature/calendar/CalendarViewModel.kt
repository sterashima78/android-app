package dev.terashima.yomitorirss.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalendarUiState(
  val month: YearMonth,
  val selectedDate: LocalDate,
  val events: List<CalendarEvent> = emptyList(),
  val loading: Boolean = true,
  val error: String? = null,
)

class CalendarViewModel(
  private val repository: CalendarRepository,
  private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {
  private val today = LocalDate.now(clock)
  private val _state = MutableStateFlow(
    CalendarUiState(
      month = YearMonth.from(today),
      selectedDate = today,
    ),
  )
  val state: StateFlow<CalendarUiState> = _state.asStateFlow()
  private var loadJob: Job? = null

  init {
    reload()
  }

  fun previousMonth() = selectMonth(_state.value.month.minusMonths(1))

  fun nextMonth() = selectMonth(_state.value.month.plusMonths(1))

  fun goToToday() {
    val date = LocalDate.now(clock)
    _state.update { it.copy(month = YearMonth.from(date), selectedDate = date) }
    reload()
  }

  fun selectDate(date: LocalDate) {
    if (YearMonth.from(date) != _state.value.month) {
      _state.update { it.copy(month = YearMonth.from(date), selectedDate = date) }
      reload()
    } else {
      _state.update { it.copy(selectedDate = date) }
    }
  }

  fun reload() {
    val month = _state.value.month
    loadJob?.cancel()
    loadJob = viewModelScope.launch(Dispatchers.IO) {
      _state.update { it.copy(loading = true, error = null) }
      runCatching {
        repository.events(
          fromInclusive = month.atDay(1),
          untilExclusive = month.plusMonths(1).atDay(1),
        )
      }.onSuccess { events ->
        if (_state.value.month == month) {
          _state.update { it.copy(events = events, loading = false, error = null) }
        }
      }.onFailure { error ->
        if (_state.value.month == month) {
          _state.update {
            it.copy(
              loading = false,
              error = error.message ?: "カレンダーの読み込みに失敗しました",
            )
          }
        }
      }
    }
  }

  private fun selectMonth(month: YearMonth) {
    val current = _state.value
    val selectedDay = current.selectedDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())
    _state.update {
      it.copy(
        month = month,
        selectedDate = month.atDay(selectedDay),
      )
    }
    reload()
  }

  class Factory(private val repository: CalendarRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
      require(modelClass.isAssignableFrom(CalendarViewModel::class.java))
      @Suppress("UNCHECKED_CAST")
      return CalendarViewModel(repository) as T
    }
  }
}

internal fun CalendarEvent.occursOn(
  date: LocalDate,
  zoneId: ZoneId = ZoneId.systemDefault(),
): Boolean = when (val eventTime = time) {
  is CalendarEventTime.AllDay -> date >= eventTime.startDate && date < eventTime.endDateExclusive
  is CalendarEventTime.Timed -> {
    val startDate = eventTime.start.atZone(zoneId).toLocalDate()
    val endDate = eventTime.endExclusive
      ?.minusMillis(1)
      ?.atZone(zoneId)
      ?.toLocalDate()
      ?: startDate
    date >= startDate && date <= endDate
  }
}
