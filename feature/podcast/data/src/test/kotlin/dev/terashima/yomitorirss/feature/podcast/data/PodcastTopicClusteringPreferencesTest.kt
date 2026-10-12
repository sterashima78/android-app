package dev.terashima.yomitorirss.feature.podcast.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.feature.podcast.DEFAULT_PODCAST_TOPIC_SIMILARITY_THRESHOLD
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PodcastTopicClusteringPreferencesTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("podcast_topic_clustering", Context.MODE_PRIVATE)
      .edit().clear().commit()
  }

  @Test
  fun `restored preference immediately updates the observed threshold`() {
    val settings = PodcastTopicClusteringPreferences(context)
    context.getSharedPreferences("podcast_topic_clustering", Context.MODE_PRIVATE)
      .edit().putInt("similarity_threshold_percent", 94).commit()
    assertEquals(0.94, settings.similarityThreshold.value, 0.0)
  }

  @Test
  fun `threshold survives recreation and reset restores default`() {
    val first = PodcastTopicClusteringPreferences(context)
    assertEquals(DEFAULT_PODCAST_TOPIC_SIMILARITY_THRESHOLD, first.similarityThreshold.value, 0.0)

    first.updateSimilarityThreshold(0.91)
    assertEquals(0.91, PodcastTopicClusteringPreferences(context).similarityThreshold.value, 0.0)

    first.updateSimilarityThreshold(DEFAULT_PODCAST_TOPIC_SIMILARITY_THRESHOLD)
    assertEquals(DEFAULT_PODCAST_TOPIC_SIMILARITY_THRESHOLD, PodcastTopicClusteringPreferences(context).similarityThreshold.value, 0.0)
    assertEquals(
      false,
      context.getSharedPreferences("podcast_topic_clustering", Context.MODE_PRIVATE)
        .contains("similarity_threshold_percent"),
    )
  }
}
