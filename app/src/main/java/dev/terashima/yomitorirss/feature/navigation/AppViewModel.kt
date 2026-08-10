package dev.terashima.yomitorirss.feature.navigation

import androidx.lifecycle.ViewModel
import dev.terashima.yomitorirss.feature.navigation.MainTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppUiState(
  val selectedTab: MainTab = MainTab.UNREAD,
  val message: String? = null,
)

class AppViewModel : ViewModel() {
  private val _state = MutableStateFlow(AppUiState())
  val state: StateFlow<AppUiState> = _state.asStateFlow()

  fun selectTab(tab: MainTab) {
    _state.update { it.copy(selectedTab = tab) }
  }

  fun showMessage(message: String) {
    _state.update { it.copy(message = message) }
  }

  fun dismissMessage() {
    _state.update { it.copy(message = null) }
  }
}
