package dev.terashima.yomitorirss.feature.podcast.data

import android.content.Context
import kotlin.math.roundToInt
import dev.terashima.yomitorirss.feature.podcast.DEFAULT_PODCAST_TOPIC_SIMILARITY_THRESHOLD
import dev.terashima.yomitorirss.feature.podcast.MAX_PODCAST_TOPIC_SIMILARITY_THRESHOLD
import dev.terashima.yomitorirss.feature.podcast.MIN_PODCAST_TOPIC_SIMILARITY_THRESHOLD
import dev.terashima.yomitorirss.feature.podcast.PodcastTopicClusteringSettings
import dev.terashima.yomitorirss.feature.podcast.normalizePodcastTopicSimilarityThreshold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Experimental preference shared by all podcast programs.
 * A missing preference tracks the app default, so the default can be calibrated later.
 */
class PodcastTopicClusteringPreferences(context: Context) : PodcastTopicClusteringSettings {
  private val preferences = context.applicationContext.getSharedPreferences(
    PREFERENCES_NAME,
    Context.MODE_PRIVATE,
  )

  private val defaultTicks = (DEFAULT_PODCAST_TOPIC_SIMILARITY_THRESHOLD * 100).roundToInt()
  private val minTicks = (MIN_PODCAST_TOPIC_SIMILARITY_THRESHOLD * 100).roundToInt()
  private val maxTicks = (MAX_PODCAST_TOPIC_SIMILARITY_THRESHOLD * 100).roundToInt()

  private val _similarityThreshold = MutableStateFlow(
    runCatching { preferences.getInt(KEY_THRESHOLD_PERCENT, defaultTicks) }
      .getOrDefault(defaultTicks)
      .coerceIn(minTicks, maxTicks) / 100.0,
  )
  override val similarityThreshold: StateFlow<Double> = _similarityThreshold.asStateFlow()

  override fun updateSimilarityThreshold(value: Double) {
    val normalized = normalizePodcastTopicSimilarityThreshold(value)
    val ticks = (normalized * 100).roundToInt()
    val editor = preferences.edit()
    if (ticks == defaultTicks) {
      editor.remove(KEY_THRESHOLD_PERCENT)
    } else {
      editor.putInt(KEY_THRESHOLD_PERCENT, ticks)
    }
    editor.apply()
    _similarityThreshold.value = normalized
  }

  private companion object {
    const val PREFERENCES_NAME = "podcast_topic_clustering"
    const val KEY_THRESHOLD_PERCENT = "similarity_threshold_percent"
  }
}
