package dev.terashima.yomitorirss.feature.podcast.data

import android.content.Context
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationLog
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationLogLevel
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * A small, app-private diagnostic history. Not a second generation-state store.
 * Entries contain only predefined messages, counts, timestamps and local program IDs.
 */
class PodcastGenerationLogPreferences(context: Context) : PodcastGenerationLogStore {
  private val preferences = context.applicationContext.getSharedPreferences(
    "podcast_generation_diagnostics",
    Context.MODE_PRIVATE,
  )
  private val _logs = MutableStateFlow(readLogs())
  override val logs: StateFlow<List<PodcastGenerationLog>> = _logs.asStateFlow()

  @Synchronized
  override fun record(log: PodcastGenerationLog) {
    if (log.programId.isBlank() || log.message.isBlank()) return
    writeLogs((listOf(log) + _logs.value).take(MAX_LOG_ENTRIES))
  }

  @Synchronized
  override fun clear(programId: String) {
    writeLogs(_logs.value.filterNot { it.programId == programId })
  }

  private fun writeLogs(entries: List<PodcastGenerationLog>) {
    val array = JSONArray()
    entries.forEach { log ->
      array.put(
        JSONObject()
          .put("program", log.programId)
          .put("at", log.recordedAtEpochMillis)
          .put("level", log.level.name)
          .put("message", log.message),
      )
    }
    // Persist before publishing, so the UI never reports an event that was not saved.
    if (preferences.edit().putString(KEY_LOGS, array.toString()).commit()) {
      _logs.value = entries
    }
  }

  private fun readLogs(): List<PodcastGenerationLog> = runCatching {
    val array = JSONArray(preferences.getString(KEY_LOGS, "[]"))
    buildList {
      for (index in 0 until minOf(array.length(), MAX_LOG_ENTRIES)) {
        val item = array.optJSONObject(index) ?: continue
        val programId = item.optString("program")
        val message = item.optString("message")
        val level = runCatching {
          PodcastGenerationLogLevel.valueOf(item.optString("level"))
        }.getOrNull() ?: continue
        if (programId.isBlank() || message.isBlank()) continue
        add(PodcastGenerationLog(programId, item.optLong("at"), level, message))
      }
    }
  }.getOrDefault(emptyList())

  private companion object {
    const val MAX_LOG_ENTRIES = 100
    const val KEY_LOGS = "recent_events"
  }
}
