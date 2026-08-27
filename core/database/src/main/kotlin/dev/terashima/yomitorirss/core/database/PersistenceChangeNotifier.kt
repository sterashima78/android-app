package dev.terashima.yomitorirss.core.database

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Application-wide signal for committed durable database mutations. */
class PersistenceChangeNotifier {
  private val _version = MutableStateFlow(0L)
  val version: StateFlow<Long> = _version.asStateFlow()

  fun notifyChanged() {
    _version.update { it + 1L }
  }

  companion object {
    val shared: PersistenceChangeNotifier = PersistenceChangeNotifier()
  }
}
