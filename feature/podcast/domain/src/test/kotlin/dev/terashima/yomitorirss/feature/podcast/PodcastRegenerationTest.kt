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
    assertTrue(regenerated.script.orEmpty().contains("新しい原稿"))
    assertEquals(null, regenerated.regenerationStatus)
    assertEquals(PodcastChapterGenerationStatus.READY, regenerated.articles.single().chapterStatus)
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
    assertEquals(PodcastRegenerationStatus.FAILED, repository.episode.regenerationStatus)
    assertEquals(PodcastChapterGenerationStatus.FAILED, repository.episode.articles.single().chapterStatus)
    assertFalse(repository.failEpisodeCalled)
    assertEquals(0, feedSource.requestCount)
  }

  @Test
  fun `再生成失敗後の再実行は完成済みチャプターを保持する`() = runSuspend {
    val repository = RegenerationRepository(
      articles = listOf(
        article("article-1", "本文1"),
        article("article-2", "本文2"),
      ),
    )
    val feedSource = RecordingFeedSource()
    val firstGenerator = RegenerationSequenceGenerator(listOf("新1", null))
    val useCase = GeneratePodcastEpisodeUseCase(repository, feedSource, firstGenerator)

    runCatching { useCase.regenerate(repository.episode.id) }
    assertEquals(PodcastChapterGenerationStatus.READY, repository.episode.articles[0].chapterStatus)
    assertEquals(PodcastChapterGenerationStatus.FAILED, repository.episode.articles[1].chapterStatus)

    val retryGenerator = RegenerationGenerator(result = "新2")
    val retried = GeneratePodcastEpisodeUseCase(repository, feedSource, retryGenerator)
      .regenerate(repository.episode.id).episode

    assertFalse(retryGenerator.prompt.contains("本文1"))
    assertTrue(retryGenerator.prompt.contains("本文2"))
    assertTrue(retried.script.orEmpty().contains("新1"))
    assertTrue(retried.script.orEmpty().contains("新2"))
    assertEquals(null, retried.regenerationStatus)
  }
}

private class RegenerationRepository(
  articles: List<PodcastEpisodeArticle> = listOf(article("article-1", "保存済み本文")),
) : PodcastRepository {
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
    articles = articles,
    script = "既存原稿",
  )
  var failEpisodeCalled = false

  override suspend fun findProgram(programId: String): PodcastProgram? = program.takeIf { it.id == programId }
  override suspend fun findEpisode(episodeId: String): PodcastEpisode? = episode.takeIf { it.id == episodeId }
  override suspend fun archiveEpisode(episodeId: String): PodcastEpisode = error("unused")
  override suspend fun restoreEpisode(episodeId: String): PodcastEpisode = error("unused")
  override suspend fun deleteEpisode(episodeId: String) = error("unused")

  override suspend fun prepareEpisodeRetry(episodeId: String): PodcastEpisode {
    episode = episode.copy(status = PodcastEpisodeStatus.GENERATING, errorMessage = null, regenerationStatus = null)
    return episode
  }

  override suspend fun prepareEpisodeRegeneration(episodeId: String): PodcastEpisode {
    check(episode.id == episodeId)
    val preparedArticles = if (episode.regenerationStatus == null) {
      episode.articles.map {
        it.copy(
          chapterStatus = PodcastChapterGenerationStatus.PENDING,
          chapterScript = null,
          chapterError = null,
        )
      }
    } else {
      episode.articles
    }
    episode = episode.copy(
      articles = preparedArticles,
      regenerationStatus = PodcastRegenerationStatus.RUNNING,
      errorMessage = null,
    )
    return episode
  }

  override suspend fun markChapterGenerating(episodeId: String, position: Int): PodcastEpisode =
    updateArticle(position) { it.copy(chapterStatus = PodcastChapterGenerationStatus.GENERATING, chapterError = null) }

  override suspend fun completeChapter(episodeId: String, position: Int, script: String): PodcastEpisode =
    updateArticle(position) {
      it.copy(
        chapterStatus = PodcastChapterGenerationStatus.READY,
        chapterScript = script,
        chapterError = null,
      )
    }

  override suspend fun failChapter(episodeId: String, position: Int, message: String): PodcastEpisode =
    updateArticle(position) {
      it.copy(chapterStatus = PodcastChapterGenerationStatus.FAILED, chapterError = message)
    }

  override suspend fun failRegeneration(episodeId: String, message: String): PodcastEpisode {
    episode = episode.copy(regenerationStatus = PodcastRegenerationStatus.FAILED, errorMessage = message)
    return episode
  }

  override suspend fun completeEpisode(episodeId: String, title: String, script: String): PodcastEpisode {
    check(episode.id == episodeId)
    episode = episode.copy(
      title = title,
      script = script,
      errorMessage = null,
      status = PodcastEpisodeStatus.READY,
      regenerationStatus = null,
    )
    return episode
  }

  override suspend fun failEpisode(episodeId: String, message: String): PodcastEpisode {
    failEpisodeCalled = true
    episode = episode.copy(errorMessage = message, status = PodcastEpisodeStatus.FAILED, regenerationStatus = null)
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

  private fun updateArticle(
    position: Int,
    transform: (PodcastEpisodeArticle) -> PodcastEpisodeArticle,
  ): PodcastEpisode {
    episode = episode.copy(
      articles = episode.articles.mapIndexed { index, article ->
        if (index == position) transform(article) else article
      },
    )
    return episode
  }
}

private fun article(id: String, body: String) = PodcastEpisodeArticle(
  articleId = id,
  feedId = "source-1",
  title = "記事タイトル $id",
  sourceTitle = "情報源",
  publishedAtEpochMillis = 500L,
  articleUrl = "https://example.invalid/article/$id",
  feedContent = body,
)

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

private class RegenerationSequenceGenerator(
  private val results: List<String?>,
) : PodcastScriptGenerator {
  private var index = 0

  override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String =
    results.getOrNull(index++) ?: error("generation failed")
}

private fun runSuspend(block: suspend () -> Unit) {
  var result: Result<Unit>? = null
  block.startCoroutine(object : Continuation<Unit> {
    override val context = EmptyCoroutineContext
    override fun resumeWith(value: Result<Unit>) { result = value }
  })
  result?.getOrThrow() ?: error("suspending test did not complete synchronously")
}
