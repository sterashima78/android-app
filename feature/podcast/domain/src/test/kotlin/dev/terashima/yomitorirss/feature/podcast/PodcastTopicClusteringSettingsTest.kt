package dev.terashima.yomitorirss.feature.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PodcastTopicClusteringSettingsTest {
  @Test
  fun `threshold is normalized to hundredths`() {
    assertEquals(0.78, normalizePodcastTopicSimilarityThreshold(0.78), 0.0)
    assertEquals(0.89, normalizePodcastTopicSimilarityThreshold(0.894), 0.0)
    assertEquals(0.99, normalizePodcastTopicSimilarityThreshold(0.99), 0.0)
  }

  @Test
  fun `non finite and out of range values are rejected`() {
    for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.69, 1.0)) {
      assertThrows(IllegalArgumentException::class.java) {
        normalizePodcastTopicSimilarityThreshold(value)
      }
    }
  }
}
