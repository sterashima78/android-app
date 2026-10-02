package dev.terashima.yomitorirss.feature.podcast.data

import dev.terashima.yomitorirss.core.aiinference.BackgroundAiStructuredTextInference
import dev.terashima.yomitorirss.core.aiinference.AiStructuredTool
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolArgument
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolArgumentType
import dev.terashima.yomitorirss.core.aiinference.AiStructuredToolCall
import dev.terashima.yomitorirss.core.aiinference.BackgroundAiTextInference
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import dev.terashima.yomitorirss.feature.podcast.PodcastClusteringStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastNewsClusterer
import dev.terashima.yomitorirss.feature.podcast.PodcastNewsClusteringResult
import java.time.Instant
import java.util.concurrent.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class DefaultPodcastNewsClusterer(
  private val localTextInference: BackgroundAiTextInference,
  private val cloudTextInference: BackgroundAiTextInference,
  private val localStructuredInference: BackgroundAiStructuredTextInference,
  private val cloudStructuredInference: BackgroundAiStructuredTextInference,
) : PodcastNewsClusterer {
  override suspend fun cluster(
    provider: PodcastGenerationProvider,
    candidates: List<PodcastFeedEntry>,
  ): PodcastNewsClusteringResult {
    if (candidates.size <= 1) {
      return fallback(candidates, PodcastClusteringStatus.SKIPPED)
    }

    val route = when (provider) {
      PodcastGenerationProvider.LOCAL -> InferenceRoute(localTextInference, localStructuredInference, true)
      PodcastGenerationProvider.CLOUD -> InferenceRoute(cloudTextInference, cloudStructuredInference, false)
    }
    val model = try {
      checkNotNull(route.text.selectedModel()) { "利用するAIモデルを選択してください" }
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      return fallback(candidates, PodcastClusteringStatus.FALLBACK_INFERENCE_ERROR)
    }
    val prompt = try {
      buildPodcastClusteringToolPrompt(candidates, model.promptBudgetChars)
    } catch (_: Throwable) {
      return fallback(candidates, PodcastClusteringStatus.FALLBACK_INFERENCE_ERROR)
    }

    var request = prompt
    repeat(PODCAST_CLUSTERING_MAX_ATTEMPTS) { attempt ->
      val call = try {
        generateToolCall(route, request)
      } catch (error: CancellationException) {
        throw error
      } catch (_: Throwable) {
        return fallback(candidates, PodcastClusteringStatus.FALLBACK_INFERENCE_ERROR)
      }

      val groups = runCatching { parsePodcastClusterToolCall(call, candidates.size) }.getOrElse { error ->
        if (attempt == PODCAST_CLUSTERING_MAX_ATTEMPTS - 1) {
          return fallback(candidates, PodcastClusteringStatus.FALLBACK_INVALID_OUTPUT)
        }
        request = buildRepairPrompt(prompt, error.message.orEmpty(), model.promptBudgetChars)
        return@repeat
      }
      return PodcastNewsClusteringResult(groups = groups, status = PodcastClusteringStatus.SUCCESS)
    }
    return fallback(candidates, PodcastClusteringStatus.FALLBACK_INVALID_OUTPUT)
  }

  private suspend fun generateToolCall(
    route: InferenceRoute,
    request: String,
  ): AiStructuredToolCall? = if (route.local) {
    LocalAiBackgroundTaskGate.withPermit(priority = LocalAiBackgroundTaskPriority.NORMAL) {
      route.structured.generateToolCall(
        systemInstruction = PODCAST_CLUSTERING_SYSTEM_INSTRUCTION,
        userMessage = request,
        tool = PODCAST_CLUSTERING_OUTPUT_TOOL,
      )
    }
  } else {
    route.structured.generateToolCall(
      systemInstruction = PODCAST_CLUSTERING_SYSTEM_INSTRUCTION,
      userMessage = request,
      tool = PODCAST_CLUSTERING_OUTPUT_TOOL,
    )
  }
}

internal fun parsePodcastClusterToolCall(
  call: AiStructuredToolCall?,
  entryCount: Int,
): List<List<Int>> {
  require(entryCount > 0) { "entryCount must be positive" }
  require(call != null) { "classification tool was not called" }
  require(call.name == PODCAST_CLUSTERING_OUTPUT_TOOL.name) { "unexpected classification tool" }
  require(call.arguments.keys == setOf(PODCAST_CLUSTERING_GROUP_IDS_ARGUMENT)) {
    "classification tool arguments are invalid"
  }
  val raw = requireNotNull(call.arguments[PODCAST_CLUSTERING_GROUP_IDS_ARGUMENT])
  val array = PODCAST_CLUSTERING_JSON.parseToJsonElement(raw) as? JsonArray
    ?: throw IllegalArgumentException("group_ids must be an array")
  val groupIds = array.map { element ->
    (element as? JsonPrimitive)?.contentOrNull?.trim()
      ?.takeIf(String::isNotBlank)
      ?: throw IllegalArgumentException("group_ids must contain non-blank strings")
  }
  require(groupIds.size == entryCount) { "group_ids must contain one value per entry" }

  val groups = linkedMapOf<String, MutableList<Int>>()
  groupIds.forEachIndexed { index, groupId ->
    groups.getOrPut(groupId) { mutableListOf() } += index
  }
  return groups.values.map { it.toList() }
}

internal fun buildPodcastClusteringToolPrompt(
  entries: List<PodcastFeedEntry>,
  maxChars: Int,
): String {
  require(entries.isNotEmpty()) { "entries must not be empty" }
  require(maxChars > 0) { "maxChars must be positive" }

  val header = "候補記事を全件まとめて比較し、同じニュースごとに分類してください。番号が離れていても必ず比較してください。group_idsは候補記事と同じ要素数・同じ順序で返してください。\n"
  fun compactLine(index: Int, entry: PodcastFeedEntry, includeMetadata: Boolean): String = buildString {
    append(index + 1).append(". タイトル: ").append(entry.title.replace(Regex("\\s+"), " ").trim())
    if (includeMetadata) {
      entry.sourceTitle?.takeIf(String::isNotBlank)?.let {
        append(" | 情報源: ").append(it.replace(Regex("\\s+"), " ").trim())
      }
      entry.publishedAtEpochMillis?.let { append(" | 公開時刻: ").append(Instant.ofEpochMilli(it)) }
    }
  }

  val detailed = header + entries.mapIndexed { index, entry -> compactLine(index, entry, true) }.joinToString("\n")
  if (detailed.length <= maxChars) return detailed

  val plainTitles = entries.mapIndexed { index, entry -> compactLine(index, entry, false) }
  val plain = header + plainTitles.joinToString("\n")
  if (plain.length <= maxChars) return plain

  val prefixes = entries.indices.map { (it + 1).toString() + ". " }
  val fixedChars = header.length + prefixes.sumOf(String::length) + (entries.size - 1)
  val titleBudget = maxChars - fixedChars
  require(titleBudget >= entries.size * PODCAST_CLUSTERING_MIN_TITLE_CHARS) {
    "classification prompt budget is too small for all candidates"
  }
  val perTitle = titleBudget / entries.size
  return header + entries.mapIndexed { index, entry ->
    val normalized = entry.title.replace(Regex("\\s+"), " ").trim()
    prefixes[index] + normalized.take(perTitle)
  }.joinToString("\n")
}

private fun buildRepairPrompt(original: String, message: String, maxChars: Int): String {
  val feedback = "\n\n前回のtool callは検証に失敗しました。候補数と同じ長さのgroup_idsを1回だけ返してください。" +
    message.take(120).takeIf(String::isNotBlank)?.let { " 検証結果: " + it }.orEmpty()
  return if (original.length + feedback.length <= maxChars) original + feedback else original
}

private fun fallback(
  candidates: List<PodcastFeedEntry>,
  status: PodcastClusteringStatus,
): PodcastNewsClusteringResult = PodcastNewsClusteringResult(
  groups = candidates.indices.map { listOf(it) },
  status = status,
)

private data class InferenceRoute(
  val text: BackgroundAiTextInference,
  val structured: BackgroundAiStructuredTextInference,
  val local: Boolean,
)

private const val PODCAST_CLUSTERING_GROUP_IDS_ARGUMENT = "group_ids"
private const val PODCAST_CLUSTERING_MAX_ATTEMPTS = 2
private const val PODCAST_CLUSTERING_MIN_TITLE_CHARS = 12
private val PODCAST_CLUSTERING_JSON = Json { isLenient = false }

private const val PODCAST_CLUSTERING_SYSTEM_INSTRUCTION =
  "候補全体を比較し、同じ具体的な出来事を報じる記事だけを同じニュースとして分類してください。" +
    "タイトルの文字列一致ではなく出来事の意味で判断し、見出しの語順、翻訳、言い換え、表現の強弱が異なっても、主要な主体・行為や決定・対象が一致し、同じ出来事を指すと判断できる場合は同じgroup IDにしてください。" +
    "候補番号が離れていても必ず比較し、tool call前に単独グループになった候補を含む全候補を再確認して取りこぼしがないか確認してください。" +
    "同じ企業・人物・製品を扱うだけで行為や決定・対象が異なる場合、または同一出来事と判断できない場合は別ニュースにしてください。" +
    "情報源や公開時刻は補助情報であり、それだけを理由にニュースを分割しないでください。" +
    "通常テキストやMarkdownは返さず、指定toolを1回だけ呼び出してください。"

private val PODCAST_CLUSTERING_OUTPUT_TOOL = AiStructuredTool(
  name = "submit_podcast_news_clusters",
  description = "候補全体を比較し、同じ具体的な出来事を報じる記事へ同じgroup IDを割り当てる",
  arguments = listOf(
    AiStructuredToolArgument(
      name = PODCAST_CLUSTERING_GROUP_IDS_ARGUMENT,
      description = "候補記事と同じ順序・同じ要素数のgroup ID配列。語順・翻訳・言い換えが異なっても主要な主体・行為や決定・対象が一致して同一出来事なら同じ文字列を使う",
      required = true,
      type = AiStructuredToolArgumentType.STRING_ARRAY,
    ),
  ),
  allowAdditionalArguments = false,
)
