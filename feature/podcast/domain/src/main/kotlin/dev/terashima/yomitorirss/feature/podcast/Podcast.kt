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

data class PodcastEpisodeArticle(
  val articleId: String,
  val feedId: String,
  val title: String,
  val sourceTitle: String?,
  val publishedAtEpochMillis: Long?,
  val articleUrl: String?,
  val feedContent: String,
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
      PodcastPlaybackChapter(
        number = index + 1,
        article = articles[articleNumber - 1],
        speechText = speech.substring(start, endExclusive).trim(),
      )
    }
    return chapters.takeIf { it.none { chapter -> chapter.speechText.isBlank() } }
      ?: listOf(PodcastPlaybackChapter(number = 1, article = null, speechText = speech))
  }
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

  suspend fun archiveEpisode(episodeId: String): PodcastEpisode =
    error("archiveEpisode is not implemented")

  suspend fun restoreEpisode(episodeId: String): PodcastEpisode =
    error("restoreEpisode is not implemented")

  suspend fun deleteEpisode(episodeId: String) {
    error("deleteEpisode is not implemented")
  }

  /** Returns the oldest persisted GENERATING episode without promoting other queued work. */
  suspend fun findGeneratingEpisode(programId: String): PodcastEpisode? =
    listEpisodes(programId)
      .asSequence()
      .filter { it.status == PodcastEpisodeStatus.GENERATING }
      .minWithOrNull(compareBy<PodcastEpisode> { it.createdAtEpochMillis }.thenBy { it.id })

  /**
   * Returns the interrupted GENERATING episode first, otherwise promotes the oldest QUEUED episode
   * to GENERATING and returns it. Returns null when the program has no reserved work.
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
      generateReserved(program, episode)
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
      generateReserved(
        program = program,
        episode = episode,
        markFailedOnError = episode.status != PodcastEpisodeStatus.READY,
      )
    }
  }

  /**
   * Resumes only a persisted GENERATING episode for [programId]. Unlike [generate], this never
   * promotes QUEUED work or reserves new feed entries when the interrupted episode has already
   * completed elsewhere.
   */
  suspend fun resumeInterrupted(programId: String): PodcastGenerationResult.Generated? =
    withProgramGeneration(programId) {
      val program = requireNotNull(repository.findProgram(programId)) { "program not found: $programId" }
      val interrupted = repository.findGeneratingEpisode(programId)
        ?: return@withProgramGeneration null
      generateReserved(program, interrupted)
    }

  private suspend fun generateReserved(
    program: PodcastProgram,
    episode: PodcastEpisode,
    markFailedOnError: Boolean = true,
  ): PodcastGenerationResult.Generated {
    return try {
      val script = scriptGenerator.generate(
        provider = program.provider,
        prompt = buildPodcastPrompt(program.name, episode.articles),
      ).trim()
      require(script.isNotBlank()) { "generated podcast script is blank" }
      val title = buildEpisodeTitle(program.name, episode.createdAtEpochMillis)
      PodcastGenerationResult.Generated(repository.completeEpisode(episode.id, title, script))
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      if (markFailedOnError) {
        repository.failEpisode(
          episodeId = episode.id,
          message = error.message ?: error::class.simpleName ?: "generation failed",
        )
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

fun buildPodcastPrompt(
  programName: String,
  articles: List<PodcastEpisodeArticle>,
): String {
  require(articles.isNotEmpty()) { "articles must not be empty" }
  val material = articles.mapIndexed { index, article ->
    buildString {
      appendLine("---")
      appendLine("記事番号: ${index + 1}")
      appendLine("タイトル: ${article.title}")
      article.sourceTitle?.takeIf(String::isNotBlank)?.let { appendLine("情報源: $it") }
      appendLine("本文:")
      append(article.feedContent.trim())
    }
  }.joinToString(separator = "\n\n")
  return """
    あなたはニュース音声番組「$programName」の原稿編集者です。
    以下に渡すフィード内のタイトルと本文だけを根拠に、日本語の音声読み上げ用原稿を作成してください。

    必須条件:
    - 外部ページ、リンク先、一般知識から情報を補わない。
    - 入力が外国語なら内容を日本語で自然に要約する。
    - 入力記事は全${articles.size}件。重要度にかかわらず全記事を必ず原稿へ含め、省略しない。
    - 入力記事1件を1チャプターとして扱い、重要度の高い話題から並べる。
    - 各チャプターの先頭に、そのチャプターが扱う記事番号で `[[CHAPTER:n]]` を1行だけ出力する。
    - `[[CHAPTER:n]]` は入力記事数と同じ個数だけ出力し、各記事番号を1回ずつ使う。並び順は記事番号順でなくてよい。
    - 各チャプターは対応する1件の記事だけを根拠にし、何が起きたか、なぜ重要かを簡潔に説明する。
    - 話題同士のつながりが分かる構成にする。
    - 冒頭の挨拶と番組名は最初のチャプター内、最後の短い締めは最後のチャプター内に含める。
    - 推測と入力に明記された事実を混同しない。
    - チャプターマーカー以外のMarkdown見出し、箇条書き、URLは使わず、読み上げやすい連続した文章にする。
    - 記号、略語、数字の羅列は、意味を変えない範囲で音声合成が自然に読める日本語表現へ言い換える。
    - 全${articles.size}件を必ず含めたうえで原稿全体は3000文字程度以内に収める。記事数が多い場合は、一部の記事を落とすのではなく各チャプターを短くする。

    入力記事:
    $material
  """.trimIndent()
}

private fun buildEpisodeTitle(programName: String, createdAtEpochMillis: Long): String =
  "$programName / ${EPISODE_TITLE_FORMATTER.format(Instant.ofEpochMilli(createdAtEpochMillis).atZone(ZoneId.systemDefault()))}"

private val PODCAST_CHAPTER_MARKER = Regex("""(?m)^\[\[CHAPTER:(\d+)\]\]\s*$""")
private val EPISODE_TITLE_FORMATTER = DateTimeFormatter.ofPattern("M/d HH:mm")
