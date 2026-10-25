package dev.terashima.yomitorirss.feature.podcast.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationLog
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationLogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PodcastGenerationLogPreferencesTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.getSharedPreferences("podcast_generation_diagnostics", Context.MODE_PRIVATE)
      .edit().clear().commit()
  }

  @Test
  fun `ログは再生成したstoreでも読み取れて100件を超えると古い順に破棄される`() {
    val store = PodcastGenerationLogPreferences(context)
    repeat(110) { index ->
      store.record(PodcastGenerationLog(
        programId = "program-a",
        recordedAtEpochMillis = index.toLong(),
        level = PodcastGenerationLogLevel.WARNING,
        message = "比較失敗 ${index}",
      ))
    }

    val restored = PodcastGenerationLogPreferences(context).logs.value
    assertEquals(100, restored.size)
    assertEquals(109L, restored.first().recordedAtEpochMillis)
    assertEquals(10L, restored.last().recordedAtEpochMillis)
    assertTrue(restored.all { it.level == PodcastGenerationLogLevel.WARNING })
  }

  @Test
  fun `特定番組のログだけ削除する`() {
    val store = PodcastGenerationLogPreferences(context)
    store.record(PodcastGenerationLog("program-a", 1, PodcastGenerationLogLevel.INFO, "開始"))
    store.record(PodcastGenerationLog("program-b", 2, PodcastGenerationLogLevel.ERROR, "失敗"))
    store.clear("program-a")

    assertEquals(listOf("program-b"), store.logs.value.map { it.programId })
    assertEquals(listOf("program-b"), PodcastGenerationLogPreferences(context).logs.value.map { it.programId })
  }
}
