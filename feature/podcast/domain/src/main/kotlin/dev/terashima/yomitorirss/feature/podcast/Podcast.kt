package dev.terashima.yomitorirss.feature.podcast

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CancellationException

enum class PodcastGenerationProvider { LOCAL, CLOUD }

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
  val articleUrl: String,
  val feedContent: String,
  val chapterPosition: Int? = null,
) {
  init {
    require(articleId.isNotBlank()) { "article id must not be blank" }
    require(feedId.isNotBlank()) { "source id must not be blank" }
    require(title.isNotBlank()) { "article title must not be blank" }
    require(articleUrl.isNotBlank()) { "article url must not be blank" }
    require(feedContent.isNotBlank()) { "feed content must not be blank" }
    require(chapterPosition == null || chapterPosition >= 0) { "chapterPosition must not be negative" }
  }
}

enum class PodcastChapterGenerationStatus { PENDING, GENERATING, READY, FAILED }
enum class PodcastRegenerationStatus { RUNNING, FAILED }
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
  val regenerationStatus: PodcastRegenerationStatus? = null,
  val clusteringStatus: PodcastClusteringStatus? = null,
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
  suspend fun unconsumedEntries(programId: String, candidates: List<PodcastFeedEntry>): List<PodcastFeedEntry>
}

object AllPodcastCandidates : PodcastCandidateFilter {
  override suspend fun unconsumedEntries(
    programId: String,
    candidates: List<PodcastFeedEntry>,
  ): List<PodcastFeedEntry> = candidates
}

interface PodcastRepository {
  suspend fun listSources(): List<PodcastSource>
  suspend fun findSource(sourceId: String): PodcastSource?
  suspend fun saveSource(source: PodcastSource)
  suspend fun deleteSource(sourceId: String)
  suspend fun listPrograms(): List<PodcastProgram>
  suspend fun findProgram(programId: String): PodcastProgram?
  suspend fun saveProgram(program: PodcastProgram)
  suspend fun deleteProgram(programId: String)
  suspend fun listEpisodes(programId: String): List<PodcastEpisode>
  suspend fun findEpisode(episodeId: String): PodcastEpisode?
  suspend fun archiveEpisode(episodeId: String): PodcastEpisode
  suspend fun restoreEpisode(episodeId: String): PodcastEpisode
  suspend fun deleteEpisode(episodeId: String)

  suspend fun findInterruptedGenerationEpisode(programId: String): PodcastEpisode? =
    listEpisodes(programId).asSequence()
      .filter { it.status == PodcastEpisodeStatus.GENERATING || it.regenerationStatus == PodcastRegenerationStatus.RUNNING }
      .minWithOrNull(compareBy<PodcastEpisode> { it.createdAtEpochMillis }.thenBy { it.id })

  suspend fun claimPendingEpisode(programId: String): PodcastEpisode?
  suspend fun reserveEpisode(program: PodcastProgram, candidates: List<PodcastFeedEntry>, createdAtEpochMillis: Long): PodcastEpisode?
  suspend fun reserveEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
    clusteringStatus: PodcastClusteringStatus?,
  ): PodcastEpisode? = reserveEpisode(program, candidates, createdAtEpochMillis)
  suspend fun prepareEpisodeRetry(episodeId: String): PodcastEpisode
  suspend fun prepareEpisodeRegeneration(episodeId: String): PodcastEpisode
  suspend fun markChapterGenerating(episodeId: String, position: Int): PodcastEpisode
  suspend fun completeChapter(episodeId: String, position: Int, script: String): PodcastEpisode
  suspend fun failChapter(episodeId: String, position: Int, message: String): PodcastEpisode
  suspend fun failRegeneration(episodeId: String, message: String): PodcastEpisode
  suspend fun completeEpisode(episodeId: String, title: String, script: String): PodcastEpisode
  suspend fun failEpisode(episodeId: String, message: String): PodcastEpisode
}

interface PodcastScriptGenerator {
  suspend fun generate(provider: PodcastGenerationProvider, prompt: String): String
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

sealed interface PodcastGenerationResult {
  data class Generated(val episode: PodcastEpisode) : PodcastGenerationResult
  data object NoNewArticles : PodcastGenerationResult
}

private data class PodcastClusteredCandidates(
  val entries: List<PodcastFeedEntry>,
  val status: PodcastClusteringStatus,
)

class GeneratePodcastEpisodeUseCase(
  private val repository: PodcastRepository,
  private val feedContentSource: PodcastFeedContentSource,
  private val scriptGenerator: PodcastScriptGenerator,
  private val nowEpochMillis: () -> Long = System::currentTimeMillis,
  private val newsClusterer: PodcastNewsClusterer = SingletonPodcastNewsClusterer,
  private val candidateFilter: PodcastCandidateFilter = AllPodcastCandidates,
) {
  private val activeProgramIds = mutableSetOf<String>()

  suspend fun generate(programId: String): PodcastGenerationResult = withProgramGeneration(programId) {
    val program = requireNotNull(repository.findProgram(programId)) { "program not found: $programId" }
    repository.claimPendingEpisode(programId)?.let { return@withProgramGeneration generateReserved(program, it) }
    val sources = repository.listSources().filter { it.id in program.sourceIds }
    require(sources.mapTo(mutableSetOf(), PodcastSource::id) == program.sourceIds) {
      "番組に利用できないソースがあります。番組設定を確認してください"
    }
    val candidates = feedContentSource.latestEntries(sources, Int.MAX_VALUE)
    val unconsumedCandidates = candidateFilter.unconsumedEntries(program.id, candidates)
    if (unconsumedCandidates.isEmpty()) return@withProgramGeneration PodcastGenerationResult.NoNewArticles
    val clustered = clusterCandidates(program, unconsumedCandidates)
    val reserved = repository.reserveEpisode(
      program = program,
      candidates = clustered.entries,
      createdAtEpochMillis = nowEpochMillis(),
      clusteringStatus = clustered.status,
    ) ?: return@withProgramGeneration PodcastGenerationResult.NoNewArticles
    generateReserved(program, reserved)
  }

  suspend fun retry(episodeId: String): PodcastGenerationResult.Generated {
    val initial = requireNotNull(repository.findEpisode(episodeId)) { "episode not found: $episodeId" }
    return withProgramGeneration(initial.programId) {
      val episode = requireNotNull(repository.findEpisode(episodeId)) { "episode not found: $episodeId" }
      require(episode.status == PodcastEpisodeStatus.FAILED) { "only failed episodes can be retried" }
      val program = requireNotNull(repository.findProgram(episode.programId)) { "program not found: ${episode.programId}" }
      generateReserved(program, repository.prepareEpisodeRetry(episode.id))
    }
  }

  suspend fun regenerate(episodeId: String): PodcastGenerationResult.Generated {
    val initial = requireNotNull(repository.findEpisode(episodeId)) { "episode not found: $episodeId" }
    return withProgramGeneration(initial.programId) {
      val episode = requireNotNull(repository.findEpisode(episodeId)) { "episode not found: $episodeId" }
      require(episode.status == PodcastEpisodeStatus.READY || episode.status == PodcastEpisodeStatus.FAILED) {
        "only ready or failed episodes can be regenerated"
      }
      val program = requireNotNull(repository.findProgram(episode.programId)) { "program not found: ${episode.programId}" }
      val prepared = if (episode.status == PodcastEpisodeStatus.READY) repository.prepareEpisodeRegeneration(episode.id)
      else repository.prepareEpisodeRetry(episode.id)
      generateReserved(program, prepared)
    }
  }

  suspend fun resumeInterrupted(programId: String): PodcastGenerationResult.Generated? = withProgramGeneration(programId) {
    val program = requireNotNull(repository.findProgram(programId)) { "program not found: $programId" }
    val interrupted = repository.findInterruptedGenerationEpisode(programId) ?: return@withProgramGeneration null
    generateReserved(program, interrupted)
  }

  private suspend fun clusterCandidates(program: PodcastProgram, candidates: List<PodcastFeedEntry>): PodcastClusteredCandidates {
    val result = newsClusterer.cluster(program.provider, candidates)
    val groups = result.groups
    require(groups.flatten().toSet() == candidates.indices.toSet() && groups.sumOf { it.size } == candidates.size) {
      "news clusterer must contain every candidate exactly once"
    }
    return PodcastClusteredCandidates(
      entries = groups.flatMapIndexed { chapterPosition, indexes ->
        indexes.map { index -> candidates[index].copy(chapterPosition = chapterPosition) }
      },
      status = result.status,
    )
  }

  private suspend fun generateReserved(program: PodcastProgram, initialEpisode: PodcastEpisode): PodcastGenerationResult.Generated {
    var episode = initialEpisode
    val regenerating = episode.regenerationStatus == PodcastRegenerationStatus.RUNNING
    var activePosition: Int? = null
    return try {
      val totalChapters = episode.chapterGroups().size
      (0 until totalChapters).forEach { position ->
        var group = episode.chapterGroups()[position]
        val current = group.first()
        if (current.chapterStatus == PodcastChapterGenerationStatus.READY && !current.chapterScript.isNullOrBlank()) return@forEach

        activePosition = position
        episode = repository.markChapterGenerating(episode.id, position)
        group = episode.chapterGroups()[position]
        val chapterScript = scriptGenerator.generate(
          program.provider,
          buildPodcastChapterPrompt(program.name, group, position + 1, totalChapters),
        ).trim()
        require(chapterScript.isNotBlank()) { "generated podcast chapter is blank" }
        episode = repository.completeChapter(episode.id, position, chapterScript)
        activePosition = null
      }

      episode = requireNotNull(repository.findEpisode(episode.id)) { "episode not found: ${episode.id}" }
      val script = buildPodcastEpisodeScriptFromChapters(program.name, episode.chapterGroups())
      val title = buildEpisodeTitle(program.name, episode.createdAtEpochMillis)
      PodcastGenerationResult.Generated(repository.completeEpisode(episode.id, title, script))
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      val message = error.message ?: error::class.simpleName ?: "generation failed"
      activePosition?.let { runCatching { repository.failChapter(episode.id, it, message) } }
      if (regenerating) runCatching { repository.failRegeneration(episode.id, message) }
      else runCatching { repository.failEpisode(episode.id, message) }
      throw error
    }
  }

  private suspend fun <T> withProgramGeneration(programId: String, block: suspend () -> T): T {
    check(synchronized(activeProgramIds) { activeProgramIds.add(programId) }) { "podcast generation already in progress: $programId" }
    return try { block() } finally { synchronized(activeProgramIds) { activeProgramIds.remove(programId) } }
  }
}

fun buildPodcastChapterPrompt(
  programName: String,
  article: PodcastEpisodeArticle,
  chapterNumber: Int,
  totalChapters: Int,
): String = buildPodcastChapterPrompt(programName, listOf(article), chapterNumber, totalChapters)

fun buildPodcastChapterPrompt(
  programName: String,
  articles: List<PodcastEpisodeArticle>,
  chapterNumber: Int,
  totalChapters: Int,
): String {
  require(articles.isNotEmpty()) { "articles must not be empty" }
  require(chapterNumber in 1..totalChapters) { "chapterNumber must be within totalChapters" }
  val targetChars = (PODCAST_TARGET_SCRIPT_CHARS / totalChapters).coerceIn(60, 500)
  val articleText = articles.mapIndexed { index, article ->
    buildString {
      appendLine("記事${index + 1}:")
      appendLine("タイトル: ${article.title}")
      article.sourceTitle?.takeIf(String::isNotBlank)?.let { appendLine("情報源: $it") }
      appendLine("本文:")
      append(article.feedContent.trim())
    }
  }.joinToString("\n---\n")

  return """
    あなたはニュース音声番組「$programName」の原稿編集者です。
    以下は同じ具体的なニュースを報じた記事です。入力記事だけを根拠に、重複内容を繰り返さず1つの日本語音声チャプターへ統合してください。

    出力形式:
    - 1行目は必ず [[TITLE:日本語の見出し]] とする。
    - 2行目以降に読み上げ本文だけを書く。
    - 2行目以降では見出しを繰り返さず、「タイトル」「見出し」などのラベルも付けない。
    - 見出しは元記事のタイトルをそのまま複製せず、入力内容だけを根拠に音声ニュースとして自然な日本語へ翻訳・言い換える。
    - 見出しは40文字程度までを目安に簡潔にし、入力にない事実を追加しない。

    必須条件:
    - 複数記事に共通する内容は一度だけ説明する。
    - 各記事にしかない情報は、互いに矛盾しない範囲で統合する。
    - 記事間で内容が食い違う場合は断定せず、一致している事実を優先する。
    - 外部ページ、リンク先、一般知識から情報を補わない。
    - 入力が外国語なら内容を日本語で自然に要約する。
    - 何が起きたか、なぜ重要かを簡潔に説明する。
    - 推測と入力に明記された事実を混同しない。
    - TITLEマーカー以外のMarkdown見出し、箇条書き、URL、チャプターマーカーは出力しない。
    - 番組全体の冒頭挨拶や締めの挨拶は出力しない。
    - 記号、略語、数字の羅列は、意味を変えない範囲で音声合成が自然に読める日本語表現へ言い換える。
    - ${targetChars}文字程度を目安に、重要な内容を優先して簡潔にまとめる。

    チャプター: $chapterNumber / $totalChapters
    入力記事:
    ---
    $articleText
  """.trimIndent()
}

fun buildPodcastEpisodeScript(programName: String, articles: List<PodcastEpisodeArticle>): String =
  buildPodcastEpisodeScriptFromChapters(programName, articles.map { listOf(it) })

private fun buildPodcastEpisodeScriptFromChapters(
  programName: String,
  chapters: List<List<PodcastEpisodeArticle>>,
): String {
  require(chapters.isNotEmpty()) { "chapters must not be empty" }
  require(chapters.all { chapter ->
    chapter.isNotEmpty() && chapter.all {
      it.chapterStatus == PodcastChapterGenerationStatus.READY && !it.chapterScript.isNullOrBlank()
    }
  }) { "all podcast chapters must be ready" }

  return chapters.mapIndexed { index, chapter ->
    val script = requireNotNull(chapter.first().chapterScript).trim()
    require(chapter.all { it.chapterScript?.trim() == script }) { "chapter articles must share the same generated script" }
    val speech = buildString {
      if (index == 0) append("${programName}です。今回のニュースをお伝えします。 ")
      append(script)
      if (index == chapters.lastIndex) append(" 以上、今回のニュースでした。")
    }
    "[[CHAPTER:${index + 1}]]\n$speech"
  }.joinToString("\n\n")
}

private fun stripRepeatedSpokenTitle(speech: String, spokenTitle: String?): String {
  val title = spokenTitle?.trim()?.takeIf(String::isNotBlank) ?: return speech
  val repeatedTitle = Regex("""^\s*(?:タイトル|見出し)\s*[、,:：]\s*${Regex.escape(title)}\s*(?:[。.!！?？]\s*)?""")
  return speech.replaceFirst(repeatedTitle, "").trimStart()
}

private fun buildEpisodeTitle(programName: String, createdAtEpochMillis: Long): String =
  "$programName / ${EPISODE_TITLE_FORMATTER.format(Instant.ofEpochMilli(createdAtEpochMillis).atZone(ZoneId.systemDefault()))}"

private val PODCAST_CHAPTER_MARKER = Regex("""(?m)^\[\[CHAPTER:(\d+)\]\]\s*$""")
private val PODCAST_SPOKEN_TITLE_MARKER = Regex("""\[\[TITLE:([^\]\r\n]+)\]\]""")
private const val PODCAST_TRANSITION_CUE = "続いて。"
private val EPISODE_TITLE_FORMATTER = DateTimeFormatter.ofPattern("M/d HH:mm")
private const val PODCAST_TARGET_SCRIPT_CHARS = 3_000
