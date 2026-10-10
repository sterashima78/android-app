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
  val norms = vectors.map { v -> sqrt(v.sumOf { it.toDouble() * it.toDouble() }) }
  require(norms.all { it > 0.0 && it.isFinite() })
  val similarities = Array(vectors.size) { DoubleArray(vectors.size) { 1.0 } }
  val edges = mutableListOf<Triple<Int, Int, Double>>()
  for (left in vectors.indices) {
    for (right in left + 1 until vectors.size) {
      val dot = vectors[left].indices.sumOf { i ->
        vectors[left][i].toDouble() * vectors[right][i].toDouble()
      }
      val score = dot / (norms[left] * norms[right])
      require(score.isFinite())
      similarities[left][right] = score
      similarities[right][left] = score
      if (score >= threshold) edges += Triple(left, right, score)
    }
  }
  // Consider strongest links first, and validate all cross-pairs before merging.
  edges.sortWith(compareByDescending<Triple<Int, Int, Double>> { it.third }.thenBy { it.first }.thenBy { it.second })
  val roots = vectors.indices.toMutableList()
  val groups = vectors.indices.associateWith { mutableListOf(it) }.toMutableMap()
  for ((left, right) in edges) {
    val leftRoot = roots[left]
    val rightRoot = roots[right]
    if (leftRoot == rightRoot) continue
    val leftMembers = requireNotNull(groups[leftRoot])
    val rightMembers = requireNotNull(groups[rightRoot])
    if (leftMembers.size + rightMembers.size > maxArticlesPerGroup) continue
    if (!leftMembers.all { a -> rightMembers.all { b -> similarities[a][b] >= threshold } }) continue
    leftMembers.addAll(rightMembers)
    leftMembers.sort()
    rightMembers.forEach { roots[it] = leftRoot }
    groups.remove(rightRoot)
  }
  return groups.values.map { it.toList() }.sortedBy { it.first() }
}
