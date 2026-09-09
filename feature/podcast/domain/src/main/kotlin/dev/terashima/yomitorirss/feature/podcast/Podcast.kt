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

data class PodcastProgram(
  val id: String,
  val name: String,
  val feedIds: Set<String>,
  val provider: PodcastGenerationProvider,
  val schedule: PodcastSchedule = PodcastSchedule(),
  val maxArticlesPerEpisode: Int = 12,
) {
  init {
    require(id.isNotBlank()) { "program id must not be blank" }
    require(name.isNotBlank()) { "program name must not be blank" }
    require(feedIds.isNotEmpty()) { "program must contain at least one feed" }
    require(maxArticlesPerEpisode in 1..50) { "maxArticlesPerEpisode must be between 1 and 50" }
  }
}

data class PodcastFeedEntry(
  val articleId: String,
  val feedId: String,
  val title: String,
  val sourceTitle: String?,
  val publishedAtEpochMillis: Long?,
  val feedContent: String,
) {
  init {
    require(articleId.isNotBlank()) { "article id must not be blank" }
    require(feedId.isNotBlank()) { "feed id must not be blank" }
    require(title.isNotBlank()) { "article title must not be blank" }
    require(feedContent.isNotBlank()) { "feed content must not be blank" }
  }
}

data class PodcastEpisodeArticle(
  val articleId: String,
  val feedId: String,
  val title: String,
  val sourceTitle: String?,
  val publishedAtEpochMillis: Long?,
  val feedContent: String,
)

enum class PodcastEpisodeStatus {
  GENERATING,
  READY,
  FAILED,
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
)

interface PodcastFeedContentSource {
  suspend fun latestEntries(feedIds: Set<String>, limit: Int): List<PodcastFeedEntry>
}

interface PodcastRepository {
  suspend fun listPrograms(): List<PodcastProgram>
  suspend fun findProgram(programId: String): PodcastProgram?
  suspend fun saveProgram(program: PodcastProgram)
  suspend fun deleteProgram(programId: String)
  suspend fun listEpisodes(programId: String): List<PodcastEpisode>
  suspend fun findEpisode(episodeId: String): PodcastEpisode?
  suspend fun findGeneratingEpisode(programId: String): PodcastEpisode?

  /**
   * Atomically excludes articles already consumed by this program, creates an episode snapshot,
   * and marks the selected article ids as consumed. Returns null when there is nothing to consume.
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
  suspend fun generate(programId: String): PodcastGenerationResult {
    val program = requireNotNull(repository.findProgram(programId)) { "program not found: $programId" }
    repository.findGeneratingEpisode(programId)?.let { interrupted ->
      return generateReserved(program, interrupted)
    }
    val candidates = feedContentSource.latestEntries(
      feedIds = program.feedIds,
      limit = Int.MAX_VALUE,
    )
    val reserved = repository.reserveEpisode(program, candidates, nowEpochMillis())
      ?: return PodcastGenerationResult.NoNewArticles
    return generateReserved(program, reserved)
  }

  suspend fun retry(episodeId: String): PodcastGenerationResult.Generated {
    val episode = requireNotNull(repository.findEpisode(episodeId)) { "episode not found: $episodeId" }
    require(episode.status == PodcastEpisodeStatus.FAILED) { "only failed episodes can be retried" }
    val program = requireNotNull(repository.findProgram(episode.programId)) { "program not found: ${episode.programId}" }
    return generateReserved(program, episode)
  }

  private suspend fun generateReserved(
    program: PodcastProgram,
    episode: PodcastEpisode,
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
      repository.failEpisode(
        episodeId = episode.id,
        message = error.message ?: error::class.simpleName ?: "generation failed",
      )
      throw error
    }
  }
}

fun buildPodcastPrompt(
  programName: String,
  articles: List<PodcastEpisodeArticle>,
): String {
  require(articles.isNotEmpty()) { "articles must not be empty" }
  val material = articles.joinToString(separator = "\n\n") { article ->
    buildString {
      appendLine("---")
      appendLine("タイトル: ${article.title}")
      article.sourceTitle?.takeIf(String::isNotBlank)?.let { appendLine("情報源: $it") }
      appendLine("本文:")
      append(article.feedContent.trim())
    }
  }
  return """
    あなたはニュース音声番組「$programName」の原稿編集者です。
    以下に渡すフィード内のタイトルと本文だけを根拠に、日本語の音声読み上げ用原稿を作成してください。

    必須条件:
    - 外部ページ、リンク先、一般知識から情報を補わない。
    - 入力が外国語なら内容を日本語で自然に要約する。
    - 重要度の高い話題から並べ、話題同士のつながりが分かる構成にする。
    - 各話題で、何が起きたか、なぜ重要かを簡潔に説明する。
    - 推測と入力に明記された事実を混同しない。
    - Markdown の見出し、箇条書き、URL は使わず、読み上げやすい連続した文章にする。
    - 記号、略語、数字の羅列は、意味を変えない範囲で音声合成が自然に読める日本語表現へ言い換える。
    - 冒頭の挨拶と番組名、最後の短い締めを含める。
    - 音声合成で途中欠落しないよう、原稿全体は3000文字程度以内に収める。

    入力記事:
    $material
  """.trimIndent()
}

private fun buildEpisodeTitle(programName: String, createdAtEpochMillis: Long): String =
  "$programName / ${EPISODE_TITLE_FORMATTER.format(Instant.ofEpochMilli(createdAtEpochMillis).atZone(ZoneId.systemDefault()))}"

private val EPISODE_TITLE_FORMATTER = DateTimeFormatter.ofPattern("M/d HH:mm")
