package dev.terashima.yomitorirss.feature.podcast

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastRegenerationTest {
  @Test
  fun `再生成は保存済み記事へ現在の除外条件を適用して作り直す`() = runSuspend {
    val repository = RegenerationRepository(
      exclusionPrompt = "カテゴリAを除外する",
      articles = listOf(
        article("article-1", "除外する本文"),
        article("article-2", "残す本文"),
      ),
    )
    val feedSource = RecordingFeedSource()
    val generator = RegenerationGenerator(result = "新しい原稿")
    val excluder = RegenerationNewsExcluder(excludedIds = setOf("article-1"))
    val clusterer = RecordingNewsClusterer(groups = listOf(listOf(0)))
    val useCase = GeneratePodcastEpisodeUseCase(
      repository = repository,
      feedContentSource = feedSource,
      scriptGenerator = generator,
      newsClusterer = clusterer,
      newsExcluder = excluder,
    )

    val regenerated = useCase.regenerate(repository.episode.id).episode

    assertEquals(PodcastEpisodeStatus.READY, regenerated.status)
    assertEquals(listOf("article-2"), regenerated.articles.map { it.articleId })
    assertEquals(listOf("article-1", "article-2"), excluder.candidateIds)
    assertEquals(listOf("article-2"), clusterer.candidateIds)
    assertEquals(0, regenerated.articles.single().chapterPosition)
    assertTrue(regenerated.script.orEmpty().contains("新しい原稿"))
    assertEquals(0, feedSource.requestCount)
    assertTrue(generator.prompt.contains("残す本文"))
    assertFalse(generator.prompt.contains("除外する本文"))
  }

  @Test
  fun `再生成は保存済み記事を現在の条件で再クラスタリングする`() = runSuspend {
    val repository = RegenerationRepository(
      articles = listOf(
        article("article-1", "本文1"),
        article("article-2", "本文2"),
        article("article-3", "本文3"),
      ),
    )
    val clusterer = RecordingNewsClusterer(groups = listOf(listOf(0, 2), listOf(1)))
    val useCase = GeneratePodcastEpisodeUseCase(
      repository = repository,
      feedContentSource = RecordingFeedSource(),
      scriptGenerator = RegenerationGenerator(result = "新しい原稿"),
      newsClusterer = clusterer,
    )

    val regenerated = useCase.regenerate(repository.episode.id).episode

    assertEquals(listOf("article-1", "article-3", "article-2"), regenerated.articles.map { it.articleId })
    assertEquals(listOf(0, 0, 1), regenerated.articles.map { it.chapterPosition })
    assertEquals(PodcastClusteringStatus.SUCCESS, regenerated.clusteringStatus)
  }

  @Test
  fun `現在の除外条件で全記事が除外される場合は既存エピソードを保持する`() = runSuspend {
    val repository = RegenerationRepository(
      exclusionPrompt = "カテゴリAを除外する",
      articles = listOf(article("article-1", "保存済み本文")),
    )
    val original = repository.episode
    val generator = RegenerationGenerator(result = "unused")
    val useCase = GeneratePodcastEpisodeUseCase(
      repository = repository,
      feedContentSource = RecordingFeedSource(),
      scriptGenerator = generator,
      newsExcluder = RegenerationNewsExcluder(excludedIds = setOf("article-1")),
    )

    val error = runCatching { useCase.regenerate(repository.episode.id) }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
    assertTrue(error?.message.orEmpty().contains("再生成対象の記事がありません"))
    assertEquals(original, repository.episode)
    assertFalse(repository.rebuildCalled)
    assertTrue(generator.prompt.isEmpty())
  }

  @Test
  fun `作り直し開始後に原稿生成が失敗した場合は旧原稿へ戻さない`() = runSuspend {
    val repository = RegenerationRepository()
    val useCase = GeneratePodcastEpisodeUseCase(
      repository = repository,
      feedContentSource = RecordingFeedSource(),
      scriptGenerator = RegenerationGenerator(error = IllegalStateException("generation failed")),
    )

    val error = runCatching { useCase.regenerate(repository.episode.id) }.exceptionOrNull()

    assertTrue(error is IllegalStateException)
    assertTrue(repository.rebuildCalled)
    assertEquals(PodcastEpisodeStatus.FAILED, repository.episode.status)
    assertNull(repository.episode.script)
    assertNull(repository.episode.regenerationStatus)
    assertTrue(repository.failEpisodeCalled)
  }
}

private class RegenerationRepository(
  exclusionPrompt: String = "",
  articles: List<PodcastEpisodeArticle> = listOf(article("article-1", "保存済み本文")),
) : PodcastRepository {
  private val program = PodcastProgram(
    id = "program-1",
    name = "朝のニュース",
    sourceIds = setOf("source-1"),
    provider = PodcastGenerationProvider.LOCAL,
    exclusionPrompt = exclusionPrompt,
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
  var rebuildCalled = false

  override suspend fun findProgram(programId: String): PodcastProgram? = program.takeIf { it.id == programId }
  override suspend fun findEpisode(episodeId: String): PodcastEpisode? = episode.takeIf { it.id == episodeId }
  override suspend fun archiveEpisode(episodeId: String): PodcastEpisode = error("unused")
  override suspend fun restoreEpisode(episodeId: String): PodcastEpisode = error("unused")
  override suspend fun deleteEpisode(episodeId: String) = error("unused")

  override suspend fun prepareEpisodeRetry(episodeId: String): PodcastEpisode {
    episode = episode.copy(status = PodcastEpisodeStatus.GENERATING, errorMessage = null, regenerationStatus = null)
    return episode
  }

  override suspend fun prepareEpisodeRebuild(
    episodeId: String,
    candidates: List<PodcastFeedEntry>,
    clusteringStatus: PodcastClusteringStatus,
  ): PodcastEpisode {
    check(episode.id == episodeId)
    rebuildCalled = true
    episode = episode.copy(
      status = PodcastEpisodeStatus.GENERATING,
      articles = candidates.map { it.toEpisodeArticle() },
      script = null,
      errorMessage = null,
      regenerationStatus = null,
      clusteringStatus = clusteringStatus,
    )
    return episode
  }

  override suspend fun markChapterGenerating(episodeId: String, position: Int): PodcastEpisode =
    updateChapter(position) { it.copy(chapterStatus = PodcastChapterGenerationStatus.GENERATING, chapterError = null) }

  override suspend fun completeChapter(episodeId: String, position: Int, script: String): PodcastEpisode =
    updateChapter(position) {
      it.copy(
        chapterStatus = PodcastChapterGenerationStatus.READY,
        chapterScript = script,
        chapterError = null,
      )
    }

  override suspend fun failChapter(episodeId: String, position: Int, message: String): PodcastEpisode =
    updateChapter(position) {
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
  override suspend fun recordExcludedEntries(
    programId: String,
    entries: List<PodcastFeedEntry>,
    excludedAtEpochMillis: Long,
  ) = Unit
  override suspend fun listEpisodes(programId: String): List<PodcastEpisode> = listOf(episode)
  override suspend fun claimPendingEpisode(programId: String): PodcastEpisode? = null
  override suspend fun reserveEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
  ): PodcastEpisode? = error("unused")

  private fun updateChapter(
    position: Int,
    transform: (PodcastEpisodeArticle) -> PodcastEpisodeArticle,
  ): PodcastEpisode {
    episode = episode.copy(
      articles = episode.articles.map { article ->
        if ((article.chapterPosition ?: 0) == position) transform(article) else article
      },
    )
    return episode
  }
}

private fun PodcastFeedEntry.toEpisodeArticle() = PodcastEpisodeArticle(
  articleId = articleId,
  feedId = feedId,
  title = title,
  sourceTitle = sourceTitle,
  publishedAtEpochMillis = publishedAtEpochMillis,
  articleUrl = articleUrl,
  feedContent = feedContent,
  chapterPosition = chapterPosition,
)

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

private class RegenerationNewsExcluder(
  private val excludedIds: Set<String>,
) : PodcastNewsExcluder {
  var candidateIds: List<String> = emptyList()

  override suspend fun filter(
    provider: PodcastGenerationProvider,
    exclusionPrompt: String,
    candidates: List<PodcastFeedEntry>,
  ): PodcastNewsExclusionResult {
    candidateIds = candidates.map(PodcastFeedEntry::articleId)
    return PodcastNewsExclusionResult(
      included = candidates.filterNot { it.articleId in excludedIds },
      excluded = candidates.filter { it.articleId in excludedIds },
    )
  }
}

private class RecordingNewsClusterer(
  private val groups: List<List<Int>>,
) : PodcastNewsClusterer {
  var candidateIds: List<String> = emptyList()

  override suspend fun cluster(
    provider: PodcastGenerationProvider,
    candidates: List<PodcastFeedEntry>,
  ): PodcastNewsClusteringResult {
    candidateIds = candidates.map(PodcastFeedEntry::articleId)
    return PodcastNewsClusteringResult(groups, PodcastClusteringStatus.SUCCESS)
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
