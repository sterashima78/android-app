package dev.terashima.yomitorirss.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HealthPeriod(val label: String) {
  TODAY("今日"),
  SEVEN_DAYS("7日"),
  THIRTY_DAYS("30日"),
}

sealed interface HealthUiState {
  data object Loading : HealthUiState
  data object PermissionRequired : HealthUiState
  data object Unavailable : HealthUiState
  data object ProviderUpdateRequired : HealthUiState
  data class Content(
    val period: HealthPeriod,
    val overview: HealthOverview,
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

  private var selectedPeriod = HealthPeriod.TODAY
  private var loadJob: Job? = null

  init {
    refresh()
  }

  fun selectPeriod(period: HealthPeriod) {
    if (selectedPeriod == period && _state.value is HealthUiState.Content) return
    selectedPeriod = period
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

        val end = Instant.now(clock)
        val start = selectedPeriod.startInstant(end, zoneId)
        _state.value = HealthUiState.Content(
          period = selectedPeriod,
          overview = repository.readOverview(start, end),
        )
      } catch (_: SecurityException) {
        _state.value = HealthUiState.PermissionRequired
      } catch (error: Exception) {
        _state.value = HealthUiState.Error(error.message ?: "ヘルスデータを読み込めませんでした")
      }
    }
  }

  fun onPermissionResult() = refresh()

  class Factory(private val repository: HealthRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HealthViewModel(repository) as T
  }
}

internal fun HealthPeriod.startInstant(end: Instant, zoneId: ZoneId): Instant = when (this) {
  HealthPeriod.TODAY -> LocalDate.ofInstant(end, zoneId).atStartOfDay(zoneId).toInstant()
  HealthPeriod.SEVEN_DAYS -> end.minus(Duration.ofDays(7))
  HealthPeriod.THIRTY_DAYS -> end.minus(Duration.ofDays(30))
}
