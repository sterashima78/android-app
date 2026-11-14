package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.core.aiinference.BackgroundTextEmbedding
import dev.terashima.yomitorirss.feature.podcast.PodcastClusteringStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingPodcastTopicClustererTest {
  @Test
  fun `related events form a topic while unrelated news stays separate`() = runSuspend {
    val clusterer = EmbeddingPodcastTopicClusterer(
      object : BackgroundTextEmbedding {
        override suspend fun embed(texts: List<String>): List<FloatArray> {
          assertEquals(3, texts.size)
          assertTrue(texts.all { it.startsWith("task: clustering | text:") })
          return listOf(floatArrayOf(1f, 0f), floatArrayOf(.98f, .199f), floatArrayOf(0f, 1f))
        }
      },
    )
    val result = clusterer.cluster(PodcastGenerationProvider.CLOUD, listOf(entry("a"), entry("b"), entry("c")))
    assertEquals(PodcastClusteringStatus.SUCCESS, result.status)
    assertEquals(listOf(listOf(0, 1), listOf(2)), result.groups)
  }

  @Test
  fun `updated threshold is used on the next clustering pass`() = runSuspend {
    var threshold = 0.78
    val clusterer = EmbeddingPodcastTopicClusterer(
      embedding = object : BackgroundTextEmbedding {
        override suspend fun embed(texts: List<String>): List<FloatArray> =
          listOf(floatArrayOf(1f, 0f), floatArrayOf(.9f, .43589f))
      },
      similarityThreshold = { threshold },
    )
    val candidates = listOf(entry("a"), entry("b"))
    assertEquals(
      listOf(listOf(0, 1)),
      clusterer.cluster(PodcastGenerationProvider.LOCAL, candidates).groups,
    )

    threshold = 0.95
    assertEquals(
      listOf(listOf(0), listOf(1)),
      clusterer.cluster(PodcastGenerationProvider.LOCAL, candidates).groups,
    )
  }

  @Test
  fun `complete link prevents transitive topic chains`() {
    val result = groupRelatedTopics(
      listOf(
        floatArrayOf(1f, 0f),
        floatArrayOf(.9f, .43589f),
        floatArrayOf(.56f, .82849f),
      ),
      threshold = .8,
    )
    assertEquals(listOf(listOf(0, 1), listOf(2)), result)
  }

  @Test
  fun `topic group limits protect chapter coverage`() {
    val vectors = List(9) { floatArrayOf(1f, 0f) }
    val groups = groupRelatedTopics(vectors, maxArticlesPerGroup = 3)
    assertTrue(groups.all { it.size <= 3 })
    assertEquals((0..8).toList(), groups.flatten().sorted())
  }

  @Test
  fun `embedding failure does not drop articles`() = runSuspend {
    val clusterer = EmbeddingPodcastTopicClusterer(object : BackgroundTextEmbedding {
      override suspend fun embed(texts: List<String>): List<FloatArray> = error("model unavailable")
    })
    val result = clusterer.cluster(PodcastGenerationProvider.LOCAL, listOf(entry("a"), entry("b")))
    assertEquals(PodcastClusteringStatus.FALLBACK_INFERENCE_ERROR, result.status)
    assertEquals(listOf(listOf(0), listOf(1)), result.groups)
  }

  @Test
  fun `prepared clustering reuses one embedding pass for all archive subsets and final partitions`() = runSuspend {
    var embeddingCalls = 0
    var capturedThreshold = 0.95
    val candidates = (0 until 25).map { entry("old-$it") } + entry("repeat") + entry("fresh")
    val clusterer = EmbeddingPodcastTopicClusterer(
      embedding = object : BackgroundTextEmbedding {
        override suspend fun embed(texts: List<String>): List<FloatArray> {
          embeddingCalls++
          assertEquals(27, texts.size)
          return texts.indices.map { index ->
            // Only old-24 and repeat share a vector.
            val dimension = if (index == 25) 24 else index
            FloatArray(texts.size) { if (it == dimension) 1f else 0f }
          }
        }
      },
      similarityThreshold = { capturedThreshold },
    )
    val prepared = clusterer.prepare(PodcastGenerationProvider.LOCAL, candidates)
    val first = prepared.cluster((0 until 12).toList() + listOf(25, 26))
    val last = prepared.cluster(listOf(24, 25, 26))
    val fresh = prepared.cluster(listOf(26))
    val repeated = prepared.cluster(listOf(25))

    assertEquals(PodcastClusteringStatus.SUCCESS, first.status)
    assertTrue(first.groups.all { it.size == 1 })
    assertEquals(listOf(listOf(0, 1), listOf(2)), last.groups)
    assertEquals(PodcastClusteringStatus.SKIPPED, fresh.status)
    assertEquals(PodcastClusteringStatus.SKIPPED, repeated.status)
    assertEquals(1, embeddingCalls)

    capturedThreshold = 0.70 // Existing session remains independent of later UI edits.
    assertEquals(listOf(listOf(0, 1), listOf(2)), prepared.cluster(listOf(24, 25, 26)).groups)
    assertEquals(1, embeddingCalls)
  }

  private fun entry(id: String) = PodcastFeedEntry(id, "source", "headline $id", null, null, null, "body $id")
}

private fun <T> runSuspend(block: suspend () -> T): T {
  var value: Result<T>? = null
  block.startCoroutine(object : Continuation<T> {
    override val context = EmptyCoroutineContext
    override fun resumeWith(result: Result<T>) { value = result }
  })
  return requireNotNull(value).getOrThrow()
}
