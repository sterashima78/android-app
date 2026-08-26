package dev.terashima.yomitorirss

import android.content.Context

internal class AppLockPreferences(context: Context) {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  var enabled: Boolean
    get() = preferences.getBoolean(KEY_ENABLED, false)
    set(value) {
      preferences.edit().putBoolean(KEY_ENABLED, value).apply()
    }

  private companion object {
    const val PREFERENCES_NAME = "app_lock"
    const val KEY_ENABLED = "enabled"
  }
}
