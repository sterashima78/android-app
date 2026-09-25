package dev.terashima.yomitorirss.feature.podcast.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.podcast.PodcastChapterGenerationStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastClusteringStatus
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
      DatabaseSchema(version = 39, contributions = listOf(podcastDatabaseSchema)),
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
  fun `番組の除外条件と除外済みentryを永続化する`() = runSuspend {
    val source = PodcastSource("source-1", "ニュース", "https://example.invalid/feed.xml")
    val program = PodcastProgram(
      id = "program-1",
      name = "朝のニュース",
      sourceIds = setOf(source.id),
      provider = PodcastGenerationProvider.LOCAL,
      exclusionPrompt = "カテゴリAの記事は除外する",
    )
    val entry = PodcastFeedEntry(
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
    val candidateFilter = SqlitePodcastCandidateFilter(DatabaseConnection(database))

    assertEquals(program, repository.listPrograms().single())
    assertEquals(listOf(entry), candidateFilter.availableEntries(program.id, listOf(entry)))

    repository.recordExcludedEntries(program.id, listOf(entry), 100L)

    assertTrue(candidateFilter.availableEntries(program.id, listOf(entry)).isEmpty())
    assertNull(repository.reserveEpisode(program, listOf(entry), 200L))
  }

  @Test
  fun `database version 38から39へ除外設定schemaを移行する`() = runSuspend {
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
    val legacySchema = DatabaseSchema(
      version = 38,
      contributions = listOf(
        DatabaseSchemaContribution(
          owner = "podcast",
          createSchema = { db ->
            db.execSQL(
              "CREATE TABLE podcast_programs(" +
                "id TEXT PRIMARY KEY NOT NULL," +
                "name TEXT NOT NULL," +
                "source_ids TEXT NOT NULL," +
                "provider TEXT NOT NULL," +
                "schedule_enabled INTEGER NOT NULL DEFAULT 0," +
                "schedule_hour INTEGER NOT NULL DEFAULT 7," +
                "schedule_minute INTEGER NOT NULL DEFAULT 0," +
                "max_articles INTEGER NOT NULL DEFAULT 12" +
                ")",
            )
            db.execSQL(
              "INSERT INTO podcast_programs(id,name,source_ids,provider,max_articles) VALUES(?,?,?,?,?)",
              arrayOf<Any>("program-1", "番組", "source-1", PodcastGenerationProvider.LOCAL.name, 12),
            )
          },
        ),
      ),
    )
    YomitoriDatabase.create(context, legacySchema).close()

    database = YomitoriDatabase.create(
      context,
      DatabaseSchema(version = 39, contributions = listOf(podcastDatabaseSchema)),
    )
    repository = SqlitePodcastRepository(DatabaseConnection(database))

    val migrated = repository.listPrograms().single()
    assertEquals("", migrated.exclusionPrompt)
    repository.recordExcludedEntries(
      migrated.id,
      listOf(
        PodcastFeedEntry(
          articleId = "source-1:article-1",
          feedId = "source-1",
          title = "記事",
          sourceTitle = null,
          publishedAtEpochMillis = null,
          articleUrl = "https://example.invalid/article-1",
          feedContent = "本文",
        ),
      ),
      100L,
    )
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
  fun `ニュース分類の診断状態をエピソードへ永続化する`() = runSuspend {
    val source = PodcastSource("source-1", "ニュース", "https://example.invalid/feed.xml")
    val program = PodcastProgram(
      id = "program-1",
      name = "朝のニュース",
      sourceIds = setOf(source.id),
      provider = PodcastGenerationProvider.LOCAL,
    )
    repository.saveSource(source)
    repository.saveProgram(program)

    val episode = requireNotNull(
      repository.reserveEpisode(
        program = program,
        candidates = listOf(
          PodcastFeedEntry("article-1", source.id, "記事1", source.name, 1L, "https://example.invalid/1", "本文1", chapterPosition = 0),
          PodcastFeedEntry("article-2", source.id, "記事2", source.name, 2L, "https://example.invalid/2", "本文2", chapterPosition = 0),
          PodcastFeedEntry("article-3", source.id, "記事3", source.name, 3L, "https://example.invalid/3", "本文3", chapterPosition = 1),
        ),
        createdAtEpochMillis = 100L,
        clusteringStatus = PodcastClusteringStatus.SUCCESS,
      ),
    )

    val persisted = requireNotNull(repository.findEpisode(episode.id))
    assertEquals(PodcastClusteringStatus.SUCCESS, persisted.clusteringStatus)
    assertEquals(3, persisted.articles.size)
    assertEquals(2, persisted.chapterGroups().size)
    assertEquals(listOf(2, 1), persisted.chapterGroups().map { it.size })
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
  fun `エピソード作り直しは同じIDで記事snapshotとclusterを置き換える`() = runSuspend {
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
          PodcastFeedEntry("article-2", source.id, "記事2", source.name, 2L, "https://example.invalid/2", "本文2"),
        ),
        createdAtEpochMillis = 100L,
      ),
    )
    repository.completeChapter(episode.id, 0, "既存原稿1")
    repository.completeChapter(episode.id, 1, "既存原稿2")
    repository.completeEpisode(episode.id, "朝のニュース / 1/1 07:00", "既存原稿")

    val rebuilding = repository.prepareEpisodeRebuild(
      episodeId = episode.id,
      candidates = listOf(
        PodcastFeedEntry(
          articleId = "article-2",
          feedId = source.id,
          title = "記事2",
          sourceTitle = source.name,
          publishedAtEpochMillis = 2L,
          articleUrl = null,
          feedContent = "本文2",
          chapterPosition = 0,
        ),
      ),
      clusteringStatus = PodcastClusteringStatus.SUCCESS,
    )

    assertEquals(episode.id, rebuilding.id)
    assertEquals(PodcastEpisodeStatus.GENERATING, rebuilding.status)
    assertNull(rebuilding.script)
    assertNull(rebuilding.regenerationStatus)
    assertEquals(PodcastClusteringStatus.SUCCESS, rebuilding.clusteringStatus)
    assertEquals(listOf("article-2"), rebuilding.articles.map { it.articleId })
    assertEquals(0, rebuilding.articles.single().chapterPosition)
    assertNull(rebuilding.articles.single().articleUrl)
    assertEquals(PodcastChapterGenerationStatus.PENDING, rebuilding.articles.single().chapterStatus)
    assertTrue(runCatching { repository.archiveEpisode(episode.id) }.exceptionOrNull() is IllegalArgumentException)
    assertTrue(runCatching { repository.deleteEpisode(episode.id) }.exceptionOrNull() is IllegalArgumentException)
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
