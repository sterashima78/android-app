package dev.terashima.yomitorirss.feature.podcast

import kotlin.math.roundToInt
import kotlinx.coroutines.flow.StateFlow

const val DEFAULT_PODCAST_TOPIC_SIMILARITY_THRESHOLD = 0.95
const val MIN_PODCAST_TOPIC_SIMILARITY_THRESHOLD = 0.70
const val MAX_PODCAST_TOPIC_SIMILARITY_THRESHOLD = 0.99

/** A device-wide tuning parameter for embedding-based topic clustering. */
interface PodcastTopicClusteringSettings {
  val similarityThreshold: StateFlow<Double>

  fun updateSimilarityThreshold(value: Double)
}

/** Normalize to the 0.01 increments offered by the tuning control. */
fun normalizePodcastTopicSimilarityThreshold(value: Double): Double {
  require(value.isFinite() && value >= MIN_PODCAST_TOPIC_SIMILARITY_THRESHOLD &&
    value <= MAX_PODCAST_TOPIC_SIMILARITY_THRESHOLD) {
    "topic similarity threshold must be between 0.70 and 0.99"
  }
  return (value * 100).roundToInt() / 100.0
}
