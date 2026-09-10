package dev.terashima.yomitorirss.feature.podcast

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastRegenerationTest {
  @Test
  fun `生成済みエピソードは保存済み記事から再生成する`() = runSuspend {
    val repository = RegenerationRepository()
    val feedSource = RecordingFeedSource()
    val generator = RegenerationGenerator(result = "新しい原稿")
    val useCase = GeneratePodcastEpisodeUseCase(repository, feedSource, generator)

    val regenerated = useCase.regenerate(repository.episode.id).episode

    assertEquals(PodcastEpisodeStatus.READY, regenerated.status)
    assertEquals("新しい原稿", regenerated.script)
    assertEquals(repository.episode.id, regenerated.id)
    assertEquals(0, feedSource.requestCount)
    assertTrue(generator.prompt.contains("保存済み本文"))
  }

  @Test
  fun `生成済みエピソードの再生成に失敗しても既存原稿を保持する`() = runSuspend {
    val repository = RegenerationRepository()
    val feedSource = RecordingFeedSource()
    val useCase = GeneratePodcastEpisodeUseCase(
      repository = repository,
      feedContentSource = feedSource,
      scriptGenerator = RegenerationGenerator(error = IllegalStateException("generation failed")),
    )

    val error = runCatching { useCase.regenerate(repository.episode.id) }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertEquals(PodcastEpisodeStatus.READY, repository.episode.status)
    assertEquals("既存原稿", repository.episode.script)
    assertFalse(repository.failCalled)
    assertEquals(0, feedSource.requestCount)
  }
}

private class RegenerationRepository : PodcastRepository {
  private val program = PodcastProgram(
    id = "program-1",
    name = "朝のニュース",
    sourceIds = setOf("source-1"),
    provider = PodcastGenerationProvider.LOCAL,
  )
  var episode = PodcastEpisode(
    id = "episode-1",
    programId = program.id,
    title = "朝のニュース / 9/10 07:00",
    createdAtEpochMillis = 1_000L,
    status = PodcastEpisodeStatus.READY,
    articles = listOf(
      PodcastEpisodeArticle(
        articleId = "article-1",
        feedId = "source-1",
        title = "記事タイトル",
        sourceTitle = "情報源",
        publishedAtEpochMillis = 500L,
        articleUrl = "https://example.invalid/article",
        feedContent = "保存済み本文",
      ),
    ),
    script = "既存原稿",
  )
  var failCalled = false

  override suspend fun findProgram(programId: String): PodcastProgram? = program.takeIf { it.id == programId }
  override suspend fun findEpisode(episodeId: String): PodcastEpisode? = episode.takeIf { it.id == episodeId }
  override suspend fun archiveEpisode(episodeId: String): PodcastEpisode = error("unused")
  override suspend fun restoreEpisode(episodeId: String): PodcastEpisode = error("unused")
  override suspend fun deleteEpisode(episodeId: String) = error("unused")
  override suspend fun completeEpisode(episodeId: String, title: String, script: String): PodcastEpisode {
    check(episode.id == episodeId)
    episode = episode.copy(title = title, script = script, errorMessage = null, status = PodcastEpisodeStatus.READY)
    return episode
  }
  override suspend fun failEpisode(episodeId: String, message: String): PodcastEpisode {
    failCalled = true
    episode = episode.copy(errorMessage = message, status = PodcastEpisodeStatus.FAILED)
    return episode
  }

  override suspend fun listSources(): List<PodcastSource> = emptyList()
  override suspend fun findSource(sourceId: String): PodcastSource? = null
  override suspend fun saveSource(source: PodcastSource) = error("unused")
  override suspend fun deleteSource(sourceId: String) = error("unused")
  override suspend fun listPrograms(): List<PodcastProgram> = listOf(program)
  override suspend fun saveProgram(program: PodcastProgram) = error("unused")
  override suspend fun deleteProgram(programId: String) = error("unused")
  override suspend fun listEpisodes(programId: String): List<PodcastEpisode> = listOf(episode)
  override suspend fun claimPendingEpisode(programId: String): PodcastEpisode? = null
  override suspend fun reserveEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
  ): PodcastEpisode? = error("unused")
}

private class RecordingFeedSource : PodcastFeedContentSource {
  var requestCount = 0

  override suspend fun latestEntries(sources: List<PodcastSource>, limit: Int): List<PodcastFeedEntry> {
    requestCount += 1
    return emptyList()
  }
}

private class RegenerationGenerator(
  private val result: String = "",
  private val error: Throwable? = null,
) : PodcastScriptGenerator {
  var prompt: String = ""

  override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String {
    this.prompt = prompt
    error?.let { throw it }
    return result
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