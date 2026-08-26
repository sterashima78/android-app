package dev.terashima.yomitorirss.feature.knowledge.data

import android.content.Context

class KnowledgeBuildQueueStateStore(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences(
    PREFERENCES_NAME,
    Context.MODE_PRIVATE,
  )

  val requested: Boolean
    get() = preferences.getBoolean(KEY_REQUESTED, false)

  val stopped: Boolean
    get() = preferences.getBoolean(KEY_STOPPED, false)

  val failed: Boolean
    get() = preferences.getBoolean(KEY_FAILED, false)

  val error: String?
    get() = preferences.getString(KEY_ERROR, null)

  fun request() {
    preferences.edit()
      .putBoolean(KEY_REQUESTED, true)
      .putBoolean(KEY_STOPPED, false)
      .putBoolean(KEY_FAILED, false)
      .remove(KEY_ERROR)
      .apply()
  }

  fun markStopped() {
    preferences.edit()
      .putBoolean(KEY_STOPPED, true)
      .putBoolean(KEY_FAILED, false)
      .remove(KEY_ERROR)
      .apply()
  }

  fun markReady() {
    preferences.edit()
      .putBoolean(KEY_STOPPED, false)
      .putBoolean(KEY_FAILED, false)
      .remove(KEY_ERROR)
      .apply()
  }

  fun markRetrying(message: String) {
    preferences.edit()
      .putBoolean(KEY_REQUESTED, true)
      .putBoolean(KEY_STOPPED, false)
      .putBoolean(KEY_FAILED, false)
      .putString(KEY_ERROR, message.take(MAX_ERROR_LENGTH))
      .apply()
  }

  fun markFailed(message: String) {
    preferences.edit()
      .putBoolean(KEY_REQUESTED, true)
      .putBoolean(KEY_STOPPED, false)
      .putBoolean(KEY_FAILED, true)
      .putString(KEY_ERROR, message.take(MAX_ERROR_LENGTH))
      .apply()
  }

  fun complete() {
    clear()
  }

  fun clear() {
    preferences.edit().clear().apply()
  }

  private companion object {
    const val PREFERENCES_NAME = "knowledge_ai_build_queue"
    const val KEY_REQUESTED = "requested"
    const val KEY_STOPPED = "stopped"
    const val KEY_FAILED = "failed"
    const val KEY_ERROR = "error"
    const val MAX_ERROR_LENGTH = 500
  }
}
