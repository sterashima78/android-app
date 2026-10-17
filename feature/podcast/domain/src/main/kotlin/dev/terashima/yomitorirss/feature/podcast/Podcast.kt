package dev.terashima.yomitorirss.feature.podcast

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

enum class PodcastGenerationProvider { LOCAL, CLOUD }

enum class PodcastClusteringMode { EVENT, TOPIC }

fun PodcastClusteringMode.alternative(): PodcastClusteringMode = when (this) {
  PodcastClusteringMode.EVENT -> PodcastClusteringMode.TOPIC
  PodcastClusteringMode.TOPIC -> PodcastClusteringMode.EVENT
}

data class PodcastSchedule(
  val enabled: Boolean = false,
  val hour: Int = 7,
  val minute: Int = 0,
) {
  init {
    require(hour in 0..23) { "hour must be between 0 and 23" }
    require(minute in 0..59) { "minute must be between 0 and 59" }
  }
}

data class PodcastSource(
  val id: String,
  val name: String,
  val feedUrl: String,
) {
  init {
    require(id.isNotBlank()) { "source id must not be blank" }
    require(name.isNotBlank()) { "source name must not be blank" }
    require(feedUrl.isNotBlank()) { "source feedUrl must not be blank" }
  }
}

data class PodcastProgram(
  val id: String,
  val name: String,
  val sourceIds: Set<String>,
  val provider: PodcastGenerationProvider,
  val schedule: PodcastSchedule = PodcastSchedule(),
  val maxArticlesPerEpisode: Int = 12,
  val exclusionPrompt: String = "",
  val clusteringMode: PodcastClusteringMode = PodcastClusteringMode.EVENT,
) {
  init {
    require(id.isNotBlank()) { "program id must not be blank" }
    require(name.isNotBlank()) { "program name must not be blank" }
    require(sourceIds.isNotEmpty()) { "program must contain at least one source" }
    require(maxArticlesPerEpisode in 1..50) { "maxArticlesPerEpisode must be between 1 and 50" }
  }
}

data class PodcastFeedEntry(
  val articleId: String,
  val feedId: String,
  val title: String,
  val sourceTitle: String?,
  val publishedAtEpochMillis: Long?,
  val articleUrl: String?,
  val feedContent: String,
  val categories: List<String> = emptyList(),
  val chapterPosition: Int? = null,
) {
  init {
    require(articleId.isNotBlank()) { "article id must not be blank" }
    require(feedId.isNotBlank()) { "source id must not be blank" }
    require(title.isNotBlank()) { "article title must not be blank" }
    require(articleUrl == null || articleUrl.isNotBlank()) { "article url must not be blank" }
    require(feedContent.isNotBlank()) { "feed content must not be blank" }
    require(chapterPosition == null || chapterPosition >= 0) { "chapterPosition must not be negative" }
  }
}

enum class PodcastChapterGenerationStatus { PENDING, GENERATING, READY, FAILED }
enum class PodcastClusteringStatus { SUCCESS, FALLBACK_INFERENCE_ERROR, FALLBACK_INVALID_OUTPUT, SKIPPED }

data class PodcastEpisodeArticle(
  val articleId: String,
  val feedId: String,
  val title: String,
  val sourceTitle: String?,
  val publishedAtEpochMillis: Long?,
  val articleUrl: String?,
  val feedContent: String,
  val chapterPosition: Int? = null,
  val chapterStatus: PodcastChapterGenerationStatus = PodcastChapterGenerationStatus.PENDING,
  val chapterScript: String? = null,
  val chapterError: String? = null,
)

data class PodcastPlaybackChapter(
  val number: Int,
  val article: PodcastEpisodeArticle?,
  val speechText: String,
  val articles: List<PodcastEpisodeArticle> = article?.let { listOf(it) }.orEmpty(),
)

enum class PodcastEpisodeStatus { QUEUED, GENERATING, READY, FAILED, ARCHIVED, DELETED }

data class PodcastEpisode(
  val id: String,
  val programId: String,
  val title: String,
  val createdAtEpochMillis: Long,
  val status: PodcastEpisodeStatus,
  val articles: List<PodcastEpisodeArticle>,
  val script: String? = null,
  val errorMessage: String? = null,
  val clusteringStatus: PodcastClusteringStatus? = null,
  val clusteringMode: PodcastClusteringMode = PodcastClusteringMode.EVENT,
  val lastPlayedChapter: Int? = null,
) {
  fun chapterGroups(): List<List<PodcastEpisodeArticle>> = articles
    .mapIndexed { index, article -> (article.chapterPosition ?: index) to article }
    .groupBy({ it.first }, { it.second })
    .toSortedMap()
    .values
    .toList()

  fun playbackChapters(): List<PodcastPlaybackChapter> {
    val speech = script?.trim().orEmpty()
    if (speech.isBlank()) return emptyList()

    val groups = chapterGroups()
    val matches = PODCAST_CHAPTER_MARKER.findAll(speech).toList()
    val chapterNumbers = matches.mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
    val expectedNumbers = groups.indices.map { it + 1 }
    if (matches.size != groups.size || chapterNumbers.sorted() != expectedNumbers) {
      return listOf(PodcastPlaybackChapter(1, null, speech, emptyList()))
    }

    val chapters = matches.mapIndexed { index, match ->
      val start = match.range.last + 1
      val endExclusive = matches.getOrNull(index + 1)?.range?.first ?: speech.length
      val chapterNumber = chapterNumbers[index]
      val segment = speech.substring(start, endExclusive).trim()
      val titleMatch = PODCAST_SPOKEN_TITLE_MARKER.find(segment)
      val spokenTitle = titleMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
      val chapterArticles = groups[chapterNumber - 1]
      val playbackArticle = chapterArticles.firstOrNull()?.let { article ->
        spokenTitle?.let { title -> article.copy(title = if (index == 0) title else "$PODCAST_TRANSITION_CUE$title") } ?: article
      }
      val chapterSpeech = titleMatch?.let { marker ->
        val beforeTitle = segment.substring(0, marker.range.first).trimEnd()
        val afterTitle = segment.substring(marker.range.last + 1).trimStart()
        listOf(beforeTitle, stripRepeatedSpokenTitle(afterTitle, spokenTitle))
          .filter(String::isNotBlank)
          .joinToString(" ")
          .trim()
      } ?: segment
      PodcastPlaybackChapter(index + 1, playbackArticle, chapterSpeech, chapterArticles)
    }
    return chapters.takeIf { it.none { chapter -> chapter.speechText.isBlank() } }
      ?: listOf(PodcastPlaybackChapter(1, null, speech, emptyList()))
  }
}

enum class PodcastGenerationTaskState { QUEUED, RUNNING, FAILED }

data class PodcastGenerationTask(
  val episodeId: String,
  val title: String,
  val programName: String,
  val provider: PodcastGenerationProvider,
  val state: PodcastGenerationTaskState,
  val completedChapters: Int,
  val totalChapters: Int,
  val error: String?,
  val createdAtEpochMillis: Long,
)

interface PodcastGenerationTaskReader {
  suspend fun listGenerationTasks(): List<PodcastGenerationTask>
}

interface PodcastFeedContentSource {
  suspend fun latestEntries(sources: List<PodcastSource>, limit: Int): List<PodcastFeedEntry>
}

interface PodcastCandidateFilter {
  suspend fun availableEntries(programId: String, candidates: List<PodcastFeedEntry>): List<PodcastFeedEntry>
}

object AllPodcastCandidates : PodcastCandidateFilter {
  override suspend fun availableEntries(
    programId: String,
    candidates: List<PodcastFeedEntry>,
  ): List<PodcastFeedEntry> = candidates
}

interface PodcastRepository {
  val changes: Flow<Unit>
    get() = emptyFlow()

  suspend fun listSources(): List<PodcastSource>
  suspend fun findSource(sourceId: String): PodcastSource?
  suspend fun saveSource(source: PodcastSource)
  suspend fun deleteSource(sourceId: String)
  suspend fun listPrograms(): List<PodcastProgram>
  suspend fun findProgram(programId: String): PodcastProgram?
  suspend fun saveProgram(program: PodcastProgram)
  suspend fun deleteProgram(programId: String)
  suspend fun recordExcludedEntries(programId: String, entries: List<PodcastFeedEntry>, excludedAtEpochMillis: Long)
  suspend fun listEpisodes(programId: String): List<PodcastEpisode>
  /** Saved articles from archived episodes created since [sinceEpochMillis], scoped to one program. */
  suspend fun listRecentArchivedArticles(programId: String, sinceEpochMillis: Long): List<PodcastFeedEntry> =
    listEpisodes(programId)
      .asSequence()
      .filter { it.status == PodcastEpisodeStatus.ARCHIVED && it.createdAtEpochMillis >= sinceEpochMillis }
      .flatMap { it.articles.asSequence() }
      .map { article ->
        PodcastFeedEntry(
          articleId = article.articleId,
          feedId = article.feedId,
          title = article.title,
          sourceTitle = article.sourceTitle,
          publishedAtEpochMillis = article.publishedAtEpochMillis,
          articleUrl = article.articleUrl,
          feedContent = article.feedContent,
        )
      }
      .distinctBy(PodcastFeedEntry::articleId)
      .toList()
  suspend fun findEpisode(episodeId: String): PodcastEpisode?
  suspend fun archiveEpisode(episodeId: String): PodcastEpisode
  suspend fun restoreEpisode(episodeId: String): PodcastEpisode
  suspend fun recordPlaybackChapter(episodeId: String, chapterNumber: Int)
  suspend fun deleteEpisode(episodeId: String)

  suspend fun findInterruptedGenerationEpisode(programId: String): PodcastEpisode? =
    listEpisodes(programId).asSequence()
      .filter { it.status == PodcastEpisodeStatus.GENERATING }
      .minWithOrNull(compareBy<PodcastEpisode> { it.createdAtEpochMillis }.thenBy { it.id })

  suspend fun claimPendingEpisode(programId: String): PodcastEpisode?
  suspend fun reserveEpisode(program: PodcastProgram, candidates: List<PodcastFeedEntry>, createdAtEpochMillis: Long): PodcastEpisode?
  suspend fun reserveEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
    clusteringStatus: PodcastClusteringStatus?,
  ): PodcastEpisode? = reserveEpisode(program, candidates, createdAtEpochMillis)
  suspend fun reserveComparisonEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
    clusteringStatus: PodcastClusteringStatus,
  ): PodcastEpisode = error("Comparison episode support is unavailable")
  suspend fun prepareEpisodeRetry(episodeId: String): PodcastEpisode
  suspend fun prepareEpisodeRebuild(
    episodeId: String,
    candidates: List<PodcastFeedEntry>,
    clusteringStatus: PodcastClusteringStatus,
  ): PodcastEpisode
  suspend fun markChapterGenerating(episodeId: String, position: Int): PodcastEpisode
  suspend fun completeChapter(episodeId: String, position: Int, script: String): PodcastEpisode
  suspend fun failChapter(episodeId: String, position: Int, message: String): PodcastEpisode
  suspend fun completeEpisode(episodeId: String, title: String, script: String): PodcastEpisode
  suspend fun failEpisode(episodeId: String, message: String): PodcastEpisode
}

interface PodcastScriptGenerator {
  suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String
}

data class PodcastNewsExclusionResult(
  val included: List<PodcastFeedEntry>,
  val excluded: List<PodcastFeedEntry>,
)

interface PodcastNewsExcluder {
  suspend fun filter(
    provider: PodcastGenerationProvider,
    exclusionPrompt: String,
    candidates: List<PodcastFeedEntry>,
  ): PodcastNewsExclusionResult
}

object IncludeAllPodcastNews : PodcastNewsExcluder {
  override suspend fun filter(
    provider: PodcastGenerationProvider,
    exclusionPrompt: String,
    candidates: List<PodcastFeedEntry>,
  ): PodcastNewsExclusionResult = PodcastNewsExclusionResult(candidates, emptyList())
}

data class PodcastNewsClusteringResult(
  val groups: List<List<Int>>,
  val status: PodcastClusteringStatus,
)

interface PodcastNewsClusterer {
  suspend fun cluster(
    provider: PodcastGenerationProvider,
    candidates: List<PodcastFeedEntry>,
  ): PodcastNewsClusteringResult
}

object SingletonPodcastNewsClusterer : PodcastNewsClusterer {
  override suspend fun cluster(
    provider: PodcastGenerationProvider,
    candidates: List<PodcastFeedEntry>,
  ): PodcastNewsClusteringResult = PodcastNewsClusteringResult(
    groups = candidates.indices.map { listOf(it) },
    status = PodcastClusteringStatus.SKIPPED,
  )
}

interface PodcastNewsCategorizer {
  suspend fun categorize(
    provider: PodcastGenerationProvider,
    news: List<List<PodcastFeedEntry>>,
    existingCategories: Set<String>,
  ): List<String>
}

object OtherPodcastNewsCategorizer : PodcastNewsCategorizer {
  override suspend fun categorize(
    provider: PodcastGenerationProvider,
    news: List<List<PodcastFeedEntry>>,
    existingCategories: Set<String>,
  ): List<String> = List(news.size) { PODCAST_OTHER_CATEGORY }
}

const val PODCAST_OTHER_CATEGORY = "その他"

internal fun selectPodcastFeedCategories(
  news: List<List<PodcastFeedEntry>>,
  supplementalCategories: Map<Int, String> = emptyMap(),
): List<String?> {
  val categoriesByNews = news.mapIndexed { index, articles ->
    val feedCategories = articles.asSequence()
      .flatMap { it.categories.asSequence() }
      .map(::normalizePodcastCategory)
      .filter(String::isNotBlank)
      .distinct()
      .toList()
    if (feedCategories.isNotEmpty()) {
      feedCategories
    } else {
      listOfNotNull(
        supplementalCategories[index]
          ?.let(::normalizePodcastCategory)
          ?.takeIf(String::isNotBlank),
      )
    }
  }
  val categoryCounts = categoriesByNews.flatten().groupingBy { it }.eachCount()
  return categoriesByNews.map { categories ->
    categories.minWithOrNull(
      compareBy<String> { categoryCounts.getValue(it) }
        .thenByDescending { category -> category.count { it == '/' } }
        .thenBy { it.lowercase(Locale.ROOT) }
        .thenBy { it },
    )
  }
}

internal fun orderPodcastNewsByCategory(
  news: List<List<PodcastFeedEntry>>,
  categories: List<String>,
): List<List<PodcastFeedEntry>> {
  require(news.size == categories.size) { "one category is required per news cluster" }
  val grouped = linkedMapOf<String, MutableList<List<PodcastFeedEntry>>>()
  news.forEachIndexed { index, articles ->
    val category = normalizePodcastCategory(categories[index]).ifBlank { PODCAST_OTHER_CATEGORY }
    grouped.getOrPut(category) { mutableListOf() } += articles
  }
  return grouped.values.flatten()
}

private fun normalizePodcastCategory(category: String): String =
  Normalizer.normalize(category, Normalizer.Form.NFKC)
    .trim()
    .replace(Regex("\\s+"), " ")


private val PODCAST_CHAPTER_MARKER = Regex("""(?m)^\[\[CHAPTER:(\d+)\]\]\s*$""")
private val PODCAST_SPOKEN_TITLE_MARKER = Regex("""\[\[TITLE:([^\]\r\n]+)\]\]""")
private const val PODCAST_TRANSITION_CUE = "続いて。"
