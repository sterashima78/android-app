package dev.terashima.yomitorirss.feature.podcast

import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastTest {
  @Test
  fun `生成開始時に記事を予約し原稿を保存する`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1"), entry("a2")))
    val generator = RecordingGenerator("生成された原稿")
    val useCase = GeneratePodcastEpisodeUseCase(repository, source, generator) { 1234L }

    val result = useCase.generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(PodcastEpisodeStatus.READY, result.episode.status)
    assertEquals(listOf("a1", "a2"), result.episode.articles.map { it.articleId })
    assertEquals("生成された原稿", result.episode.script)
    assertEquals(PodcastGenerationProvider.LOCAL, generator.provider)
    assertTrue(generator.prompt.orEmpty().contains("フィード内のタイトルと本文だけ"))
    assertFalse(generator.prompt.orEmpty().contains("https://"))
  }

  @Test
  fun `同じ番組で消費済みの記事は次のエピソードに含めない`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1")))
    val useCase = GeneratePodcastEpisodeUseCase(repository, source, RecordingGenerator("原稿")) { 1234L }

    useCase.generate(program.id)
    val second = useCase.generate(program.id)

    assertEquals(PodcastGenerationResult.NoNewArticles, second)
  }

  @Test
  fun `最大記事数を超えた未消費記事は次回生成に回す`() = runSuspend {
    val program = program(maxArticlesPerEpisode = 1)
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1"), entry("a2")))
    val useCase = GeneratePodcastEpisodeUseCase(repository, source, RecordingGenerator("原稿")) { 1234L }

    val first = useCase.generate(program.id) as PodcastGenerationResult.Generated
    val second = useCase.generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(listOf("a1"), first.episode.articles.map { it.articleId })
    assertEquals(listOf("a2"), second.episode.articles.map { it.articleId })
  }

  @Test
  fun `生成失敗後の再試行は同じ記事スナップショットを使う`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1")))
    val failing = RecordingGenerator(result = null)
    val firstUseCase = GeneratePodcastEpisodeUseCase(repository, source, failing) { 1234L }

    runCatching { firstUseCase.generate(program.id) }
    val failed = repository.episodes.single()
    assertEquals(PodcastEpisodeStatus.FAILED, failed.status)

    source.entries = listOf(entry("a2"))
    val retryGenerator = RecordingGenerator("再生成原稿")
    val retryUseCase = GeneratePodcastEpisodeUseCase(repository, source, retryGenerator) { 9999L }
    val retried = retryUseCase.retry(failed.id).episode

    assertEquals(listOf("a1"), retried.articles.map { it.articleId })
    assertFalse(retryGenerator.prompt.orEmpty().contains("a2"))
  }

  @Test
  fun `生成中断後の次回実行は同じ記事スナップショットを再開する`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1")))
    val interruptedUseCase = GeneratePodcastEpisodeUseCase(repository, source, CancellingGenerator()) { 1234L }

    runCatching { interruptedUseCase.generate(program.id) }
    val interrupted = repository.episodes.single()
    assertEquals(PodcastEpisodeStatus.GENERATING, interrupted.status)

    source.entries = listOf(entry("a2"))
    val resumeGenerator = RecordingGenerator("再開原稿")
    val resumed = GeneratePodcastEpisodeUseCase(repository, source, resumeGenerator) { 9999L }
      .generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(interrupted.id, resumed.episode.id)
    assertEquals(listOf("a1"), resumed.episode.articles.map { it.articleId })
    assertEquals(1, repository.episodes.size)
    assertFalse(resumeGenerator.prompt.orEmpty().contains("a2"))
  }

  @Test
  fun `プロンプトは入力本文のみを根拠にし読み上げ向け表現を要求する`() {
    val prompt = buildPodcastPrompt(
      programName = "朝のニュース",
      articles = listOf(entry("a1").toEpisodeArticle()),
    )

    assertTrue(prompt.contains("外部ページ、リンク先、一般知識から情報を補わない"))
    assertTrue(prompt.contains("音声合成が自然に読める日本語表現"))
    assertTrue(prompt.contains("本文 a1"))
  }
}

private fun program(maxArticlesPerEpisode: Int = 12) = PodcastProgram(
  id = "program-1",
  name = "朝のニュース",
  feedIds = setOf("feed-1"),
  provider = PodcastGenerationProvider.LOCAL,
  maxArticlesPerEpisode = maxArticlesPerEpisode,
)

private fun entry(id: String) = PodcastFeedEntry(
  articleId = id,
  feedId = "feed-1",
  title = "タイトル $id",
  sourceTitle = "情報源",
  publishedAtEpochMillis = 100L,
  feedContent = "本文 $id",
)

private fun PodcastFeedEntry.toEpisodeArticle() = PodcastEpisodeArticle(
  articleId = articleId,
  feedId = feedId,
  title = title,
  sourceTitle = sourceTitle,
  publishedAtEpochMillis = publishedAtEpochMillis,
  feedContent = feedContent,
)

private class FakeFeedContentSource(
  var entries: List<PodcastFeedEntry>,
) : PodcastFeedContentSource {
  override suspend fun latestEntries(feedIds: Set<String>, limit: Int): List<PodcastFeedEntry> =
    entries.filter { it.feedId in feedIds }.take(limit)
}

private class RecordingGenerator(
  private val result: String?,
) : PodcastScriptGenerator {
  var provider: PodcastGenerationProvider? = null
  var prompt: String? = null

  override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String {
    this.provider = provider
    this.prompt = prompt
    return result ?: error("generation failed")
  }
}

private class CancellingGenerator : PodcastScriptGenerator {
  override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String {
    throw CancellationException("interrupted")
  }
}

private class FakePodcastRepository(
  private val program: PodcastProgram,
) : PodcastRepository {
  val episodes = mutableListOf<PodcastEpisode>()
  private val consumed = mutableSetOf<String>()

  override suspend fun listPrograms(): List<PodcastProgram> = listOf(program)
  override suspend fun findProgram(programId: String): PodcastProgram? = program.takeIf { it.id == programId }
  override suspend fun saveProgram(program: PodcastProgram) = Unit
  override suspend fun deleteProgram(programId: String) = Unit
  override suspend fun listEpisodes(programId: String): List<PodcastEpisode> = episodes.filter { it.programId == programId }
  override suspend fun findEpisode(episodeId: String): PodcastEpisode? = episodes.find { it.id == episodeId }
  override suspend fun findGeneratingEpisode(programId: String): PodcastEpisode? =
    episodes.firstOrNull { it.programId == programId && it.status == PodcastEpisodeStatus.GENERATING }

  override suspend fun reserveEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
  ): PodcastEpisode? {
    val selected = candidates
      .filterNot { it.articleId in consumed }
      .take(program.maxArticlesPerEpisode)
    if (selected.isEmpty()) return null
    consumed += selected.map { it.articleId }
    val episode = PodcastEpisode(
      id = "episode-${episodes.size + 1}",
      programId = program.id,
      title = program.name,
      createdAtEpochMillis = createdAtEpochMillis,
      status = PodcastEpisodeStatus.GENERATING,
      articles = selected.map { it.toEpisodeArticle() },
    )
    episodes += episode
    return episode
  }

  override suspend fun completeEpisode(episodeId: String, title: String, script: String): PodcastEpisode =
    update(episodeId) { it.copy(title = title, script = script, errorMessage = null, status = PodcastEpisodeStatus.READY) }

  override suspend fun failEpisode(episodeId: String, message: String): PodcastEpisode =
    update(episodeId) { it.copy(errorMessage = message, status = PodcastEpisodeStatus.FAILED) }

  private fun update(episodeId: String, transform: (PodcastEpisode) -> PodcastEpisode): PodcastEpisode {
    val index = episodes.indexOfFirst { it.id == episodeId }
    check(index >= 0)
    return transform(episodes[index]).also { episodes[index] = it }
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
