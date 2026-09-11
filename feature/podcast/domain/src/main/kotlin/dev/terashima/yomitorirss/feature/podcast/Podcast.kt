package dev.terashima.yomitorirss.feature.podcast

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.CancellationException

enum class PodcastGenerationProvider {
  LOCAL,
  CLOUD,
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
) {
  init {
    require(articleId.isNotBlank()) { "article id must not be blank" }
    require(feedId.isNotBlank()) { "source id must not be blank" }
    require(title.isNotBlank()) { "article title must not be blank" }
    require(articleUrl.isNotBlank()) { "article url must not be blank" }
    require(feedContent.isNotBlank()) { "feed content must not be blank" }
  }
}

enum class PodcastChapterGenerationStatus {
  PENDING,
  GENERATING,
  READY,
  FAILED,
}

enum class PodcastRegenerationStatus {
  RUNNING,
  FAILED,
}

data class PodcastEpisodeArticle(
  val articleId: String,
  val feedId: String,
  val title: String,
  val sourceTitle: String?,
  val publishedAtEpochMillis: Long?,
  val articleUrl: String?,
  val feedContent: String,
  val chapterStatus: PodcastChapterGenerationStatus = PodcastChapterGenerationStatus.PENDING,
  val chapterScript: String? = null,
  val chapterError: String? = null,
)

data class PodcastPlaybackChapter(
  val number: Int,
  val article: PodcastEpisodeArticle?,
  val speechText: String,
)

enum class PodcastEpisodeStatus {
  QUEUED,
  GENERATING,
  READY,
  FAILED,
  ARCHIVED,
  DELETED,
}

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
) {
  fun playbackChapters(): List<PodcastPlaybackChapter> {
    val speech = script?.trim().orEmpty()
    if (speech.isBlank()) return emptyList()

    val matches = PODCAST_CHAPTER_MARKER.findAll(speech).toList()
    val articleNumbers = matches.mapNotNull { match -> match.groupValues.getOrNull(1)?.toIntOrNull() }
    val expectedNumbers = articles.indices.map { it + 1 }
    if (matches.size != articles.size || articleNumbers.sorted() != expectedNumbers) {
      return listOf(PodcastPlaybackChapter(number = 1, article = null, speechText = speech))
    }

    val chapters = matches.mapIndexed { index, match ->
      val start = match.range.last + 1
      val endExclusive = matches.getOrNull(index + 1)?.range?.first ?: speech.length
      val articleNumber = articleNumbers[index]
      val segment = speech.substring(start, endExclusive).trim()
      val titleMatch = PODCAST_SPOKEN_TITLE_MARKER.find(segment)
      val spokenTitle = titleMatch
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf(String::isNotBlank)
      val playbackArticle = articles[articleNumber - 1].let { article ->
        spokenTitle?.let { title ->
          article.copy(title = if (index == 0) title else "$PODCAST_TRANSITION_CUE$title")
        } ?: article
      }
      val chapterSpeech = titleMatch?.let { segment.removeRange(it.range).trim() } ?: segment
      PodcastPlaybackChapter(
        number = index + 1,
        article = playbackArticle,
        speechText = chapterSpeech,
      )
    }
    return chapters.takeIf { it.none { chapter -> chapter.speechText.isBlank() } }
      ?: listOf(PodcastPlaybackChapter(number = 1, article = null, speechText = speech))
  }
}

enum class PodcastGenerationTaskState {
  QUEUED,
  RUNNING,
  FAILED,
}

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

  /** Returns the oldest persisted initial generation or regeneration interrupted by process loss. */
  suspend fun findInterruptedGenerationEpisode(programId: String): PodcastEpisode? =
    listEpisodes(programId)
      .asSequence()
      .filter {
        it.status == PodcastEpisodeStatus.GENERATING ||
          it.regenerationStatus == PodcastRegenerationStatus.RUNNING
      }
      .minWithOrNull(compareBy<PodcastEpisode> { it.createdAtEpochMillis }.thenBy { it.id })

  /**
   * Returns interrupted generation first, otherwise promotes the oldest QUEUED episode to
   * GENERATING and returns it. Returns null when the program has no reserved work.
   */
  suspend fun claimPendingEpisode(programId: String): PodcastEpisode?

  /**
   * Atomically excludes articles already consumed by this program and snapshots every currently
   * eligible candidate in max-sized episode chunks. The first chunk is GENERATING and later chunks
   * are QUEUED so feed rotation cannot discard already observed content. Returns the first episode,
   * or null when there is nothing to consume.
   */
  suspend fun reserveEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
  ): PodcastEpisode?

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
  suspend fun generate(
    provider: PodcastGenerationProvider,
    prompt: String,
  ): String
}

sealed interface PodcastGenerationResult {
  data class Generated(val episode: PodcastEpisode) : PodcastGenerationResult
  data object NoNewArticles : PodcastGenerationResult
}

class GeneratePodcastEpisodeUseCase(
  private val repository: PodcastRepository,
  private val feedContentSource: PodcastFeedContentSource,
  private val scriptGenerator: PodcastScriptGenerator,
  private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
  private val activeProgramIds = mutableSetOf<String>()

  suspend fun generate(programId: String): PodcastGenerationResult = withProgramGeneration(programId) {
    val program = requireNotNull(repository.findProgram(programId)) { "program not found: $programId" }
    repository.claimPendingEpisode(programId)?.let { pending ->
      return@withProgramGeneration generateReserved(program, pending)
    }
    val sources = repository.listSources().filter { it.id in program.sourceIds }
    require(sources.mapTo(mutableSetOf(), PodcastSource::id) == program.sourceIds) {
      "番組に利用できないソースがあります。番組設定を確認してください"
    }
    val candidates = feedContentSource.latestEntries(
      sources = sources,
      limit = Int.MAX_VALUE,
    )
    val reserved = repository.reserveEpisode(program, candidates, nowEpochMillis())
      ?: return@withProgramGeneration PodcastGenerationResult.NoNewArticles
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
      val prepared = if (episode.status == PodcastEpisodeStatus.READY) {
        repository.prepareEpisodeRegeneration(episode.id)
      } else {
        repository.prepareEpisodeRetry(episode.id)
      }
      generateReserved(program, prepared)
    }
  }

  /**
   * Resumes only a persisted initial generation or regeneration for [programId]. Unlike [generate],
   * this never promotes unrelated QUEUED work or reserves new feed entries.
   */
  suspend fun resumeInterrupted(programId: String): PodcastGenerationResult.Generated? =
    withProgramGeneration(programId) {
      val program = requireNotNull(repository.findProgram(programId)) { "program not found: $programId" }
      val interrupted = repository.findInterruptedGenerationEpisode(programId)
        ?: return@withProgramGeneration null
      generateReserved(program, interrupted)
    }

  private suspend fun generateReserved(
    program: PodcastProgram,
    initialEpisode: PodcastEpisode,
  ): PodcastGenerationResult.Generated {
    var episode = initialEpisode
    val regenerating = episode.regenerationStatus == PodcastRegenerationStatus.RUNNING
    var activePosition: Int? = null
    return try {
      episode.articles.indices.forEach { position ->
        val current = episode.articles[position]
        if (current.chapterStatus == PodcastChapterGenerationStatus.READY && !current.chapterScript.isNullOrBlank()) {
          return@forEach
        }

        activePosition = position
        episode = repository.markChapterGenerating(episode.id, position)
        val article = episode.articles[position]
        val chapterScript = scriptGenerator.generate(
          provider = program.provider,
          prompt = buildPodcastChapterPrompt(
            programName = program.name,
            article = article,
            chapterNumber = position + 1,
            totalChapters = episode.articles.size,
          ),
        ).trim()
        require(chapterScript.isNotBlank()) { "generated podcast chapter is blank" }
        require(PODCAST_SPOKEN_TITLE_MARKER.containsMatchIn(chapterScript)) {
          "generated podcast chapter is missing Japanese title"
        }
        episode = repository.completeChapter(episode.id, position, chapterScript)
        activePosition = null
      }

      episode = requireNotNull(repository.findEpisode(episode.id)) { "episode not found: ${episode.id}" }
      val script = buildPodcastEpisodeScript(program.name, episode.articles)
      val title = buildEpisodeTitle(program.name, episode.createdAtEpochMillis)
      PodcastGenerationResult.Generated(repository.completeEpisode(episode.id, title, script))
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      val message = error.message ?: error::class.simpleName ?: "generation failed"
      activePosition?.let { position ->
        runCatching { repository.failChapter(episode.id, position, message) }
      }
      if (regenerating) {
        runCatching { repository.failRegeneration(episode.id, message) }
      } else {
        runCatching { repository.failEpisode(episode.id, message) }
      }
      throw error
    }
  }

  private suspend fun <T> withProgramGeneration(programId: String, block: suspend () -> T): T {
    check(synchronized(activeProgramIds) { activeProgramIds.add(programId) }) {
      "podcast generation already in progress: $programId"
    }
    return try {
      block()
    } finally {
      synchronized(activeProgramIds) { activeProgramIds.remove(programId) }
    }
  }
}

fun buildPodcastChapterPrompt(
  programName: String,
  article: PodcastEpisodeArticle,
  chapterNumber: Int,
  totalChapters: Int,
): String {
  require(chapterNumber in 1..totalChapters) { "chapterNumber must be within totalChapters" }
  val targetChars = (PODCAST_TARGET_SCRIPT_CHARS / totalChapters).coerceIn(60, 500)
  return """
    あなたはニュース音声番組「$programName」の原稿編集者です。
    以下の1件の記事だけを根拠に、日本語の音声読み上げ用チャプターを作成してください。

    出力形式:
    - 1行目は必ず [[TITLE:日本語の見出し]] とする。
    - 2行目以降に読み上げ本文だけを書く。
    - 見出しは元記事のタイトルをそのまま複製せず、入力内容だけを根拠に音声ニュースとして自然な日本語へ翻訳・言い換える。
    - 見出しは40文字程度までを目安に簡潔にし、入力にない事実を追加しない。

    必須条件:
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
    タイトル: ${article.title}
    ${article.sourceTitle?.takeIf(String::isNotBlank)?.let { "情報源: $it" }.orEmpty()}
    本文:
    ${article.feedContent.trim()}
  """.trimIndent()
}

fun buildPodcastEpisodeScript(
  programName: String,
  articles: List<PodcastEpisodeArticle>,
): String {
  require(articles.isNotEmpty()) { "articles must not be empty" }
  require(articles.all {
    it.chapterStatus == PodcastChapterGenerationStatus.READY && !it.chapterScript.isNullOrBlank()
  }) { "all podcast chapters must be ready" }

  return articles.mapIndexed { index, article ->
    val speech = buildString {
      if (index == 0) append("${programName}です。今回のニュースをお伝えします。 ")
      append(requireNotNull(article.chapterScript).trim())
      if (index == articles.lastIndex) append(" 以上、今回のニュースでした。")
    }
    "[[CHAPTER:${index + 1}]]\n$speech"
  }.joinToString(separator = "\n\n")
}

private fun buildEpisodeTitle(programName: String, createdAtEpochMillis: Long): String =
  "$programName / ${EPISODE_TITLE_FORMATTER.format(Instant.ofEpochMilli(createdAtEpochMillis).atZone(ZoneId.systemDefault()))}"

private val PODCAST_CHAPTER_MARKER = Regex("""(?m)^\[\[CHAPTER:(\d+)\]\]\s*$""")
private val PODCAST_SPOKEN_TITLE_MARKER = Regex("""\[\[TITLE:([^\]\r\n]+)\]\]""")
private const val PODCAST_TRANSITION_CUE = "続いて。"
private val EPISODE_TITLE_FORMATTER = DateTimeFormatter.ofPattern("M/d HH:mm")
private const val PODCAST_TARGET_SCRIPT_CHARS = 3_000