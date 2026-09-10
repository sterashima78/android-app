package dev.terashima.yomitorirss.feature.podcast

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastInterruptedRecoveryTest {
  @Test
  fun `中断episodeは新しいfeedを取得せず同じsnapshotから再開する`() = runSuspend {
    val repository = RecoveryPodcastRepository(
      episodeStatus = PodcastEpisodeStatus.GENERATING,
    )
    val feedSource = RecordingRecoveryFeedSource()
    val generator = RecordingRecoveryGenerator()
    val useCase = GeneratePodcastEpisodeUseCase(repository, feedSource, generator)

    val resumed = useCase.resumeInterrupted(repository.program.id)

    requireNotNull(resumed)
    assertEquals(repository.episodeId, resumed.episode.id)
    assertEquals(PodcastEpisodeStatus.READY, resumed.episode.status)
    assertEquals("生成された原稿", resumed.episode.script)
    assertEquals(0, feedSource.requestCount)
    assertTrue(generator.prompt.contains("保存済み本文"))
    assertTrue(!generator.prompt.contains("https://"))
  }

  @Test
  fun `中断episodeがなければ新しいepisodeを予約しない`() = runSuspend {
    val repository = RecoveryPodcastRepository(
      episodeStatus = PodcastEpisodeStatus.READY,
    )
    val feedSource = RecordingRecoveryFeedSource()
    val generator = RecordingRecoveryGenerator()
    val useCase = GeneratePodcastEpisodeUseCase(repository, feedSource, generator)

    val resumed = useCase.resumeInterrupted(repository.program.id)

    assertNull(resumed)
    assertEquals(0, feedSource.requestCount)
    assertEquals(0, generator.requestCount)
    assertEquals(1, repository.episodes.size)
  }
}

private class RecoveryPodcastRepository(
  episodeStatus: PodcastEpisodeStatus,
) : PodcastRepository {
  val program = PodcastProgram(
    id = "program-1",
    name = "朝のニュース",
    sourceIds = setOf("source-1"),
    provider = PodcastGenerationProvider.LOCAL,
  )
  val episodeId = "episode-1"
  val episodes = mutableListOf(
    PodcastEpisode(
      id = episodeId,
      programId = program.id,
      title = program.name,
      createdAtEpochMillis = 100L,
      status = episodeStatus,
      articles = listOf(
        PodcastEpisodeArticle(
          articleId = "article-1",
          feedId = "source-1",
          title = "保存済み記事",
          sourceTitle = "情報源",
          publishedAtEpochMillis = 10L,
          articleUrl = "https://example.invalid/article-1",
          feedContent = "保存済み本文",
        ),
      ),
      script = if (episodeStatus == PodcastEpisodeStatus.READY) "既存原稿" else null,
    ),
  )

  override suspend fun listSources(): List<PodcastSource> = emptyList()
  override suspend fun findSource(sourceId: String): PodcastSource? = null
  override suspend fun saveSource(source: PodcastSource) = Unit
  override suspend fun deleteSource(sourceId: String) = Unit
  override suspend fun listPrograms(): List<PodcastProgram> = listOf(program)
  override suspend fun findProgram(programId: String): PodcastProgram? = program.takeIf { it.id == programId }
  override suspend fun saveProgram(program: PodcastProgram) = Unit
  override suspend fun deleteProgram(programId: String) = Unit
  override suspend fun listEpisodes(programId: String): List<PodcastEpisode> =
    episodes.filter { it.programId == programId }
  override suspend fun findEpisode(episodeId: String): PodcastEpisode? = episodes.find { it.id == episodeId }
  override suspend fun claimPendingEpisode(programId: String): PodcastEpisode? = error("not used")
  override suspend fun reserveEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
  ): PodcastEpisode? = error("not used")

  override suspend fun completeEpisode(episodeId: String, title: String, script: String): PodcastEpisode =
    updateEpisode(episodeId) {
      it.copy(
        title = title,
        status = PodcastEpisodeStatus.READY,
        script = script,
        errorMessage = null,
      )
    }

  override suspend fun failEpisode(episodeId: String, message: String): PodcastEpisode =
    updateEpisode(episodeId) {
      it.copy(status = PodcastEpisodeStatus.FAILED, errorMessage = message)
    }

  private fun updateEpisode(
    episodeId: String,
    transform: (PodcastEpisode) -> PodcastEpisode,
  ): PodcastEpisode {
    val index = episodes.indexOfFirst { it.id == episodeId }
    check(index >= 0)
    return transform(episodes[index]).also { episodes[index] = it }
  }
}

private class RecordingRecoveryFeedSource : PodcastFeedContentSource {
  var requestCount = 0

  override suspend fun latestEntries(
    sources: List<PodcastSource>,
    limit: Int,
  ): List<PodcastFeedEntry> {
    requestCount += 1
    return emptyList()
  }
}

private class RecordingRecoveryGenerator : PodcastScriptGenerator {
  var requestCount = 0
  var prompt = ""

  override suspend fun generate(
    provider: PodcastGenerationProvider,
    prompt: String,
  ): String {
    requestCount += 1
    this.prompt = prompt
    return "生成された原稿"
  }
}

private fun runSuspend(block: suspend () -> Unit) {
  var result: Result<Unit>? = null
  block.startCoroutine(object : Continuation<Unit> {
    override val context = EmptyCoroutineContext
    override fun resumeWith(value: Result<Unit>) {
      result = value
    }
  })
  result?.getOrThrow() ?: error("suspending test did not complete synchronously")
}
