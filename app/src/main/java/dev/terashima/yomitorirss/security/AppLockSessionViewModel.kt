package dev.terashima.yomitorirss.security

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
