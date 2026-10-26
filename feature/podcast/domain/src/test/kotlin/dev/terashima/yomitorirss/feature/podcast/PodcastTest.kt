package dev.terashima.yomitorirss.feature.podcast

import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastTest {
  @Test
  fun `生成開始時に1記事ずつ原稿を生成してcheckpointする`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1"), entry("a2")))
    val generator = RecordingGenerator("生成された原稿")
    val useCase = GeneratePodcastEpisodeUseCase(repository, source, generator, nowEpochMillis = { 1234L })

    val result = useCase.generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(PodcastEpisodeStatus.READY, result.episode.status)
    assertEquals(listOf("a1", "a2"), result.episode.articles.map { it.articleId })
    assertEquals(2, generator.prompts.size)
    assertTrue(generator.prompts[0].contains("本文 a1"))
    assertFalse(generator.prompts[0].contains("本文 a2"))
    assertTrue(generator.prompts[1].contains("本文 a2"))
    assertFalse(generator.prompts[1].contains("本文 a1"))
    assertTrue(result.episode.articles.all { it.chapterStatus == PodcastChapterGenerationStatus.READY })
    assertTrue(result.episode.script.orEmpty().contains("[[CHAPTER:1]]"))
    assertTrue(result.episode.script.orEmpty().contains("[[CHAPTER:2]]"))
    assertEquals(listOf("source-1"), source.requestedSources.map { it.id })
    assertEquals(PodcastGenerationProvider.LOCAL, generator.provider)
    assertFalse(generator.prompts.any { it.contains("https://") })
  }

  @Test
  fun `feedカテゴリが同じニュースを連続配置してからepisodeへ予約する`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(
      listOf(
        entry("a1").copy(categories = listOf("Topic/A")),
        entry("a2").copy(categories = listOf("Topic/B")),
        entry("a3").copy(categories = listOf("Topic/A")),
      ),
    )
    val useCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      RecordingGenerator("生成された原稿"),
      nowEpochMillis = { 1234L },
    )

    val result = useCase.generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(listOf("a1", "a3", "a2"), result.episode.articles.map { it.articleId })
    assertEquals(listOf(0, 1, 2), result.episode.articles.map { it.chapterPosition })
  }

  @Test
  fun `生成進捗は保存済みcheckpoint数から完了まで単調に更新する`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1"), entry("a2")))
    val useCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      RecordingGenerator("生成された原稿"),
      nowEpochMillis = { 1234L },
    )
    val progress = mutableListOf<PodcastGenerationProgress>()

    useCase.generate(program.id) { progress += it }

    assertEquals(
      listOf(
        PodcastGenerationProgress(0, 2),
        PodcastGenerationProgress(1, 2),
        PodcastGenerationProgress(2, 2),
      ),
      progress,
    )
  }

  @Test
  fun `進捗projectionの通常失敗は生成結果を失敗にしない`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1")))
    val useCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      RecordingGenerator("生成された原稿"),
      nowEpochMillis = { 1234L },
    )

    val result = useCase.generate(program.id) {
      throw IllegalStateException("progress unavailable")
    } as PodcastGenerationResult.Generated

    assertEquals(PodcastEpisodeStatus.READY, result.episode.status)
    assertEquals(PodcastChapterGenerationStatus.READY, result.episode.articles.single().chapterStatus)
  }

  @Test
  fun `途中失敗後は完成済みチャプターを再生成しない`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1"), entry("a2")))
    val firstGenerator = SequencedGenerator(listOf("1件目の原稿", null))
    val firstUseCase = GeneratePodcastEpisodeUseCase(repository, source, firstGenerator, nowEpochMillis = { 1234L })

    runCatching { firstUseCase.generate(program.id) }
    val failed = repository.episodes.single()
    assertEquals(PodcastEpisodeStatus.FAILED, failed.status)
    assertEquals(PodcastChapterGenerationStatus.READY, failed.articles[0].chapterStatus)
    assertEquals(PodcastChapterGenerationStatus.FAILED, failed.articles[1].chapterStatus)

    val retryGenerator = RecordingGenerator("2件目の再生成原稿")
    val retried = GeneratePodcastEpisodeUseCase(repository, source, retryGenerator, nowEpochMillis = { 9999L })
      .retry(failed.id).episode

    assertEquals(1, retryGenerator.prompts.size)
    assertFalse(retryGenerator.prompts.single().contains("本文 a1"))
    assertTrue(retryGenerator.prompts.single().contains("本文 a2"))
    assertTrue(retried.script.orEmpty().contains("1件目の原稿"))
    assertTrue(retried.script.orEmpty().contains("2件目の再生成原稿"))
  }

  @Test
  fun `中断後の再開は完成済みチャプターを再生成しない`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1"), entry("a2")))
    val interruptedUseCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      CompleteThenCancelGenerator(),
      nowEpochMillis = { 1234L },
    )

    runCatching { interruptedUseCase.generate(program.id) }
    val interrupted = repository.episodes.single()
    assertEquals(PodcastEpisodeStatus.GENERATING, interrupted.status)
    assertEquals(PodcastChapterGenerationStatus.READY, interrupted.articles[0].chapterStatus)
    assertEquals(PodcastChapterGenerationStatus.GENERATING, interrupted.articles[1].chapterStatus)

    val resumeGenerator = RecordingGenerator("2件目の再開原稿")
    val resumed = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      resumeGenerator,
      nowEpochMillis = { 9999L },
    ).generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(1, resumeGenerator.prompts.size)
    assertFalse(resumeGenerator.prompts.single().contains("本文 a1"))
    assertTrue(resumeGenerator.prompts.single().contains("本文 a2"))
    assertTrue(resumed.episode.script.orEmpty().contains("1件目の原稿"))
    assertTrue(resumed.episode.script.orEmpty().contains("2件目の再開原稿"))
  }

  @Test
  fun `クラウド生成はチャプターを最大3件並列で生成する`() = runBlocking {
    val program = program(provider = PodcastGenerationProvider.CLOUD)
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1"), entry("a2"), entry("a3"), entry("a4")))
    val generator = ParallelRecordingGenerator(expectedConcurrent = 3)
    val useCase = GeneratePodcastEpisodeUseCase(repository, source, generator, nowEpochMillis = { 1234L })

    val result = useCase.generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(PodcastEpisodeStatus.READY, result.episode.status)
    assertEquals(3, generator.maxConcurrent)
    assertEquals(4, generator.prompts.size)
    assertTrue(result.episode.articles.all { it.chapterStatus == PodcastChapterGenerationStatus.READY })
  }

  @Test
  fun `クラウド並列生成の途中失敗後は完成済みチャプターだけを再利用する`() = runBlocking {
    val program = program(provider = PodcastGenerationProvider.CLOUD)
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1"), entry("a2"), entry("a3")))
    val firstUseCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      PartialParallelFailureGenerator(),
      nowEpochMillis = { 1234L },
    )

    runCatching { firstUseCase.generate(program.id) }

    val failed = repository.episodes.single()
    assertEquals(PodcastEpisodeStatus.FAILED, failed.status)
    assertEquals(PodcastChapterGenerationStatus.READY, failed.articles[0].chapterStatus)
    assertEquals(PodcastChapterGenerationStatus.FAILED, failed.articles[1].chapterStatus)
    assertEquals(PodcastChapterGenerationStatus.READY, failed.articles[2].chapterStatus)

    val retryGenerator = RecordingGenerator("再開原稿")
    val retried = GeneratePodcastEpisodeUseCase(repository, source, retryGenerator, nowEpochMillis = { 9999L })
      .retry(failed.id).episode

    assertEquals(1, retryGenerator.prompts.size)
    assertFalse(retryGenerator.prompts.any { it.contains("本文 a1") })
    assertTrue(retryGenerator.prompts.single().contains("本文 a2"))
    assertFalse(retryGenerator.prompts.any { it.contains("本文 a3") })
    assertTrue(retried.script.orEmpty().contains("1件目の並列原稿"))
    assertTrue(retried.script.orEmpty().contains("3件目の並列原稿"))
  }

  @Test
  fun `番組が参照するソース定義が欠落している場合は設定エラーにする`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program).apply { sources.clear() }
    val source = FakeFeedContentSource(listOf(entry("a1")))
    val useCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      RecordingGenerator("原稿"),
      nowEpochMillis = { 1234L },
    )

    val error = runCatching { useCase.generate(program.id) }.exceptionOrNull()

    assertTrue(error is IllegalArgumentException)
    assertTrue(error?.message.orEmpty().contains("番組設定を確認"))
    assertTrue(source.requestedSources.isEmpty())
  }

  @Test
  fun `除外条件に該当した記事を記録して原稿生成対象から外す`() = runSuspend {
    val program = program(exclusionPrompt = "特定カテゴリを除外する")
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1"), entry("a2")))
    val generator = RecordingGenerator("原稿")
    val excluder = RecordingNewsExcluder(excludedIds = setOf("a1"))
    val useCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      generator,
      nowEpochMillis = { 1234L },
      newsExcluder = excluder,
    )

    val result = useCase.generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(listOf("a2"), result.episode.articles.map { it.articleId })
    assertEquals(setOf("a1"), repository.excluded)
    assertEquals(listOf("a1", "a2"), excluder.candidateIds)
    assertEquals(1, generator.prompts.size)
    assertTrue(generator.prompts.single().contains("本文 a2"))
    assertFalse(generator.prompts.single().contains("本文 a1"))
  }

  @Test
  fun `全候補が除外された場合はエピソードを作らない`() = runSuspend {
    val program = program(exclusionPrompt = "特定カテゴリを除外する")
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1")))
    val generator = RecordingGenerator("原稿")
    val useCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      generator,
      nowEpochMillis = { 1234L },
      newsExcluder = RecordingNewsExcluder(excludedIds = setOf("a1")),
    )

    val result = useCase.generate(program.id)

    assertEquals(PodcastGenerationResult.NoNewArticles, result)
    assertEquals(setOf("a1"), repository.excluded)
    assertTrue(repository.episodes.isEmpty())
    assertTrue(generator.prompts.isEmpty())
  }

  @Test
  fun `除外条件が空ならAI除外判定を呼ばない`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1")))
    val excluder = RecordingNewsExcluder(excludedIds = setOf("a1"))
    val useCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      RecordingGenerator("原稿"),
      nowEpochMillis = { 1234L },
      newsExcluder = excluder,
    )

    val result = useCase.generate(program.id)

    assertTrue(result is PodcastGenerationResult.Generated)
    assertTrue(excluder.candidateIds.isEmpty())
  }

  @Test
  fun `除外結果が候補の完全な分割でなければ全候補を残す`() = runSuspend {
    val program = program(exclusionPrompt = "特定カテゴリを除外する")
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1"), entry("a2")))
    val generator = RecordingGenerator("原稿")
    val malformed = object : PodcastNewsExcluder {
      override suspend fun filter(
        provider: PodcastGenerationProvider,
        exclusionPrompt: String,
        candidates: List<PodcastFeedEntry>,
      ): PodcastNewsExclusionResult = PodcastNewsExclusionResult(
        included = listOf(candidates.first()),
        excluded = emptyList(),
      )
    }
    val useCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      generator,
      nowEpochMillis = { 1234L },
      newsExcluder = malformed,
    )

    val result = useCase.generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(listOf("a1", "a2"), result.episode.articles.map { it.articleId })
    assertTrue(repository.excluded.isEmpty())
  }

  @Test
  fun `72時間以内のアーカイブで既報を別エピソードに分け新しいニュースを集約する`() = runSuspend {
    val program = program(maxArticlesPerEpisode = 3)
    val repository = FakePodcastRepository(program)
    val now = 5L * 24 * 60 * 60 * 1_000
    repository.episodes += PodcastEpisode(
      id = "archived",
      programId = program.id,
      title = "既報",
      createdAtEpochMillis = now - 60L * 60 * 60 * 1_000,
      status = PodcastEpisodeStatus.ARCHIVED,
      articles = listOf(entry("old").toEpisodeArticle()),
    )
    val source = FakeFeedContentSource(listOf(entry("repeated"), entry("new-a"), entry("new-b")))
    var eventCalls = 0
    val eventClusterer = object : PodcastNewsClusterer {
      override suspend fun cluster(
        provider: PodcastGenerationProvider,
        candidates: List<PodcastFeedEntry>,
      ): PodcastNewsClusteringResult {
        eventCalls++
        assertEquals(listOf("old", "repeated", "new-a", "new-b"), candidates.map { it.articleId })
        return PodcastNewsClusteringResult(
          groups = listOf(listOf(0, 1), listOf(2), listOf(3)),
          status = PodcastClusteringStatus.SUCCESS,
        )
      }
    }
    val generator = RecordingGenerator("原稿")
    val logs = RecordingPodcastLogStore()
    val useCase = GeneratePodcastEpisodeUseCase(
      repository, source, generator, nowEpochMillis = { now }, newsClusterer = eventClusterer, logStore = logs,
    )

    val generated = useCase.generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(listOf("new-a", "new-b"), generated.episode.articles.map { it.articleId })
    assertEquals(1, eventCalls) // Both partitions reuse the successful classification.
    assertEquals(2, generator.prompts.size)
    val repeated = repository.episodes.single { it.status == PodcastEpisodeStatus.QUEUED }
    assertEquals(listOf("repeated"), repeated.articles.map { it.articleId })
    assertTrue(repeated.title.contains("既報ニュース"))
    assertEquals(PodcastClusteringStatus.SUCCESS, generated.episode.clusteringStatus)
    assertTrue(logs.logs.value.any { it.message.contains("既報判定成功") && it.message.contains("既報1記事") })
    assertEquals(PodcastEpisodeStatus.ARCHIVED, repository.episodes.first().status)
    assertEquals(listOf("old"), repository.episodes.first().articles.map { it.articleId })
  }

  @Test
  fun `重複ニュースの複数記事は一チャプターと数え上限まで収録する`() = runSuspend {
    val program = program(maxArticlesPerEpisode = 1)
    val now = 5L * 24 * 60 * 60 * 1_000
    val repository = FakePodcastRepository(program)
    repository.episodes += PodcastEpisode(
      id = "archived", programId = program.id, title = "過去のニュース",
      createdAtEpochMillis = now - 1_000, status = PodcastEpisodeStatus.ARCHIVED,
      articles = listOf(entry("previous").toEpisodeArticle()),
    )
    val classifier = object : PodcastNewsClusterer {
      override suspend fun cluster(
        provider: PodcastGenerationProvider,
        candidates: List<PodcastFeedEntry>,
      ): PodcastNewsClusteringResult {
        assertEquals(
          listOf("previous", "covered", "new-a", "new-a-another-source", "new-b"),
          candidates.map { it.articleId },
        )
        return PodcastNewsClusteringResult(
          listOf(listOf(0, 1), listOf(2, 3), listOf(4)),
          PodcastClusteringStatus.SUCCESS,
        )
      }
    }
    val useCase = GeneratePodcastEpisodeUseCase(
      repository,
      FakeFeedContentSource(
        listOf(entry("covered"), entry("new-a"), entry("new-a-another-source"), entry("new-b")),
      ),
      RecordingGenerator("原稿"),
      nowEpochMillis = { now },
      newsClusterer = classifier,
    )

    val first = useCase.generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(listOf("new-a", "new-a-another-source"), first.episode.articles.map { it.articleId })
    assertEquals(listOf(0, 0), first.episode.articles.map { it.chapterPosition })
    val queued = repository.episodes.filter { it.status == PodcastEpisodeStatus.QUEUED }
    assertEquals(listOf("new-b", "covered"), queued.flatMap { it.articles.map(PodcastEpisodeArticle::articleId) })
    assertEquals(listOf(false, true), queued.map { it.title.contains("既報ニュース") })
    assertEquals(listOf(0, 0), queued.map { it.articles.single().chapterPosition })
  }

  @Test
  fun `50件のニュースを通常30件と10件および既報10件へ分割する`() = runSuspend {
    val program = program(maxArticlesPerEpisode = 30)
    val repository = FakePodcastRepository(program)
    val now = 5L * 24 * 60 * 60 * 1_000
    repository.episodes += PodcastEpisode(
      id = "archive", programId = program.id, title = "過去のニュース",
      createdAtEpochMillis = now - 1_000, status = PodcastEpisodeStatus.ARCHIVED,
      articles = listOf(entry("old").toEpisodeArticle()),
    )
    val articles = (0 until 40).map { entry("fresh-$it") } +
      (0 until 10).map { entry("repeat-$it") }
    val clusterer = object : PodcastNewsClusterer {
      override suspend fun cluster(
        provider: PodcastGenerationProvider,
        candidates: List<PodcastFeedEntry>,
      ): PodcastNewsClusteringResult {
        val oldIndex = candidates.indexOfFirst { it.articleId == "old" }
        val repeated = candidates.indices.filter { candidates[it].articleId.startsWith("repeat-") }
        val matched = if (oldIndex >= 0) listOf(oldIndex) + repeated else emptyList()
        val groups = candidates.indices.filterNot { it in matched }.map { listOf(it) } +
          if (matched.isNotEmpty()) listOf(matched) else emptyList()
        return PodcastNewsClusteringResult(groups, PodcastClusteringStatus.SUCCESS)
      }
    }
    val useCase = GeneratePodcastEpisodeUseCase(
      repository, FakeFeedContentSource(articles), RecordingGenerator("原稿"),
      nowEpochMillis = { now }, newsClusterer = clusterer,
    )
    val first = useCase.generate(program.id) as PodcastGenerationResult.Generated
    assertEquals(30, first.episode.chapterGroups().size)
    val reserved = repository.episodes.filter { it.id != "archive" }
    assertEquals(listOf(30, 10, 10), reserved.map { it.articles.size })
    assertEquals(
      listOf(PodcastEpisodeStatus.READY, PodcastEpisodeStatus.QUEUED, PodcastEpisodeStatus.QUEUED),
      reserved.map { it.status },
    )
    assertEquals(listOf(false, false, true), reserved.map { it.title.contains("既報ニュース") })
    assertEquals(50, reserved.flatMap { it.articles }.map { it.articleId }.toSet().size)

    useCase.generate(program.id)
    val repeated = requireNotNull(repository.episodes.lastOrNull())
    assertEquals(PodcastEpisodeStatus.QUEUED, repeated.status)
    val third = useCase.generate(program.id) as PodcastGenerationResult.Generated
    assertEquals(PodcastEpisodeStatus.READY, third.episode.status)
    assertTrue(third.episode.title.contains("既報ニュース"))
    assertEquals(10, third.episode.articles.size)
  }

  @Test
  fun `候補がすべて既報でも専用タイトルのエピソードを生成する`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val now = 5L * 24 * 60 * 60 * 1_000
    repository.episodes += PodcastEpisode(
      id = "archive", programId = program.id, title = "過去のニュース",
      createdAtEpochMillis = now - 1_000, status = PodcastEpisodeStatus.ARCHIVED,
      articles = listOf(entry("old").toEpisodeArticle()),
    )
    val clusterer = object : PodcastNewsClusterer {
      override suspend fun cluster(
        provider: PodcastGenerationProvider,
        candidates: List<PodcastFeedEntry>,
      ): PodcastNewsClusteringResult = PodcastNewsClusteringResult(
        listOf(candidates.indices.toList()), PodcastClusteringStatus.SUCCESS,
      )
    }
    val generated = GeneratePodcastEpisodeUseCase(
      repository, FakeFeedContentSource(listOf(entry("repeat"))), RecordingGenerator("原稿"),
      nowEpochMillis = { now }, newsClusterer = clusterer,
    ).generate(program.id) as PodcastGenerationResult.Generated
    assertEquals(listOf("repeat"), generated.episode.articles.map { it.articleId })
    assertTrue(generated.episode.title.contains("既報ニュース"))
  }

  @Test
  fun `72時間より古いアーカイブは重複判定に使用しない`() = runSuspend {
    val program = program()
    val now = 5L * 24 * 60 * 60 * 1_000
    val repository = FakePodcastRepository(program)
    repository.episodes += PodcastEpisode(
      id = "old-archive", programId = program.id, title = "既報",
      createdAtEpochMillis = now - 3L * 24 * 60 * 60 * 1_000 - 1,
      status = PodcastEpisodeStatus.ARCHIVED,
      articles = listOf(entry("old").toEpisodeArticle()),
    )
    val source = FakeFeedContentSource(listOf(entry("new")))
    var comparisonCalls = 0
    val clusterer = object : PodcastNewsClusterer {
      override suspend fun cluster(
        provider: PodcastGenerationProvider,
        candidates: List<PodcastFeedEntry>,
      ): PodcastNewsClusteringResult {
        comparisonCalls++
        assertEquals(listOf("new"), candidates.map { it.articleId })
        return PodcastNewsClusteringResult(listOf(listOf(0)), PodcastClusteringStatus.SKIPPED)
      }
    }

    val generated = GeneratePodcastEpisodeUseCase(
      repository, source, RecordingGenerator("原稿"),
      nowEpochMillis = { now }, newsClusterer = clusterer,
    ).generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(listOf("new"), generated.episode.articles.map { it.articleId })
    assertEquals(1, comparisonCalls)
  }

  @Test
  fun `過去アーカイブとの比較失敗時は新規記事を捨てない`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val now = 5L * 24 * 60 * 60 * 1_000
    repository.episodes += PodcastEpisode(
      id = "archive", programId = program.id, title = "既報",
      createdAtEpochMillis = now - 1_000, status = PodcastEpisodeStatus.ARCHIVED,
      articles = listOf(entry("old").toEpisodeArticle()),
    )
    var clusterCalls = 0
    val clusterer = object : PodcastNewsClusterer {
      override suspend fun cluster(
        provider: PodcastGenerationProvider,
        candidates: List<PodcastFeedEntry>,
      ): PodcastNewsClusteringResult {
        clusterCalls++
        return PodcastNewsClusteringResult(
          candidates.indices.map { listOf(it) },
          if (clusterCalls == 1) PodcastClusteringStatus.FALLBACK_INFERENCE_ERROR
          else PodcastClusteringStatus.SUCCESS,
        )
      }
    }
    val logs = RecordingPodcastLogStore()
    val generated = GeneratePodcastEpisodeUseCase(
      repository, FakeFeedContentSource(listOf(entry("new-a"), entry("new-b"))),
      RecordingGenerator("原稿"), nowEpochMillis = { now }, newsClusterer = clusterer, logStore = logs,
    ).generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(listOf("new-a", "new-b"), generated.episode.articles.map { it.articleId })
    assertEquals(2, clusterCalls)
    assertTrue(logs.logs.value.any { it.level == PodcastGenerationLogLevel.WARNING &&
      it.message.contains("FALLBACK_INFERENCE_ERROR") && it.message.contains("通常ニュース") })
  }

  @Test
  fun `多数のアーカイブでも入力を分割して既報だけを除外する`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val now = 5L * 24 * 60 * 60 * 1_000
    repository.episodes += PodcastEpisode(
      id = "archive", programId = program.id, title = "既報",
      createdAtEpochMillis = now - 1_000, status = PodcastEpisodeStatus.ARCHIVED,
      articles = (0 until 25).map { entry("old-$it").toEpisodeArticle() },
    )
    val requests = mutableListOf<List<String>>()
    val clusterer = object : PodcastNewsClusterer {
      override suspend fun cluster(
        provider: PodcastGenerationProvider,
        candidates: List<PodcastFeedEntry>,
      ): PodcastNewsClusteringResult {
        requests += candidates.map(PodcastFeedEntry::articleId)
        val oldIndex = candidates.indexOfFirst { it.articleId == "old-24" }
        val repeatedIndex = candidates.indexOfFirst { it.articleId == "repeated" }
        val matched = if (oldIndex >= 0 && repeatedIndex >= 0) setOf(oldIndex, repeatedIndex) else emptySet()
        val groups = candidates.indices.filterNot { it in matched }.map { listOf(it) } +
          if (matched.isNotEmpty()) listOf(listOf(oldIndex, repeatedIndex)) else emptyList()
        return PodcastNewsClusteringResult(groups, PodcastClusteringStatus.SUCCESS)
      }
    }

    val generated = GeneratePodcastEpisodeUseCase(
      repository, FakeFeedContentSource(listOf(entry("repeated"), entry("new"))),
      RecordingGenerator("原稿"), nowEpochMillis = { now }, newsClusterer = clusterer,
    ).generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(listOf("new"), generated.episode.articles.map { it.articleId })
    assertEquals(listOf("repeated"), repository.episodes.single { it.status == PodcastEpisodeStatus.QUEUED }.articles.map { it.articleId })
    assertEquals(5, requests.size) // Three archive batches, then two independent partitions.
    assertTrue(requests.take(3).all { it.size <= 24 })
    assertEquals(listOf("new"), requests[3])
    assertEquals(listOf("repeated"), requests[4])
  }

  @Test
  fun `53件の新規候補を25件のアーカイブと分割比較して既報を分離する`() = runSuspend {
    val program = program(maxArticlesPerEpisode = 30)
    val repository = FakePodcastRepository(program)
    val now = 5L * 24 * 60 * 60 * 1_000
    repository.episodes += PodcastEpisode(
      id = "archive", programId = program.id, title = "過去のニュース",
      createdAtEpochMillis = now - 1_000, status = PodcastEpisodeStatus.ARCHIVED,
      articles = (0 until 25).map { entry("old-$it").toEpisodeArticle() },
    )
    val fresh = (0 until 52).map { entry("fresh-$it") }
    val candidates = fresh + entry("repeat-52")
    val requests = mutableListOf<List<String>>()
    val clusterer = object : PodcastNewsClusterer {
      override suspend fun cluster(
        provider: PodcastGenerationProvider,
        candidates: List<PodcastFeedEntry>,
      ): PodcastNewsClusteringResult {
        requests += candidates.map(PodcastFeedEntry::articleId)
        val archivedIndex = candidates.indexOfFirst { it.articleId == "old-24" }
        val repeatIndex = candidates.indexOfFirst { it.articleId == "repeat-52" }
        val matched = if (archivedIndex >= 0 && repeatIndex >= 0) setOf(archivedIndex, repeatIndex) else emptySet()
        return PodcastNewsClusteringResult(
          candidates.indices.filterNot { it in matched }.map { listOf(it) } +
            if (matched.isNotEmpty()) listOf(listOf(archivedIndex, repeatIndex)) else emptyList(),
          PodcastClusteringStatus.SUCCESS,
        )
      }
    }
    val logs = RecordingPodcastLogStore()
    GeneratePodcastEpisodeUseCase(
      repository, FakeFeedContentSource(candidates), RecordingGenerator("原稿"),
      nowEpochMillis = { now }, newsClusterer = clusterer, logStore = logs,
    ).generate(program.id)

    assertEquals(17, requests.size) // 3 archive chunks x 5 candidate chunks, then two independent partitions.
    assertTrue(requests.take(15).all { it.size <= 24 })
    val reserved = repository.episodes.filter { it.id != "archive" }
    assertEquals(fresh.map(PodcastFeedEntry::articleId),
      reserved.filterNot { it.title.contains("既報ニュース") }.flatMap { it.articles.map(PodcastEpisodeArticle::articleId) })
    assertEquals(listOf("repeat-52"),
      reserved.filter { it.title.contains("既報ニュース") }.flatMap { it.articles.map(PodcastEpisodeArticle::articleId) })
    assertTrue(logs.logs.value.any { it.level == PodcastGenerationLogLevel.INFO &&
      it.message.contains("既報判定成功（分割比較）") })
    assertTrue(logs.logs.value.none { it.message.contains("必要65回、上限64回") && it.message.contains("比較予算を超過") })
  }

  @Test
  fun `比較予算超過時は既報除外を省略して新着を保持する`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val now = 5L * 24 * 60 * 60 * 1_000
    repository.episodes += PodcastEpisode(
      id = "archive", programId = program.id, title = "既報",
      createdAtEpochMillis = now - 1_000, status = PodcastEpisodeStatus.ARCHIVED,
      articles = (0 until 769).map { entry("old-$it").toEpisodeArticle() },
    )
    val requests = mutableListOf<List<String>>()
    val clusterer = object : PodcastNewsClusterer {
      override suspend fun cluster(
        provider: PodcastGenerationProvider,
        candidates: List<PodcastFeedEntry>,
      ): PodcastNewsClusteringResult {
        requests += candidates.map(PodcastFeedEntry::articleId)
        return PodcastNewsClusteringResult(candidates.indices.map { listOf(it) }, PodcastClusteringStatus.SUCCESS)
      }
    }

    val logs = RecordingPodcastLogStore()
    val generated = GeneratePodcastEpisodeUseCase(
      repository, FakeFeedContentSource(listOf(entry("new"))),
      RecordingGenerator("原稿"), nowEpochMillis = { now }, newsClusterer = clusterer, logStore = logs,
    ).generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(listOf("new"), generated.episode.articles.map { it.articleId })
    assertEquals(listOf(listOf("new")), requests)
    assertTrue(logs.logs.value.any { it.level == PodcastGenerationLogLevel.WARNING &&
      it.message.contains("比較予算を超過") })
  }

  @Test
  fun `分割比較の途中で失敗すると全候補を維持する`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val now = 5L * 24 * 60 * 60 * 1_000
    repository.episodes += PodcastEpisode(
      id = "archive", programId = program.id, title = "既報",
      createdAtEpochMillis = now - 1_000, status = PodcastEpisodeStatus.ARCHIVED,
      articles = (0 until 25).map { entry("old-$it").toEpisodeArticle() },
    )
    var requests = 0
    val clusterer = object : PodcastNewsClusterer {
      override suspend fun cluster(
        provider: PodcastGenerationProvider,
        candidates: List<PodcastFeedEntry>,
      ): PodcastNewsClusteringResult {
        requests++
        return PodcastNewsClusteringResult(
          candidates.indices.map { listOf(it) },
          if (requests == 2) PodcastClusteringStatus.FALLBACK_INFERENCE_ERROR else PodcastClusteringStatus.SUCCESS,
        )
      }
    }
    val generated = GeneratePodcastEpisodeUseCase(
      repository, FakeFeedContentSource(listOf(entry("first"), entry("second"))),
      RecordingGenerator("原稿"), nowEpochMillis = { now }, newsClusterer = clusterer,
    ).generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(listOf("first", "second"), generated.episode.articles.map { it.articleId })
    assertEquals(3, requests) // Two comparison attempts, then ordinary clustering.
  }

  @Test
  fun `同じ番組で消費済みの記事は次のエピソードに含めない`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1")))
    val useCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      RecordingGenerator("原稿"),
      nowEpochMillis = { 1234L },
    )

    useCase.generate(program.id)
    val second = useCase.generate(program.id)

    assertEquals(PodcastGenerationResult.NoNewArticles, second)
  }

  @Test
  fun `最大記事数を超えた候補はfeedから消えても予約済みsnapshotから次回生成する`() = runSuspend {
    val program = program(maxArticlesPerEpisode = 1)
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1"), entry("a2")))
    val useCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      RecordingGenerator("原稿"),
      nowEpochMillis = { 1234L },
    )

    val first = useCase.generate(program.id) as PodcastGenerationResult.Generated
    assertEquals(PodcastEpisodeStatus.QUEUED, repository.episodes.single { it.articles.single().articleId == "a2" }.status)

    source.entries = emptyList()
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
    val firstUseCase = GeneratePodcastEpisodeUseCase(repository, source, failing, nowEpochMillis = { 1234L })

    runCatching { firstUseCase.generate(program.id) }
    val failed = repository.episodes.single()
    assertEquals(PodcastEpisodeStatus.FAILED, failed.status)

    source.entries = listOf(entry("a2"))
    val retryGenerator = RecordingGenerator("再生成原稿")
    val retryUseCase = GeneratePodcastEpisodeUseCase(repository, source, retryGenerator, nowEpochMillis = { 9999L })
    val retried = retryUseCase.retry(failed.id).episode

    assertEquals(listOf("a1"), retried.articles.map { it.articleId })
    assertFalse(retryGenerator.prompts.single().contains("a2"))
  }

  @Test
  fun `生成中断後の次回実行は同じ記事スナップショットを再開する`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1")))
    val interruptedUseCase = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      CancellingGenerator(),
      nowEpochMillis = { 1234L },
    )

    runCatching { interruptedUseCase.generate(program.id) }
    val interrupted = repository.episodes.single()
    assertEquals(PodcastEpisodeStatus.GENERATING, interrupted.status)
    assertEquals(PodcastChapterGenerationStatus.GENERATING, interrupted.articles.single().chapterStatus)

    source.entries = listOf(entry("a2"))
    val resumeGenerator = RecordingGenerator("再開原稿")
    val resumed = GeneratePodcastEpisodeUseCase(
      repository,
      source,
      resumeGenerator,
      nowEpochMillis = { 9999L },
    ).generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(interrupted.id, resumed.episode.id)
    assertEquals(listOf("a1"), resumed.episode.articles.map { it.articleId })
    assertEquals(1, repository.episodes.size)
    assertFalse(resumeGenerator.prompts.single().contains("a2"))
  }

  @Test
  fun `同じ番組の生成中に重複生成を開始しない`() = runSuspend {
    val program = program()
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1")))
    lateinit var useCase: GeneratePodcastEpisodeUseCase
    val generator = ReentrantGenerator { useCase.generate(program.id) }
    useCase = GeneratePodcastEpisodeUseCase(repository, source, generator, nowEpochMillis = { 1234L })

    val generated = useCase.generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(PodcastEpisodeStatus.READY, generated.episode.status)
    assertNotNull(generator.overlappingFailure)
    assertTrue(generator.overlappingFailure is IllegalStateException)
    assertEquals(1, repository.episodes.size)
  }

  @Test
  fun `記事単位promptは1記事だけを根拠に読み上げ原稿を要求する`() {
    val prompt = buildPodcastChapterPrompt(
      programName = "朝のニュース",
      article = entry("a1").toEpisodeArticle(),
      chapterNumber = 1,
      totalChapters = 2,
    )

    assertTrue(prompt.contains("外部ページ、リンク先、一般知識から情報を補わない"))
    assertTrue(prompt.contains("本文 a1"))
    assertTrue(prompt.contains("1 / 2"))
    assertTrue(prompt.contains("音声合成が自然に読める日本語表現"))
    assertFalse(prompt.contains("https://"))
  }

  @Test
  fun `legacy program settings still generate new episodes with embedding mode`() = runSuspend {
    val program = program().copy(clusteringMode = PodcastClusteringMode.EVENT)
    val repository = FakePodcastRepository(program)
    val source = FakeFeedContentSource(listOf(entry("a1"), entry("a2")))
    val generator = RecordingGenerator("生成原稿")
    var embeddingCalls = 0
    val embeddingClusterer = object : PodcastNewsClusterer {
      override suspend fun cluster(
        provider: PodcastGenerationProvider,
        candidates: List<PodcastFeedEntry>,
      ): PodcastNewsClusteringResult {
        embeddingCalls++
        return PodcastNewsClusteringResult(listOf(listOf(0, 1)), PodcastClusteringStatus.SUCCESS)
      }
    }
    val useCase = GeneratePodcastEpisodeUseCase(
      repository = repository,
      feedContentSource = source,
      scriptGenerator = generator,
      newsClusterer = embeddingClusterer,
    )

    val generated = useCase.generate(program.id) as PodcastGenerationResult.Generated

    assertEquals(1, embeddingCalls)
    assertEquals(PodcastClusteringMode.TOPIC, generated.episode.clusteringMode)
    assertEquals(1, generated.episode.chapterGroups().size)
    assertTrue(generator.prompts.all { it.contains("同じ出来事とは限りません") })
    assertEquals(PodcastClusteringMode.EVENT, program.clusteringMode)
  }

  @Test
  fun `完成チャプターを記事順に決定的なエピソード原稿へ組み立てる`() {
    val articles = listOf(
      entry("a1").toEpisodeArticle().copy(
        chapterStatus = PodcastChapterGenerationStatus.READY,
        chapterScript = "1件目の読み上げ本文。",
      ),
      entry("a2").toEpisodeArticle().copy(
        chapterStatus = PodcastChapterGenerationStatus.READY,
        chapterScript = "2件目の読み上げ本文。",
      ),
    )

    val script = buildPodcastEpisodeScript("朝のニュース", articles)

    assertTrue(script.startsWith("[[CHAPTER:1]]"))
    assertTrue(script.indexOf("1件目の読み上げ本文。") < script.indexOf("[[CHAPTER:2]]"))
    assertTrue(script.contains("朝のニュースです。今回のニュースをお伝えします。"))
    assertTrue(script.endsWith("以上、今回のニュースでした。"))
  }

  @Test
  fun `チャプターマーカーを記事スナップショットへ対応付ける`() {
    val articles = listOf(entry("a1").toEpisodeArticle(), entry("a2").toEpisodeArticle())
    val episode = PodcastEpisode(
      id = "episode-1",
      programId = "program-1",
      title = "朝のニュース",
      createdAtEpochMillis = 100L,
      status = PodcastEpisodeStatus.READY,
      articles = articles,
      script = """
        [[CHAPTER:1]]
        1件目の読み上げ本文。
        [[CHAPTER:2]]
        2件目の読み上げ本文。
      """.trimIndent(),
    )

    val chapters = episode.playbackChapters()

    assertEquals(2, chapters.size)
    assertEquals(listOf("a1", "a2"), chapters.map { it.article?.articleId })
    assertEquals("1件目の読み上げ本文。", chapters[0].speechText)
    assertEquals("2件目の読み上げ本文。", chapters[1].speechText)
  }

  @Test
  fun `チャプター情報がない既存原稿は全文再生へフォールバックする`() {
    val episode = PodcastEpisode(
      id = "episode-1",
      programId = "program-1",
      title = "朝のニュース",
      createdAtEpochMillis = 100L,
      status = PodcastEpisodeStatus.READY,
      articles = listOf(entry("a1").toEpisodeArticle()),
      script = "既存の連続した原稿",
    )

    val chapters = episode.playbackChapters()

    assertEquals(1, chapters.size)
    assertNull(chapters.single().article)
    assertEquals("既存の連続した原稿", chapters.single().speechText)
  }
}

private fun program(
  maxArticlesPerEpisode: Int = 12,
  provider: PodcastGenerationProvider = PodcastGenerationProvider.LOCAL,
  exclusionPrompt: String = "",
) = PodcastProgram(
  id = "program-1",
  name = "朝のニュース",
  sourceIds = setOf("source-1"),
  provider = provider,
  maxArticlesPerEpisode = maxArticlesPerEpisode,
  exclusionPrompt = exclusionPrompt,
)

private fun source() = PodcastSource(
  id = "source-1",
  name = "情報源",
  feedUrl = "https://example.invalid/feed.xml",
)

private fun entry(id: String) = PodcastFeedEntry(
  articleId = id,
  feedId = "source-1",
  title = "タイトル $id",
  sourceTitle = "情報源",
  publishedAtEpochMillis = 100L,
  articleUrl = "https://example.invalid/articles/$id",
  feedContent = "本文 $id",
)

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

private class FakeFeedContentSource(
  var entries: List<PodcastFeedEntry>,
) : PodcastFeedContentSource {
  var requestedSources: List<PodcastSource> = emptyList()

  override suspend fun latestEntries(sources: List<PodcastSource>, limit: Int): List<PodcastFeedEntry> {
    requestedSources = sources
    val sourceIds = sources.mapTo(mutableSetOf(), PodcastSource::id)
    return entries.filter { it.feedId in sourceIds }.take(limit)
  }
}

private class RecordingNewsExcluder(
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

private class RecordingGenerator(
  private val result: String?,
) : PodcastScriptGenerator {
  var provider: PodcastGenerationProvider? = null
  val prompts = mutableListOf<String>()

  override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String {
    this.provider = provider
    prompts += prompt
    return result ?: error("generation failed")
  }
}

private class SequencedGenerator(
  private val results: List<String?>,
) : PodcastScriptGenerator {
  private var index = 0

  override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String {
    val result = results.getOrNull(index++) ?: error("generation failed")
    return result
  }
}

private class CompleteThenCancelGenerator : PodcastScriptGenerator {
  private var requestCount = 0

  override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String {
    requestCount += 1
    if (requestCount == 1) return "1件目の原稿"
    throw CancellationException("interrupted")
  }
}

private class ParallelRecordingGenerator(
  private val expectedConcurrent: Int,
) : PodcastScriptGenerator {
  private val active = AtomicInteger()
  private val observedMax = AtomicInteger()
  private val started = AtomicInteger()
  private val release = CompletableDeferred<Unit>()
  val prompts = ConcurrentLinkedQueue<String>()
  val maxConcurrent: Int get() = observedMax.get()

  override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String {
    val current = active.incrementAndGet()
    observedMax.updateAndGet { previous -> maxOf(previous, current) }
    try {
      if (started.incrementAndGet() == expectedConcurrent) release.complete(Unit)
      release.await()
      prompts.add(prompt)
      return "並列生成原稿"
    } finally {
      active.decrementAndGet()
    }
  }
}

private class PartialParallelFailureGenerator : PodcastScriptGenerator {
  private val firstCompleted = CompletableDeferred<Unit>()

  override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String = when {
    prompt.contains("本文 a1") -> {
      firstCompleted.complete(Unit)
      "1件目の並列原稿"
    }
    prompt.contains("本文 a2") -> {
      firstCompleted.await()
      error("2件目の生成に失敗")
    }
    else -> {
      firstCompleted.await()
      "3件目の並列原稿"
    }
  }
}

private class CancellingGenerator : PodcastScriptGenerator {
  override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String {
    throw CancellationException("interrupted")
  }
}

private class ReentrantGenerator(
  private val overlappingGenerate: suspend () -> Unit,
) : PodcastScriptGenerator {
  var overlappingFailure: Throwable? = null

  override suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String {
    overlappingFailure = runCatching { overlappingGenerate() }.exceptionOrNull()
    return "原稿"
  }
}

private class FakePodcastRepository(
  private val program: PodcastProgram,
) : PodcastRepository {
  val episodes = mutableListOf<PodcastEpisode>()
  val sources = mutableListOf(source())
  val excluded = mutableSetOf<String>()
  private val consumed = mutableSetOf<String>()

  override suspend fun listSources(): List<PodcastSource> = sources.toList()
  override suspend fun findSource(sourceId: String): PodcastSource? = sources.find { it.id == sourceId }
  override suspend fun saveSource(source: PodcastSource) {
    sources.removeAll { it.id == source.id }
    sources += source
  }
  override suspend fun deleteSource(sourceId: String) { sources.removeAll { it.id == sourceId } }
  override suspend fun listPrograms(): List<PodcastProgram> = listOf(program)
  override suspend fun findProgram(programId: String): PodcastProgram? = program.takeIf { it.id == programId }
  override suspend fun saveProgram(program: PodcastProgram) = Unit
  override suspend fun deleteProgram(programId: String) = Unit
  override suspend fun recordExcludedEntries(
    programId: String,
    entries: List<PodcastFeedEntry>,
    excludedAtEpochMillis: Long,
  ) {
    excluded += entries.map(PodcastFeedEntry::articleId)
  }
  override suspend fun listEpisodes(programId: String): List<PodcastEpisode> = episodes.filter { it.programId == programId }
  override suspend fun findEpisode(episodeId: String): PodcastEpisode? =
    synchronized(episodes) { episodes.find { it.id == episodeId } }
  override suspend fun archiveEpisode(episodeId: String): PodcastEpisode = error("unused")
  override suspend fun restoreEpisode(episodeId: String): PodcastEpisode = error("unused")
  override suspend fun recordPlaybackChapter(episodeId: String, chapterNumber: Int) = error("unused")
  override suspend fun deleteEpisode(episodeId: String) = error("unused")

  override suspend fun claimPendingEpisode(programId: String): PodcastEpisode? {
    val index = episodes.indexOfFirst {
      it.programId == programId && it.status == PodcastEpisodeStatus.GENERATING
    }.takeIf { it >= 0 } ?: episodes.indexOfFirst {
      it.programId == programId && it.status == PodcastEpisodeStatus.QUEUED
    }
    if (index < 0) return null
    val pending = episodes[index]
    if (pending.status == PodcastEpisodeStatus.QUEUED) {
      episodes[index] = pending.copy(status = PodcastEpisodeStatus.GENERATING)
    }
    return episodes[index]
  }

  override suspend fun reserveEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
  ): PodcastEpisode? = reserveEpisode(program, candidates, createdAtEpochMillis, null)

  override suspend fun reserveEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
    clusteringStatus: PodcastClusteringStatus?,
  ): PodcastEpisode? = reserveEpisode(
    program, candidates, createdAtEpochMillis, clusteringStatus, emptyList(), null,
  )

  override suspend fun reserveEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
    clusteringStatus: PodcastClusteringStatus?,
    repeatedCandidates: List<PodcastFeedEntry>,
    repeatedClusteringStatus: PodcastClusteringStatus?,
  ): PodcastEpisode? {
    val seenIds = mutableSetOf<String>()
    val partitions = listOf(
      Triple(candidates, clusteringStatus, false),
      Triple(repeatedCandidates, repeatedClusteringStatus, true),
    )
    var first: PodcastEpisode? = null
    var episodeIndex = 0
    for ((entries, status, repeated) in partitions) {
      val available = entries.filter { seenIds.add(it.articleId) && it.articleId !in consumed }
      val chapters = available.mapIndexed { index, item ->
        (item.chapterPosition ?: index) to item
      }.groupBy({ it.first }, { it.second }).toSortedMap().values.toList()
      for (selectedClusters in chapters.chunked(program.maxArticlesPerEpisode)) {
        val selected = selectedClusters.flatMapIndexed { chapterIndex, articles ->
          articles.map { it.copy(chapterPosition = chapterIndex) }
        }
        consumed += selected.map { it.articleId }
        val title = buildEpisodeTitle(program.name, createdAtEpochMillis + episodeIndex) +
          if (repeated) "（既報ニュース）" else ""
        val episode = PodcastEpisode(
          id = "episode-${episodes.size + 1}",
          programId = program.id,
          title = title,
          createdAtEpochMillis = createdAtEpochMillis + episodeIndex,
          status = if (episodeIndex == 0) PodcastEpisodeStatus.GENERATING else PodcastEpisodeStatus.QUEUED,
          articles = selected.map { it.toEpisodeArticle() },
          clusteringMode = program.clusteringMode,
          clusteringStatus = status,
        )
        episodes += episode
        if (first == null) first = episode
        episodeIndex += 1
      }
    }
    return first
  }

  override suspend fun prepareEpisodeRetry(episodeId: String): PodcastEpisode =
    update(episodeId) { it.copy(status = PodcastEpisodeStatus.GENERATING, errorMessage = null) }

  override suspend fun prepareEpisodeRebuild(
    episodeId: String,
    candidates: List<PodcastFeedEntry>,
    clusteringStatus: PodcastClusteringStatus,
  ): PodcastEpisode =
    update(episodeId) { episode ->
      episode.copy(
        status = PodcastEpisodeStatus.GENERATING,
        articles = candidates.map { it.toEpisodeArticle() },
        script = null,
        errorMessage = null,
        clusteringStatus = clusteringStatus,
      )
    }

  override suspend fun markChapterGenerating(episodeId: String, position: Int): PodcastEpisode =
    updateArticle(episodeId, position) {
      it.copy(chapterStatus = PodcastChapterGenerationStatus.GENERATING, chapterError = null)
    }

  override suspend fun completeChapter(episodeId: String, position: Int, script: String): PodcastEpisode =
    updateArticle(episodeId, position) {
      it.copy(
        chapterStatus = PodcastChapterGenerationStatus.READY,
        chapterScript = script,
        chapterError = null,
      )
    }

  override suspend fun failChapter(episodeId: String, position: Int, message: String): PodcastEpisode =
    updateArticle(episodeId, position) {
      it.copy(chapterStatus = PodcastChapterGenerationStatus.FAILED, chapterError = message)
    }

  override suspend fun completeEpisode(episodeId: String, title: String, script: String): PodcastEpisode =
    update(episodeId) {
      it.copy(
        title = title,
        script = script,
        errorMessage = null,
        status = PodcastEpisodeStatus.READY,
      )
    }

  override suspend fun failEpisode(episodeId: String, message: String): PodcastEpisode =
    update(episodeId) {
      it.copy(errorMessage = message, status = PodcastEpisodeStatus.FAILED)
    }

  private fun updateArticle(
    episodeId: String,
    position: Int,
    transform: (PodcastEpisodeArticle) -> PodcastEpisodeArticle,
  ): PodcastEpisode = update(episodeId) { episode ->
    episode.copy(
      articles = episode.articles.mapIndexed { index, article ->
        if ((article.chapterPosition ?: index) == position) transform(article) else article
      },
    )
  }

  private fun update(episodeId: String, transform: (PodcastEpisode) -> PodcastEpisode): PodcastEpisode =
    synchronized(episodes) {
      val index = episodes.indexOfFirst { it.id == episodeId }
      check(index >= 0)
      transform(episodes[index]).also { episodes[index] = it }
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

private class RecordingPodcastLogStore : PodcastGenerationLogStore {
  override val logs = MutableStateFlow<List<PodcastGenerationLog>>(emptyList())
  override fun record(log: PodcastGenerationLog) {
    logs.value = listOf(log) + logs.value
  }
  override fun clear(programId: String) {
    logs.value = logs.value.filterNot { it.programId == programId }
  }
}
