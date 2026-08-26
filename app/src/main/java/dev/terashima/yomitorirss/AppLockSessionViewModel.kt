package dev.terashima.yomitorirss

import androidx.lifecycle.ViewModel

internal class AppLockSessionViewModel : ViewModel() {
  var unlocked: Boolean = false
    private set

  fun unlock() {
    unlocked = true
  }

  fun lock() {
    unlocked = false
  }
}
