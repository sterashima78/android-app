package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.core.aiinference.AiStructuredTool
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolArgument
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolArgumentType
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolCall
import dev.terashima.yomitorirss.core.aiinference.BackgroundAiStructuredTextInference
import dev.terashima.yomitorirss.core.aiinference.BackgroundAiTextInference
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import dev.terashima.yomitorirss.feature.podcast.PODCAST_OTHER_CATEGORY
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastNewsCategorizer
import java.util.concurrent.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class DefaultPodcastNewsCategorizer(
  private val localTextInference: BackgroundAiTextInference,
  private val cloudTextInference: BackgroundAiTextInference,
  private val localStructuredInference: BackgroundAiStructuredTextInference,
  private val cloudStructuredInference: BackgroundAiStructuredTextInference,
) : PodcastNewsCategorizer {
  override suspend fun categorize(
    provider: PodcastGenerationProvider,
    news: List<List<PodcastFeedEntry>>,
    existingCategories: Set<String>,
  ): List<String> {
    if (news.isEmpty()) return emptyList()

    val route = when (provider) {
      PodcastGenerationProvider.LOCAL -> CategorizerInferenceRoute(localTextInference, localStructuredInference, true)
      PodcastGenerationProvider.CLOUD -> CategorizerInferenceRoute(cloudTextInference, cloudStructuredInference, false)
    }
    val model = try {
      checkNotNull(route.text.selectedModel()) { "利用するAIモデルを選択してください" }
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      return fallback(news)
    }
    val prompt = try {
      buildPodcastCategorizationToolPrompt(news, existingCategories, model.promptBudgetChars)
    } catch (_: Throwable) {
      return fallback(news)
    }

    var request = prompt
    repeat(PODCAST_CATEGORIZATION_MAX_ATTEMPTS) { attempt ->
      val call = try {
        generateToolCall(route, request)
      } catch (error: CancellationException) {
        throw error
      } catch (_: Throwable) {
        return fallback(news)
      }

      val categories = runCatching { parsePodcastCategoryToolCall(call, news.size) }.getOrElse { error ->
        if (attempt == PODCAST_CATEGORIZATION_MAX_ATTEMPTS - 1) return fallback(news)
        request = buildRepairPrompt(prompt, error.message.orEmpty(), model.promptBudgetChars)
        return@repeat
      }
      return categories
    }
    return fallback(news)
  }

  private suspend fun generateToolCall(
    route: CategorizerInferenceRoute,
    request: String,
  ): AiStructuredToolCall? = if (route.local) {
    LocalAiBackgroundTaskGate.withPermit(priority = LocalAiBackgroundTaskPriority.NORMAL) {
      route.structured.generateToolCall(
        systemInstruction = PODCAST_CATEGORIZATION_SYSTEM_INSTRUCTION,
        userMessage = request,
        tool = PODCAST_CATEGORIZATION_OUTPUT_TOOL,
      )
    }
  } else {
    route.structured.generateToolCall(
      systemInstruction = PODCAST_CATEGORIZATION_SYSTEM_INSTRUCTION,
      userMessage = request,
      tool = PODCAST_CATEGORIZATION_OUTPUT_TOOL,
    )
  }
}

internal fun parsePodcastCategoryToolCall(
  call: AiStructuredToolCall?,
  newsCount: Int,
): List<String> {
  require(newsCount > 0) { "newsCount must be positive" }
  require(call != null) { "categorization tool was not called" }
  require(call.name == PODCAST_CATEGORIZATION_OUTPUT_TOOL.name) { "unexpected categorization tool" }
  require(call.arguments.keys == setOf(PODCAST_CATEGORIZATION_CATEGORIES_ARGUMENT)) {
    "categorization tool arguments are invalid"
  }
  val raw = requireNotNull(call.arguments[PODCAST_CATEGORIZATION_CATEGORIES_ARGUMENT])
  val array = PODCAST_CATEGORIZATION_JSON.parseToJsonElement(raw) as? JsonArray
    ?: throw IllegalArgumentException("categories must be an array")
  val categories = array.map { element ->
    (element as? JsonPrimitive)?.contentOrNull?.trim()
      ?.takeIf(String::isNotBlank)
      ?: throw IllegalArgumentException("categories must contain non-blank strings")
  }
  require(categories.size == newsCount) { "categories must contain one value per news" }
  return categories
}

internal fun buildPodcastCategorizationToolPrompt(
  news: List<List<PodcastFeedEntry>>,
  existingCategories: Set<String>,
  maxChars: Int,
): String {
  require(news.isNotEmpty()) { "news must not be empty" }
  require(maxChars > 0) { "maxChars must be positive" }

  val existing = existingCategories
    .asSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()
    .sorted()
    .joinToString(" / ")
  val categoryHint = if (existing.isBlank()) {
    "既存カテゴリはありません。各ニュースに簡潔なカテゴリ名を付けてください。"
  } else {
    "既存カテゴリ: $existing\n内容に合う既存カテゴリがあれば同じ文字列を優先し、合わなければ簡潔な新しいカテゴリ名を付けてください。"
  }
  val header = "$categoryHint\nニュースごとに1カテゴリを、入力と同じ順序で返してください。\n"

  fun newsLine(index: Int, articles: List<PodcastFeedEntry>, includeSources: Boolean): String {
    val summaries = articles.joinToString(" / ") { article ->
      buildString {
        append(article.title.replace(Regex("\\s+"), " ").trim())
        if (includeSources) {
          article.sourceTitle?.takeIf(String::isNotBlank)?.let {
            append(" (").append(it.replace(Regex("\\s+"), " ").trim()).append(")")
          }
        }
      }
    }
    return "${index + 1}. $summaries"
  }

  val detailed = header + news.mapIndexed { index, articles -> newsLine(index, articles, true) }.joinToString("\n")
  if (detailed.length <= maxChars) return detailed

  val titlesOnly = header + news.mapIndexed { index, articles -> newsLine(index, articles, false) }.joinToString("\n")
  if (titlesOnly.length <= maxChars) return titlesOnly

  val prefixes = news.indices.map { "${it + 1}. " }
  val fixedChars = header.length + prefixes.sumOf(String::length) + (news.size - 1)
  val textBudget = maxChars - fixedChars
  require(textBudget >= news.size * PODCAST_CATEGORIZATION_MIN_NEWS_CHARS) {
    "categorization prompt budget is too small for all news"
  }
  val perNews = textBudget / news.size
  return header + news.mapIndexed { index, articles ->
    val titles = articles.joinToString(" / ") { it.title.replace(Regex("\\s+"), " ").trim() }
    prefixes[index] + titles.take(perNews)
  }.joinToString("\n")
}

private fun buildRepairPrompt(original: String, message: String, maxChars: Int): String {
  val feedback = "\n\n前回のtool callは検証に失敗しました。ニュース数と同じ長さのcategoriesを1回だけ返してください。" +
    message.take(120).takeIf(String::isNotBlank)?.let { " 検証結果: $it" }.orEmpty()
  return if (original.length + feedback.length <= maxChars) original + feedback else original
}

private fun fallback(news: List<List<PodcastFeedEntry>>): List<String> =
  List(news.size) { PODCAST_OTHER_CATEGORY }

private data class CategorizerInferenceRoute(
  val text: BackgroundAiTextInference,
  val structured: BackgroundAiStructuredTextInference,
  val local: Boolean,
)

private const val PODCAST_CATEGORIZATION_CATEGORIES_ARGUMENT = "categories"
private const val PODCAST_CATEGORIZATION_MAX_ATTEMPTS = 2
private const val PODCAST_CATEGORIZATION_MIN_NEWS_CHARS = 12
private val PODCAST_CATEGORIZATION_JSON = Json { isLenient = false }

private const val PODCAST_CATEGORIZATION_SYSTEM_INSTRUCTION =
  "ニュースを聞く順序をまとめるため、各ニュースに短く安定したカテゴリ名を1つ付けてください。" +
    "同じ話題領域のニュースには同じカテゴリ名を再利用し、必要以上に細かいカテゴリを増やさないでください。" +
    "入力されたタイトルと情報源だけを分類材料とし、外部情報を補わないでください。" +
    "通常テキストやMarkdownは返さず、指定toolを1回だけ呼び出してください。"

private val PODCAST_CATEGORIZATION_OUTPUT_TOOL = AiStructuredTool(
  name = "submit_podcast_news_categories",
  description = "ニュースごとの代表カテゴリを提出する",
  arguments = listOf(
    AiStructuredToolArgument(
      name = PODCAST_CATEGORIZATION_CATEGORIES_ARGUMENT,
      description = "ニュースと同じ順序・同じ要素数のカテゴリ名配列",
      required = true,
      type = AiStructuredToolArgumentType.STRING_ARRAY,
    ),
  ),
  allowAdditionalArguments = false,
)
