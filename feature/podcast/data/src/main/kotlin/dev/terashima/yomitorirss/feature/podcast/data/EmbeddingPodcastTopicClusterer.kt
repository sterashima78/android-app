package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.core.aiinference.BackgroundTextEmbedding
import dev.terashima.yomitorirss.feature.podcast.PodcastClusteringStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastNewsClusterer
import dev.terashima.yomitorirss.feature.podcast.PodcastNewsClusteringResult
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException

/** Experimental topic grouping; related but distinct events may share a chapter. */
class EmbeddingPodcastTopicClusterer(
  private val embedding: BackgroundTextEmbedding,
) : PodcastNewsClusterer {
  override suspend fun cluster(
    provider: PodcastGenerationProvider,
    candidates: List<PodcastFeedEntry>,
  ): PodcastNewsClusteringResult {
    if (candidates.size <= 1) {
      return PodcastNewsClusteringResult(candidates.indices.map { listOf(it) }, PodcastClusteringStatus.SKIPPED)
    }
    return try {
      val vectors = embedding.embed(candidates.map { entry ->
        val summary = entry.feedContent.replace(Regex("\\s+"), " ").take(400)
        "${entry.title}\n$summary"
      })
      require(vectors.size == candidates.size && vectors.isNotEmpty())
      val dimension = vectors.first().size
      require(dimension > 0 && vectors.all { it.size == dimension && it.all(Float::isFinite) })
      PodcastNewsClusteringResult(groupRelatedTopics(vectors), PodcastClusteringStatus.SUCCESS)
    } catch (cancel: CancellationException) {
      throw cancel
    } catch (_: Exception) {
      PodcastNewsClusteringResult(
        candidates.indices.map { listOf(it) },
        PodcastClusteringStatus.FALLBACK_INFERENCE_ERROR,
      )
    }
  }
}

/**
 * Complete-link agglomeration prevents chaining loosely related article pairs.
 * Cap cluster size so every distinct event remains representable in the chapter prompt.
 * Similarity threshold is provisional and must be calibrated on labeled news pairs.
 */
internal fun groupRelatedTopics(
  vectors: List<FloatArray>,
  threshold: Double = 0.78,
  maxArticlesPerGroup: Int = 6,
): List<List<Int>> {
  require(threshold in -1.0..1.0 && maxArticlesPerGroup > 0)
  if (vectors.isEmpty()) return emptyList()
  val size = vectors.first().size
  require(size > 0 && vectors.all { it.size == size && it.all(Float::isFinite) })
  val groups = vectors.indices.map { mutableListOf(it) }.toMutableList()
  val norms = vectors.map { v -> sqrt(v.sumOf { it.toDouble() * it.toDouble() }) }
  require(norms.all { it > 0.0 && it.isFinite() })
  fun similarity(left: Int, right: Int): Double {
    val dot = vectors[left].indices.sumOf { i ->
      vectors[left][i].toDouble() * vectors[right][i].toDouble()
    }
    return dot / (norms[left] * norms[right])
  }
  while (true) {
    var bestLeft = -1
    var bestRight = -1
    var bestScore = threshold
    for (left in 0 until groups.size) {
      for (right in left + 1 until groups.size) {
        if (groups[left].size + groups[right].size > maxArticlesPerGroup) continue
        val minimum = groups[left].minOf { a -> groups[right].minOf { b -> similarity(a, b) } }
        if (minimum > bestScore) {
          bestScore = minimum
          bestLeft = left
          bestRight = right
        }
      }
    }
    if (bestLeft < 0) break
    groups[bestLeft].addAll(groups.removeAt(bestRight))
    groups[bestLeft].sort()
  }
  return groups.map { it.toList() }.sortedBy { it.first() }
}
