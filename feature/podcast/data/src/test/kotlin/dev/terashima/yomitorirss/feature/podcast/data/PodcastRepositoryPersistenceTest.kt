package dev.terashima.yomitorirss.feature.podcast.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.podcast.PodcastChapterGenerationStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastEpisodeStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTaskState
import dev.terashima.yomitorirss.feature.podcast.PodcastProgram
import dev.terashima.yomitorirss.feature.podcast.PodcastRegenerationStatus
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
      DatabaseSchema(version = 36, contributions = listOf(podcastDatabaseSchema)),
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
    repository.completeChapter(reserved.id, 0, "原稿")
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
    assertTrue(repository.listGenerationTasks().isEmpty())
  }

  @Test
  fun `チャプターcheckpointとAIタスク進捗を永続化する`() = runSuspend {
    val source = PodcastSource("source-1", "ニュース", "https://example.invalid/feed.xml")
    repository.saveSource(source)
    val program = PodcastProgram(
      id = "program-1",
      name = "朝のニュース",
      sourceIds = setOf(source.id),
      provider = PodcastGenerationProvider.CLOUD,
    )
    repository.saveProgram(program)
    val episode = requireNotNull(
      repository.reserveEpisode(
        program = program,
        candidates = listOf(
          PodcastFeedEntry("article-1", source.id, "記事1", source.name, 1L, "https://example.invalid/1", "本文1"),
          PodcastFeedEntry("article-2", source.id, "記事2", source.name, 2L, "https://example.invalid/2", "本文2"),
        ),
        createdAtEpochMillis = 100L,
      ),
    )

    repository.markChapterGenerating(episode.id, 0)
    repository.completeChapter(episode.id, 0, "1件目の原稿")

    val persisted = requireNotNull(repository.findEpisode(episode.id))
    assertEquals(PodcastChapterGenerationStatus.READY, persisted.articles[0].chapterStatus)
    assertEquals("1件目の原稿", persisted.articles[0].chapterScript)
    assertEquals(PodcastChapterGenerationStatus.PENDING, persisted.articles[1].chapterStatus)

    val task = repository.listGenerationTasks().single()
    assertEquals(PodcastGenerationTaskState.RUNNING, task.state)
    assertEquals(1, task.completedChapters)
    assertEquals(2, task.totalChapters)
    assertEquals(PodcastGenerationProvider.CLOUD, task.provider)
  }

  @Test
  fun `再生成中は重複再生成とアーカイブ削除を拒否し失敗後のアーカイブでタスクを閉じる`() = runSuspend {
    val source = PodcastSource("source-1", "ニュース", "https://example.invalid/feed.xml")
    val program = PodcastProgram(
      id = "program-1",
      name = "朝のニュース",
      sourceIds = setOf(source.id),
      provider = PodcastGenerationProvider.CLOUD,
    )
    repository.saveSource(source)
    repository.saveProgram(program)
    val episode = requireNotNull(
      repository.reserveEpisode(
        program = program,
        candidates = listOf(
          PodcastFeedEntry("article-1", source.id, "記事1", source.name, 1L, "https://example.invalid/1", "本文1"),
        ),
        createdAtEpochMillis = 100L,
      ),
    )
    repository.completeChapter(episode.id, 0, "既存原稿")
    repository.completeEpisode(episode.id, "朝のニュース / 1/1 07:00", "既存原稿")

    val regenerating = repository.prepareEpisodeRegeneration(episode.id)
    assertEquals(PodcastRegenerationStatus.RUNNING, regenerating.regenerationStatus)
    assertTrue(runCatching { repository.prepareEpisodeRegeneration(episode.id) }.exceptionOrNull() is IllegalArgumentException)
    assertTrue(runCatching { repository.archiveEpisode(episode.id) }.exceptionOrNull() is IllegalArgumentException)
    assertTrue(runCatching { repository.deleteEpisode(episode.id) }.exceptionOrNull() is IllegalArgumentException)
    assertEquals(PodcastEpisodeStatus.READY, requireNotNull(repository.findEpisode(episode.id)).status)

    repository.failRegeneration(episode.id, "再生成に失敗")
    val failedTask = repository.listGenerationTasks().single()
    assertEquals(PodcastGenerationTaskState.FAILED, failedTask.state)

    val archived = repository.archiveEpisode(episode.id)
    assertEquals(PodcastEpisodeStatus.ARCHIVED, archived.status)
    assertNull(archived.regenerationStatus)
    assertNull(archived.errorMessage)
    assertTrue(repository.listGenerationTasks().isEmpty())
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
