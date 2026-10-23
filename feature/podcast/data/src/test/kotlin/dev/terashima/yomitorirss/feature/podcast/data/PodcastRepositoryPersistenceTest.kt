package dev.terashima.yomitorirss.feature.podcast.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.podcast.PodcastChapterGenerationStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastClusteringMode
import dev.terashima.yomitorirss.feature.podcast.PodcastClusteringStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastEpisodeStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTaskState
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
      DatabaseSchema(version = 42, contributions = listOf(podcastDatabaseSchema)),
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
  fun `database version 41を42へ更新して旧再生成columnだけを削除する`() {
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
    val previousSchema = DatabaseSchema(
      version = 41,
      contributions = listOf(
        DatabaseSchemaContribution(
          owner = "podcast",
          createSchema = { db ->
            db.execSQL(
              "CREATE TABLE podcast_episodes(" +
                "id TEXT PRIMARY KEY NOT NULL,program_id TEXT NOT NULL," +
                "title TEXT NOT NULL,created_at INTEGER NOT NULL,status TEXT NOT NULL," +
                "script TEXT,error_message TEXT,regeneration_status TEXT,clustering_status TEXT," +
                "clustering_mode TEXT NOT NULL DEFAULT 'EVENT',last_played_chapter INTEGER)",
            )
            db.execSQL(
              "INSERT INTO podcast_episodes(id,program_id,title,created_at,status,regeneration_status) " +
                "VALUES('episode','program','保存済み原稿',100,'READY',NULL)",
            )
          },
        ),
      ),
    )
    YomitoriDatabase.create(context, previousSchema).close()
    database = YomitoriDatabase.create(
      context,
      DatabaseSchema(version = 42, contributions = listOf(podcastDatabaseSchema)),
    )
    repository = SqlitePodcastRepository(DatabaseConnection(database))
    assertEquals(42, database.readableDatabase.version)
    database.readableDatabase.rawQuery("PRAGMA table_info(podcast_episodes)", null).use { cursor ->
      val nameColumn = cursor.getColumnIndexOrThrow("name")
      val columns = buildSet {
        while (cursor.moveToNext()) add(cursor.getString(nameColumn))
      }
      assertTrue("regeneration_status" !in columns)
      assertTrue("last_played_chapter" in columns)
      assertTrue("clustering_mode" in columns)
    }
    database.readableDatabase.rawQuery(
      "SELECT title,status FROM podcast_episodes WHERE id='episode'",
      null,
    ).use { cursor ->
      assertTrue(cursor.moveToFirst())
      assertEquals("保存済み原稿", cursor.getString(0))
      assertEquals("READY", cursor.getString(1))
    }
  }

  @Test
  fun `同一記事から別モード比較版を作っても消費履歴と原本は維持する`() = runSuspend {
    val source = PodcastSource("source", "ニュース", "https://example.invalid/feed")
    val program = PodcastProgram("program", "比較番組", setOf(source.id), PodcastGenerationProvider.LOCAL)
    repository.saveSource(source)
    repository.saveProgram(program)
    val articles = listOf(
      PodcastFeedEntry("article-a", source.id, "記事A", null, 10L, null, "本文A", chapterPosition = 0),
      PodcastFeedEntry("article-b", source.id, "記事B", null, 11L, null, "本文B", chapterPosition = 1),
    )
    val original = requireNotNull(repository.reserveEpisode(program, articles, 100L))
    val comparison = repository.reserveComparisonEpisode(
      program.copy(clusteringMode = PodcastClusteringMode.TOPIC),
      articles.map { it.copy(chapterPosition = 0) },
      200L,
      PodcastClusteringStatus.SUCCESS,
    )
    assertTrue(original.id != comparison.id)
    assertEquals(PodcastClusteringMode.EVENT, repository.findEpisode(original.id)?.clusteringMode)
    assertEquals(PodcastClusteringMode.TOPIC, repository.findEpisode(comparison.id)?.clusteringMode)
    assertEquals(listOf(0, 0), comparison.articles.map { it.chapterPosition })
    assertEquals(listOf(0, 1), repository.findEpisode(original.id)?.articles?.map { it.chapterPosition })
    val filter = SqlitePodcastCandidateFilter(DatabaseConnection(database))
    assertTrue(filter.availableEntries(program.id, articles).isEmpty())
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
  fun `直近アーカイブの記事参照は番組と作成日時と状態で限定する`() = runSuspend {
    val source = PodcastSource("source-1", "ニュース", "https://example.invalid/feed.xml")
    val first = PodcastProgram("program-1", "朝のニュース", setOf(source.id), PodcastGenerationProvider.LOCAL)
    val other = first.copy(id = "program-2")
    repository.saveSource(source)
    repository.saveProgram(first)
    repository.saveProgram(other)

    suspend fun saveEpisode(program: PodcastProgram, id: String, createdAt: Long, archive: Boolean) {
      val episode = requireNotNull(
        repository.reserveEpisode(
          program,
          listOf(PodcastFeedEntry(id, source.id, "記事 $id", "情報源", createdAt, "https://example.invalid/$id", "本文 $id")),
          createdAt,
        ),
      )
      repository.completeChapter(episode.id, 0, "原稿")
      repository.completeEpisode(episode.id, "完成", "原稿")
      if (archive) repository.archiveEpisode(episode.id)
    }

    saveEpisode(first, "old", 100L, archive = true)
    saveEpisode(first, "recent", 300L, archive = true)
    saveEpisode(first, "ready", 400L, archive = false)
    saveEpisode(other, "another-program", 350L, archive = true)

    val current = repository.listRecentArchivedArticles(first.id, 200L)
    assertEquals(listOf("recent"), current.map { it.articleId })
    assertEquals("https://example.invalid/recent", current.single().articleUrl)
    assertEquals("本文 recent", current.single().feedContent)
    assertEquals(listOf("recent", "old"), repository.listRecentArchivedArticles(first.id, 0L).map { it.articleId })
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
    repository.recordPlaybackChapter(reserved.id, 2)
    assertEquals(2, repository.findEpisode(reserved.id)?.lastPlayedChapter)

    val archived = repository.archiveEpisode(reserved.id)
    assertEquals(PodcastEpisodeStatus.ARCHIVED, archived.status)
    assertNull(archived.lastPlayedChapter)
    repository.recordPlaybackChapter(reserved.id, 3)
    assertNull(repository.findEpisode(reserved.id)?.lastPlayedChapter)
    assertEquals("原稿", archived.script)
    assertEquals(1, archived.articles.size)

    val restored = repository.restoreEpisode(reserved.id)
    assertEquals(PodcastEpisodeStatus.READY, restored.status)
    assertNull(restored.lastPlayedChapter)
    repository.recordPlaybackChapter(reserved.id, 1)
    assertEquals(1, repository.findEpisode(reserved.id)?.lastPlayedChapter)

    repository.deleteEpisode(reserved.id)
    val deleted = requireNotNull(repository.findEpisode(reserved.id))
    assertEquals(PodcastEpisodeStatus.DELETED, deleted.status)
    assertNull(deleted.lastPlayedChapter)
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
  fun `通常ニュースの後に既報だけのエピソードを予約し再開してもタイトルを保持する`() = runSuspend {
    val source = PodcastSource("source-1", "ニュース", "https://example.invalid/feed.xml")
    val program = PodcastProgram(
      id = "program-1", name = "朝のニュース", sourceIds = setOf(source.id),
      provider = PodcastGenerationProvider.LOCAL, maxArticlesPerEpisode = 2,
    )
    repository.saveSource(source)
    repository.saveProgram(program)
    fun article(id: String, chapter: Int) = PodcastFeedEntry(
      articleId = id, feedId = source.id, title = "記事 $id",
      sourceTitle = source.name, publishedAtEpochMillis = 10L,
      articleUrl = null, feedContent = "本文 $id", chapterPosition = chapter,
    )
    val first = requireNotNull(repository.reserveEpisode(
      program = program,
      candidates = listOf(article("fresh-1", 0), article("fresh-2", 1), article("fresh-3", 2)),
      createdAtEpochMillis = 1_000L,
      clusteringStatus = PodcastClusteringStatus.SUCCESS,
      repeatedCandidates = listOf(article("repeat-1", 0), article("repeat-2", 1)),
      repeatedClusteringStatus = PodcastClusteringStatus.SUCCESS,
    ))
    assertEquals(listOf("fresh-1", "fresh-2"), first.articles.map { it.articleId })
    val snapshots = repository.listEpisodes(program.id).sortedBy { it.createdAtEpochMillis }
    assertEquals(listOf(2, 1, 2), snapshots.map { it.articles.size })
    assertEquals(
      listOf(PodcastEpisodeStatus.GENERATING, PodcastEpisodeStatus.QUEUED, PodcastEpisodeStatus.QUEUED),
      snapshots.map { it.status },
    )
    assertEquals(listOf(false, false, true), snapshots.map { it.title.contains("既報ニュース") })
    assertEquals(listOf(0, 1), snapshots[2].articles.map { it.chapterPosition })
    assertEquals(PodcastClusteringStatus.SUCCESS, snapshots[2].clusteringStatus)
    assertEquals(5, snapshots.flatMap { it.articles }.map { it.articleId }.toSet().size)

    val filtered = SqlitePodcastCandidateFilter(DatabaseConnection(database))
    assertTrue(filtered.availableEntries(program.id, listOf(article("repeat-1", 0))).isEmpty())
    assertNull(repository.reserveEpisode(program, emptyList(), 2_000L, null, listOf(article("repeat-1", 0)), null))

    repository.completeChapter(first.id, 0, "原稿1")
    repository.completeChapter(first.id, 1, "原稿2")
    repository.completeEpisode(first.id, first.title, "完成原稿")
    val second = requireNotNull(repository.claimPendingEpisode(program.id))
    assertEquals(listOf("fresh-3"), second.articles.map { it.articleId })
    repository.completeChapter(second.id, 0, "原稿")
    repository.completeEpisode(second.id, second.title, "完成原稿")
    val repeated = requireNotNull(repository.claimPendingEpisode(program.id))
    assertEquals(listOf("repeat-1", "repeat-2"), repeated.articles.map { it.articleId })
    assertTrue(repeated.title.contains("既報ニュース"))
    assertEquals(repeated.title, repository.findEpisode(repeated.id)?.title)
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
    repository.recordPlaybackChapter(episode.id, 2)

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
    assertNull(rebuilding.lastPlayedChapter)
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
