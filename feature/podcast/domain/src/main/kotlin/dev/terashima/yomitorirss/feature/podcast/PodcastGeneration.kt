package dev.terashima.yomitorirss.feature.podcast

import java.util.concurrent.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

data class PodcastGenerationProgress(
  val completedChapters: Int,
  val totalChapters: Int,
) {
  init {
    require(totalChapters >= 0) { "totalChapters must not be negative" }
    require(completedChapters in 0..totalChapters) { "completedChapters must be within totalChapters" }
  }
}

class PodcastGenerationAlreadyRunningException(programId: String) :
  IllegalStateException("podcast generation already in progress: $programId")

sealed interface PodcastGenerationResult {
  data class Generated(val episode: PodcastEpisode) : PodcastGenerationResult
  data object NoNewArticles : PodcastGenerationResult
}

private data class PodcastClusteredCandidates(
  val entries: List<PodcastFeedEntry>,
  val status: PodcastClusteringStatus,
)

private data class PodcastArchiveComparison(
  val entries: List<PodcastFeedEntry>,
  val repeatedEntries: List<PodcastFeedEntry> = emptyList(),
  val eventClustering: PodcastNewsClusteringResult? = null,
  val repeatedEventClustering: PodcastNewsClusteringResult? = null,
)

class GeneratePodcastEpisodeUseCase(
  private val repository: PodcastRepository,
  private val feedContentSource: PodcastFeedContentSource,
  private val scriptGenerator: PodcastScriptGenerator,
  private val nowEpochMillis: () -> Long = System::currentTimeMillis,
  private val newsClusterer: PodcastNewsClusterer = SingletonPodcastNewsClusterer,
  private val topicClusterer: PodcastNewsClusterer = SingletonPodcastNewsClusterer,
  private val newsCategorizer: PodcastNewsCategorizer = OtherPodcastNewsCategorizer,
  private val newsExcluder: PodcastNewsExcluder = IncludeAllPodcastNews,
  private val candidateFilter: PodcastCandidateFilter = AllPodcastCandidates,
) {
  private val activeProgramIds = mutableSetOf<String>()

  suspend fun generate(
    programId: String,
    onProgress: suspend (PodcastGenerationProgress) -> Unit = {},
  ): PodcastGenerationResult = generate(programId, null, onProgress)

  suspend fun generate(
    programId: String,
    modeOverride: PodcastClusteringMode?,
    onProgress: suspend (PodcastGenerationProgress) -> Unit = {},
  ): PodcastGenerationResult = withProgramGeneration(programId) {
    val savedProgram = requireNotNull(repository.findProgram(programId)) { "program not found: $programId" }
    val program = savedProgram.copy(clusteringMode = modeOverride ?: savedProgram.clusteringMode)
    repository.claimPendingEpisode(programId)?.let { return@withProgramGeneration generateReserved(program, it, onProgress) }
    val sources = repository.listSources().filter { it.id in program.sourceIds }
    require(sources.mapTo(mutableSetOf(), PodcastSource::id) == program.sourceIds) {
      "番組に利用できないソースがあります。番組設定を確認してください"
    }
    val candidates = feedContentSource.latestEntries(sources, Int.MAX_VALUE)
    val availableCandidates = candidateFilter.availableEntries(program.id, candidates)
    if (availableCandidates.isEmpty()) return@withProgramGeneration PodcastGenerationResult.NoNewArticles
    val exclusion = if (program.exclusionPrompt.isBlank()) {
      PodcastNewsExclusionResult(availableCandidates, emptyList())
    } else {
      validateExclusionResult(
        availableCandidates,
        newsExcluder.filter(program.provider, program.exclusionPrompt, availableCandidates),
      )
    }
    if (exclusion.excluded.isNotEmpty()) {
      repository.recordExcludedEntries(program.id, exclusion.excluded, nowEpochMillis())
    }
    if (exclusion.included.isEmpty()) return@withProgramGeneration PodcastGenerationResult.NoNewArticles
    val compared = compareRecentlyCoveredNews(program, exclusion.included)
    val fresh = compared.entries.takeIf { it.isNotEmpty() }
      ?.let { clusterCandidates(program, it, compared.eventClustering) }
    val repeated = compared.repeatedEntries.takeIf { it.isNotEmpty() }
      ?.let { clusterCandidates(program, it, compared.repeatedEventClustering) }
    val reserved = repository.reserveEpisode(
      program = program,
      candidates = fresh?.entries.orEmpty(),
      createdAtEpochMillis = nowEpochMillis(),
      clusteringStatus = fresh?.status,
      repeatedCandidates = repeated?.entries.orEmpty(),
      repeatedClusteringStatus = repeated?.status,
    ) ?: return@withProgramGeneration PodcastGenerationResult.NoNewArticles
    generateReserved(program, reserved, onProgress)
  }

  suspend fun retry(
    episodeId: String,
    onProgress: suspend (PodcastGenerationProgress) -> Unit = {},
  ): PodcastGenerationResult.Generated {
    val initial = requireNotNull(repository.findEpisode(episodeId)) { "episode not found: $episodeId" }
    return withProgramGeneration(initial.programId) {
      val episode = requireNotNull(repository.findEpisode(episodeId)) { "episode not found: $episodeId" }
      require(episode.status == PodcastEpisodeStatus.FAILED) { "only failed episodes can be retried" }
      val program = requireNotNull(repository.findProgram(episode.programId)) { "program not found: ${episode.programId}" }
      generateReserved(program, repository.prepareEpisodeRetry(episode.id), onProgress)
    }
  }

  suspend fun regenerate(
    episodeId: String,
    onProgress: suspend (PodcastGenerationProgress) -> Unit = {},
  ): PodcastGenerationResult.Generated {
    val initial = requireNotNull(repository.findEpisode(episodeId)) { "episode not found: $episodeId" }
    return withProgramGeneration(initial.programId) {
      val episode = requireNotNull(repository.findEpisode(episodeId)) { "episode not found: $episodeId" }
      require(episode.status == PodcastEpisodeStatus.READY || episode.status == PodcastEpisodeStatus.FAILED) {
        "only ready or failed episodes can be regenerated"
      }
      val program = requireNotNull(repository.findProgram(episode.programId)) { "program not found: ${episode.programId}" }
      val candidates = episode.articles.map(PodcastEpisodeArticle::toFeedEntry)
      val exclusion = if (program.exclusionPrompt.isBlank()) {
        PodcastNewsExclusionResult(candidates, emptyList())
      } else {
        validateExclusionResult(
          candidates,
          newsExcluder.filter(program.provider, program.exclusionPrompt, candidates),
        )
      }
      require(exclusion.included.isNotEmpty()) { "現在の除外条件では再生成対象の記事がありません" }
      val clustered = clusterCandidates(program.copy(clusteringMode = episode.clusteringMode), exclusion.included)
      val prepared = repository.prepareEpisodeRebuild(
        episodeId = episode.id,
        candidates = clustered.entries,
        clusteringStatus = clustered.status,
      )
      generateReserved(program, prepared, onProgress)
    }
  }

  suspend fun compare(
    episodeId: String,
    mode: PodcastClusteringMode,
    onProgress: suspend (PodcastGenerationProgress) -> Unit = {},
  ): PodcastGenerationResult.Generated {
    val original = requireNotNull(repository.findEpisode(episodeId)) { "episode not found: $episodeId" }
    return withProgramGeneration(original.programId) {
      val source = requireNotNull(repository.findEpisode(episodeId)) { "episode not found: $episodeId" }
      require(source.status == PodcastEpisodeStatus.READY) { "only ready episodes can be compared" }
      require(source.clusteringMode != mode) { "comparison must use the alternative mode" }
      val savedProgram = requireNotNull(repository.findProgram(source.programId)) { "program not found" }
      val program = savedProgram.copy(clusteringMode = mode)
      val clustered = clusterCandidates(program, source.articles.map(PodcastEpisodeArticle::toFeedEntry))
      val comparison = repository.reserveComparisonEpisode(program, clustered.entries, nowEpochMillis(), clustered.status)
      generateReserved(program, comparison, onProgress)
    }
  }

  suspend fun resumeInterrupted(
    programId: String,
    onProgress: suspend (PodcastGenerationProgress) -> Unit = {},
  ): PodcastGenerationResult.Generated? = withProgramGeneration(programId) {
    val program = requireNotNull(repository.findProgram(programId)) { "program not found: $programId" }
    val interrupted = repository.findInterruptedGenerationEpisode(programId) ?: return@withProgramGeneration null
    generateReserved(program, interrupted, onProgress)
  }

  private fun validateExclusionResult(
    candidates: List<PodcastFeedEntry>,
    result: PodcastNewsExclusionResult,
  ): PodcastNewsExclusionResult {
    val expected = candidates.groupingBy { it }.eachCount()
    val classified = (result.included + result.excluded).groupingBy { it }.eachCount()
    val overlap = result.included.toSet().intersect(result.excluded.toSet())
    return if (classified == expected && overlap.isEmpty()) {
      result
    } else {
      PodcastNewsExclusionResult(candidates, emptyList())
    }
  }

  /** Separate past archived coverage using event identity, never broad topical similarity. */
  private suspend fun compareRecentlyCoveredNews(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
  ): PodcastArchiveComparison {
    val recentArchive = repository.listRecentArchivedArticles(
      programId = program.id,
      sinceEpochMillis = nowEpochMillis() - RECENT_PODCAST_ARCHIVE_MILLIS,
    )
    if (recentArchive.isEmpty()) return PodcastArchiveComparison(candidates)

    // A normal-sized comparison retains its complete event partition for reuse below.
    // Larger inputs are compared in bounded batches without losing archive coverage.
    if (recentArchive.size + candidates.size > MAX_ARCHIVE_COMPARISON_ITEMS) {
      return compareRecentArchiveInBatches(program, recentArchive, candidates)
    }

    val comparison = try {
      newsClusterer.cluster(program.provider, recentArchive + candidates)
    } catch (error: CancellationException) {
      throw error
    } catch (_: Exception) {
      // Uncertain history must not silently discard newly available entries.
      return PodcastArchiveComparison(candidates)
    }
    val originalCount = recentArchive.size + candidates.size
    if (comparison.status != PodcastClusteringStatus.SUCCESS ||
      comparison.groups.flatten().toSet() != (0 until originalCount).toSet() ||
      comparison.groups.sumOf { it.size } != originalCount
    ) {
      return PodcastArchiveComparison(candidates)
    }

    val newGroups = comparison.groups
      .filter { group -> group.none { it < recentArchive.size } }
      .map { group -> group.map { it - recentArchive.size } }
    val repeatedGroups = comparison.groups
      .filter { group -> group.any { it < recentArchive.size } }
      .map { group -> group.filter { it >= recentArchive.size }.map { it - recentArchive.size } }
      .filter { it.isNotEmpty() }

    fun remap(groups: List<List<Int>>): Pair<List<PodcastFeedEntry>, PodcastNewsClusteringResult> {
      val indexes = groups.flatten().toSet()
      val selected = candidates.filterIndexed { index, _ -> index in indexes }
      val remappedIndexes = candidates.indices.filter { it in indexes }
        .withIndex().associate { (newIndex, oldIndex) -> oldIndex to newIndex }
      return selected to PodcastNewsClusteringResult(
        groups = groups.map { group -> group.map { requireNotNull(remappedIndexes[it]) } },
        status = comparison.status,
      )
    }
    val (newEntries, newClustering) = remap(newGroups)
    val (repeatedEntries, repeatedClustering) = remap(repeatedGroups)
    return PodcastArchiveComparison(
      entries = newEntries,
      repeatedEntries = repeatedEntries,
      eventClustering = newClustering,
      repeatedEventClustering = repeatedClustering,
    )
  }

  private suspend fun compareRecentArchiveInBatches(
    program: PodcastProgram,
    archive: List<PodcastFeedEntry>,
    candidates: List<PodcastFeedEntry>,
  ): PodcastArchiveComparison {
    val archiveBatches = archive.chunked(ARCHIVE_COMPARISON_BATCH_SIZE)
    val candidateBatches = candidates.chunked(ARCHIVE_COMPARISON_BATCH_SIZE)
    // Never launch an unbounded series of model calls merely to eliminate repeats.
    // When the comparison budget is exceeded, fail open and retain all new entries.
    if (candidateBatches.size > MAX_ARCHIVE_COMPARISON_CALLS ||
      archiveBatches.size > MAX_ARCHIVE_COMPARISON_CALLS / candidateBatches.size
    ) {
      return PodcastArchiveComparison(candidates)
    }

    val covered = mutableSetOf<Int>()
    for (archiveBatch in archiveBatches) {
      for ((candidateIndex, candidateBatch) in candidateBatches.withIndex()) {
        val combined = archiveBatch + candidateBatch
        val result = try {
          newsClusterer.cluster(program.provider, combined)
        } catch (error: CancellationException) {
          throw error
        } catch (_: Exception) {
          return PodcastArchiveComparison(candidates)
        }
        if (result.status != PodcastClusteringStatus.SUCCESS ||
          result.groups.flatten().toSet() != combined.indices.toSet() ||
          result.groups.sumOf { it.size } != combined.size
        ) {
          return PodcastArchiveComparison(candidates)
        }
        result.groups
          .filter { group -> group.any { it < archiveBatch.size } }
          .flatMap { group -> group.filter { it >= archiveBatch.size } }
          .forEach { index ->
            covered += candidateIndex * ARCHIVE_COMPARISON_BATCH_SIZE + index - archiveBatch.size
          }
      }
    }
    // Batch partitions cannot be reused as a global partition; cluster both segments again.
    return PodcastArchiveComparison(
      entries = candidates.filterIndexed { index, _ -> index !in covered },
      repeatedEntries = candidates.filterIndexed { index, _ -> index in covered },
    )
  }

  private suspend fun clusterCandidates(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    preclassifiedEvents: PodcastNewsClusteringResult? = null,
  ): PodcastClusteredCandidates {
    val classifier = if (program.clusteringMode == PodcastClusteringMode.TOPIC) topicClusterer else newsClusterer
    val result = if (program.clusteringMode == PodcastClusteringMode.EVENT && preclassifiedEvents != null) {
      preclassifiedEvents
    } else {
      classifier.cluster(program.provider, candidates)
    }
    val groups = result.groups
    require(groups.flatten().toSet() == candidates.indices.toSet() && groups.sumOf { it.size } == candidates.size) {
      "news clusterer must contain every candidate exactly once"
    }

    val news = groups.map { indexes -> indexes.map(candidates::get) }
    val preliminaryCategories = selectPodcastFeedCategories(news)
    val uncategorizedIndexes = preliminaryCategories.indices.filter { preliminaryCategories[it] == null }
    val supplementalCategories = mutableMapOf<Int, String>()
    if (uncategorizedIndexes.isNotEmpty()) {
      val uncategorizedNews = uncategorizedIndexes.map(news::get)
      val generatedCategories = try {
        newsCategorizer.categorize(
          provider = program.provider,
          news = uncategorizedNews,
          existingCategories = preliminaryCategories.filterNotNull().toSet(),
        )
      } catch (error: CancellationException) {
        throw error
      } catch (_: Throwable) {
        List(uncategorizedNews.size) { PODCAST_OTHER_CATEGORY }
      }.takeIf { generated ->
        generated.size == uncategorizedNews.size && generated.all { it.isNotBlank() }
      } ?: List(uncategorizedNews.size) { PODCAST_OTHER_CATEGORY }

      uncategorizedIndexes.zip(generatedCategories).forEach { (index, category) ->
        supplementalCategories[index] = category
      }
    }

    val categories = selectPodcastFeedCategories(news, supplementalCategories)
      .map { it ?: PODCAST_OTHER_CATEGORY }
    val orderedNews = orderPodcastNewsByCategory(news, categories)
    return PodcastClusteredCandidates(
      entries = orderedNews.flatMapIndexed { chapterPosition, articles ->
        articles.map { article -> article.copy(chapterPosition = chapterPosition) }
      },
      status = result.status,
    )
  }

  private suspend fun generateReserved(
    program: PodcastProgram,
    initialEpisode: PodcastEpisode,
    onProgress: suspend (PodcastGenerationProgress) -> Unit,
  ): PodcastGenerationResult.Generated {
    val episodeId = initialEpisode.id
    return try {
      val groups = initialEpisode.chapterGroups()
      val totalChapters = groups.size
      val pendingPositions = groups.mapIndexedNotNull { position, group ->
        val current = group.first()
        position.takeUnless {
          current.chapterStatus == PodcastChapterGenerationStatus.READY && !current.chapterScript.isNullOrBlank()
        }
      }

      val progressMutex = Mutex()
      var completedChapters = totalChapters - pendingPositions.size
      reportProgress(onProgress, PodcastGenerationProgress(completedChapters, totalChapters))
      val chapterCompleted: suspend () -> Unit = {
        progressMutex.withLock {
          completedChapters += 1
          reportProgress(onProgress, PodcastGenerationProgress(completedChapters, totalChapters))
        }
      }

      if (program.provider == PodcastGenerationProvider.CLOUD && pendingPositions.size > 1) {
        val failures = coroutineScope {
          val semaphore = Semaphore(CLOUD_CHAPTER_PARALLELISM)
          pendingPositions.map { position ->
            async {
              semaphore.withPermit {
                try {
                  generateChapter(program, episodeId, position, totalChapters, chapterCompleted)
                  null
                } catch (error: CancellationException) {
                  throw error
                } catch (error: Throwable) {
                  error
                }
              }
            }
          }.awaitAll()
        }
        failures.filterNotNull().firstOrNull()?.let { throw it }
      } else {
        pendingPositions.forEach { position ->
          generateChapter(program, episodeId, position, totalChapters, chapterCompleted)
        }
      }

      val episode = requireNotNull(repository.findEpisode(episodeId)) { "episode not found: $episodeId" }
      val script = buildPodcastEpisodeScriptFromChapters(program.name, episode.chapterGroups())
      // Newly reserved episodes already carry their durable display title.
      // Legacy pending episodes still store only the program name.
      val title = initialEpisode.title.takeUnless { it == program.name }
        ?: buildEpisodeTitle(program.name, episode.createdAtEpochMillis)
      PodcastGenerationResult.Generated(repository.completeEpisode(episode.id, title, script))
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      val message = error.message ?: error::class.simpleName ?: "generation failed"
      runCatching { repository.failEpisode(episodeId, message) }
      throw error
    }
  }

  private suspend fun generateChapter(
    program: PodcastProgram,
    episodeId: String,
    position: Int,
    totalChapters: Int,
    onCompleted: suspend () -> Unit = {},
  ) {
    try {
      val episode = repository.markChapterGenerating(episodeId, position)
      val group = episode.chapterGroups()[position]
      val chapterScript = scriptGenerator.generate(
        program.provider,
        buildPodcastChapterPrompt(program.name, group, position + 1, totalChapters, episode.clusteringMode),
      ).trim()
      require(chapterScript.isNotBlank()) { "generated podcast chapter is blank" }
      repository.completeChapter(episodeId, position, chapterScript)
      onCompleted()
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      val message = error.message ?: error::class.simpleName ?: "generation failed"
      runCatching { repository.failChapter(episodeId, position, message) }
      throw error
    }
  }

  private suspend fun reportProgress(
    onProgress: suspend (PodcastGenerationProgress) -> Unit,
    progress: PodcastGenerationProgress,
  ) {
    try {
      onProgress(progress)
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      // Progress is an observational projection. Failure to publish it must not corrupt chapter state.
    }
  }

  private suspend fun <T> withProgramGeneration(programId: String, block: suspend () -> T): T {
    if (!synchronized(activeProgramIds) { activeProgramIds.add(programId) }) {
      throw PodcastGenerationAlreadyRunningException(programId)
    }
    return try { block() } finally { synchronized(activeProgramIds) { activeProgramIds.remove(programId) } }
  }
}

private fun PodcastEpisodeArticle.toFeedEntry(): PodcastFeedEntry = PodcastFeedEntry(
  articleId = articleId,
  feedId = feedId,
  title = title,
  sourceTitle = sourceTitle,
  publishedAtEpochMillis = publishedAtEpochMillis,
  articleUrl = articleUrl,
  feedContent = feedContent,
)

private const val CLOUD_CHAPTER_PARALLELISM = 3
private const val RECENT_PODCAST_ARCHIVE_MILLIS = 2L * 24 * 60 * 60 * 1_000

private const val MAX_ARCHIVE_COMPARISON_ITEMS = 24
private const val ARCHIVE_COMPARISON_BATCH_SIZE = 12
private const val MAX_ARCHIVE_COMPARISON_CALLS = 8
