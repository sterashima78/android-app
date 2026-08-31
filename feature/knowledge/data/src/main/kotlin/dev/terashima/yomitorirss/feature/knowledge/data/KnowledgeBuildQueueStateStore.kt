package dev.terashima.yomitorirss.feature.knowledge.data

import android.content.Context
import java.util.UUID

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

  val hasPendingTopics: Boolean
    get() = synchronized(LOCK) {
      preferences.getStringSet(KEY_PENDING_TOPIC_IDS, emptySet()).orEmpty().isNotEmpty()
    }

  fun request(): String = synchronized(LOCK) {
    startNewAttempt()
  }

  fun ensureRequestId(): String? = synchronized(LOCK) {
    if (!requested) return@synchronized null
    preferences.getString(KEY_REQUEST_ID, null) ?: newRequestId().also { requestId ->
      preferences.edit().putString(KEY_REQUEST_ID, requestId).apply()
    }
  }

  fun isActive(requestId: String): Boolean = synchronized(LOCK) {
    requested && !stopped && preferences.getString(KEY_REQUEST_ID, null) == requestId
  }

  fun setPlannedTopics(requestId: String, topicIds: Collection<String>): Boolean = synchronized(LOCK) {
    if (preferences.getString(KEY_REQUEST_ID, null) != requestId || !requested || stopped) {
      return@synchronized false
    }
    preferences.edit()
      .putStringSet(KEY_PENDING_TOPIC_IDS, topicIds.toSet())
      .apply()
    true
  }

  fun clearPlannedTopics() = synchronized(LOCK) {
    if (!requested) return@synchronized
    preferences.edit().remove(KEY_PENDING_TOPIC_IDS).apply()
  }

  fun markTopicCompleted(requestId: String, topicId: String): Boolean = synchronized(LOCK) {
    if (preferences.getString(KEY_REQUEST_ID, null) != requestId || !requested) {
      return@synchronized false
    }
    val pending = preferences.getStringSet(KEY_PENDING_TOPIC_IDS, emptySet()).orEmpty().toMutableSet()
    if (!pending.remove(topicId)) return@synchronized false
    if (pending.isEmpty() && !failed && !stopped) {
      clearLocked()
      true
    } else {
      preferences.edit().putStringSet(KEY_PENDING_TOPIC_IDS, pending).apply()
      false
    }
  }

  fun markStopped() = synchronized(LOCK) {
    preferences.edit()
      .putBoolean(KEY_STOPPED, true)
      .putBoolean(KEY_FAILED, false)
      .remove(KEY_ERROR)
      .apply()
  }

  fun markReady(): String = synchronized(LOCK) {
    startNewAttempt()
  }

  fun markRetrying(requestId: String, message: String) = synchronized(LOCK) {
    if (preferences.getString(KEY_REQUEST_ID, null) != requestId || !requested || failed) {
      return@synchronized
    }
    preferences.edit()
      .putBoolean(KEY_STOPPED, false)
      .putString(KEY_ERROR, message.take(MAX_ERROR_LENGTH))
      .apply()
  }

  fun markFailed(requestId: String, message: String) = synchronized(LOCK) {
    if (preferences.getString(KEY_REQUEST_ID, null) != requestId || !requested) {
      return@synchronized
    }
    preferences.edit()
      .putBoolean(KEY_STOPPED, false)
      .putBoolean(KEY_FAILED, true)
      .putString(KEY_ERROR, message.take(MAX_ERROR_LENGTH))
      .apply()
  }

  fun complete(requestId: String): Boolean = synchronized(LOCK) {
    if (preferences.getString(KEY_REQUEST_ID, null) != requestId || !requested || stopped || failed) {
      return@synchronized false
    }
    clearLocked()
    true
  }

  fun clear() = synchronized(LOCK) {
    clearLocked()
  }

  private fun startNewAttempt(): String {
    val requestId = newRequestId()
    preferences.edit()
      .putBoolean(KEY_REQUESTED, true)
      .putBoolean(KEY_STOPPED, false)
      .putBoolean(KEY_FAILED, false)
      .putString(KEY_REQUEST_ID, requestId)
      .remove(KEY_PENDING_TOPIC_IDS)
      .remove(KEY_ERROR)
      .apply()
    return requestId
  }

  private fun clearLocked() {
    preferences.edit().clear().apply()
  }

  private fun newRequestId(): String = UUID.randomUUID().toString()

  private companion object {
    val LOCK = Any()
    const val PREFERENCES_NAME = "knowledge_ai_build_queue"
    const val KEY_REQUESTED = "requested"
    const val KEY_STOPPED = "stopped"
    const val KEY_FAILED = "failed"
    const val KEY_ERROR = "error"
    const val KEY_REQUEST_ID = "request_id"
    const val KEY_PENDING_TOPIC_IDS = "pending_topic_ids"
    const val MAX_ERROR_LENGTH = 500
  }
}
