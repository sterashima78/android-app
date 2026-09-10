package dev.terashima.yomitorirss.feature.podcast.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.podcast.PodcastEpisodeStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastProgram
import dev.terashima.yomitorirss.feature.podcast.PodcastSource
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PodcastRepositoryPersistenceTest {
  private lateinit var context: Context
  private lateinit var database: YomitoriDatabase
  private lateinit var repository: SqlitePodcastRepository

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
    database = YomitoriDatabase.create(
      context,
      DatabaseSchema(version = 35, contributions = listOf(podcastDatabaseSchema)),
    )
    repository = SqlitePodcastRepository(DatabaseConnection(database))
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `Podcast sourceを保存し利用中は削除せず番組削除後に削除できる`() = runSuspend {
    val source = PodcastSource(
      id = "source-1",
      name = "ニュース",
      feedUrl = "https://example.invalid/feed.xml",
    )
    repository.saveSource(source)
    assertEquals(listOf(source), repository.listSources())

    repository.saveProgram(
      PodcastProgram(
        id = "program-1",
        name = "朝のニュース",
        sourceIds = setOf(source.id),
        provider = PodcastGenerationProvider.LOCAL,
      ),
    )

    val deleteError = runCatching { repository.deleteSource(source.id) }.exceptionOrNull()
    assertTrue(deleteError is IllegalArgumentException)
    assertEquals(listOf(source), repository.listSources())

    repository.deleteProgram("program-1")
    repository.deleteSource(source.id)
    assertTrue(repository.listSources().isEmpty())
  }

  @Test
  fun `存在しないPodcast sourceを参照する番組は保存しない`() = runSuspend {
    val error = runCatching {
      repository.saveProgram(
        PodcastProgram(
          id = "program-1",
          name = "朝のニュース",
          sourceIds = setOf("missing-source"),
          provider = PodcastGenerationProvider.LOCAL,
        ),
      )
    }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
    assertTrue(repository.listPrograms().isEmpty())
  }

  @Test
  fun `エピソードをアーカイブ復元削除しても消費済み記事は再利用しない`() = runSuspend {
    val source = PodcastSource(
      id = "source-1",
      name = "ニュース",
      feedUrl = "https://example.invalid/feed.xml",
    )
    val program = PodcastProgram(
      id = "program-1",
      name = "朝のニュース",
      sourceIds = setOf(source.id),
      provider = PodcastGenerationProvider.LOCAL,
    )
    val article = PodcastFeedEntry(
      articleId = "source-1:article-1",
      feedId = source.id,
      title = "記事",
      sourceTitle = source.name,
      publishedAtEpochMillis = 10L,
      articleUrl = "https://example.invalid/article-1",
      feedContent = "本文",
    )
    repository.saveSource(source)
    repository.saveProgram(program)
    val reserved = requireNotNull(repository.reserveEpisode(program, listOf(article), 100L))
    repository.completeEpisode(reserved.id, "朝のニュース / 1/1 07:00", "原稿")

    val archived = repository.archiveEpisode(reserved.id)
    assertEquals(PodcastEpisodeStatus.ARCHIVED, archived.status)
    assertEquals("原稿", archived.script)
    assertEquals(1, archived.articles.size)

    val restored = repository.restoreEpisode(reserved.id)
    assertEquals(PodcastEpisodeStatus.READY, restored.status)

    repository.deleteEpisode(reserved.id)
    val deleted = requireNotNull(repository.findEpisode(reserved.id))
    assertEquals(PodcastEpisodeStatus.DELETED, deleted.status)
    assertTrue(deleted.articles.isEmpty())
    assertNull(deleted.script)
    assertNull(repository.reserveEpisode(program, listOf(article), 200L))
  }
}

private fun runSuspend(block: suspend () -> Unit) {
  var result: Result<Unit>? = null
  block.startCoroutine(object : Continuation<Unit> {
    override val context = EmptyCoroutineContext
    override fun resumeWith(value: Result<Unit>) { result = value }
  })
  result?.getOrThrow() ?: error("suspending test did not complete synchronously")
}
