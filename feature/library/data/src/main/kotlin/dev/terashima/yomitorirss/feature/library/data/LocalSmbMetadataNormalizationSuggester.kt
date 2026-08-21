package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.airuntime.LocalInferenceTool
import dev.terashima.yomitorirss.core.airuntime.LocalInferenceToolArgument
import dev.terashima.yomitorirss.core.airuntime.LocalInferenceToolArgumentType
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.feature.library.DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT
import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import dev.terashima.yomitorirss.feature.library.renderSmbMetadataNormalizationPrompt
import java.util.Locale

internal class LocalSmbMetadataNormalizationSuggester(
  private val modelManager: LocalModelManager,
) {
  fun suggest(
    currentFileName: String,
    coverBytes: ByteArray,
    promptTemplate: String = DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT,
  ): SmbBookMetadataProposal {
    require(currentFileName.isNotBlank()) { "現在のファイル名がありません" }
    require(coverBytes.isNotEmpty()) { "表紙画像がありません" }
    require(coverBytes.size <= MAX_COVER_INPUT_BYTES) { "表紙画像が大きすぎます" }

    val initialPrompt = buildSmbMetadataNormalizationPrompt(currentFileName, promptTemplate)
    return try {
      completeSmbSeriesMetadataFromFileName(
        currentFileName,
        generateStructuredProposal(initialPrompt, coverBytes),
      )
    } catch (firstError: IllegalArgumentException) {
      val repairPrompt = buildString {
        append(initialPrompt)
        append("\n\n前回の構造化出力は検証に失敗しました。")
        append("次の検証エラーだけを修正し、説明文を返さず submit_book_metadata を1回だけ呼び出してください。\n")
        append(firstError.message.orEmpty().take(MAX_VALIDATION_ERROR_CHARS))
      }
      try {
        completeSmbSeriesMetadataFromFileName(
          currentFileName,
          generateStructuredProposal(repairPrompt, coverBytes),
        )
      } catch (secondError: IllegalArgumentException) {
        throw IllegalArgumentException(
          "AIが構造化された書誌情報を返せませんでした。再解析してください",
          secondError,
        )
      }
    }
  }

  private fun generateStructuredProposal(
    prompt: String,
    coverBytes: ByteArray,
  ): SmbBookMetadataProposal {
    val call = try {
      modelManager.generateImageToolCall(
        systemInstruction = SMB_METADATA_SYSTEM_INSTRUCTION,
        userMessage = prompt,
        imageBytes = coverBytes,
        tools = listOf(SMB_METADATA_OUTPUT_TOOL),
      )
    } catch (error: Throwable) {
      if (!error.isSmbMetadataToolCallParseFailure()) throw error
      throw IllegalArgumentException("構造化ツール呼び出しを解析できませんでした", error)
    } ?: throw IllegalArgumentException("submit_book_metadata が1回だけ呼び出されませんでした")
    require(call.name == SMB_METADATA_OUTPUT_TOOL_NAME) { "想定外のツールが呼び出されました" }
    return parseSmbBookMetadataProposal(call.arguments)
  }
}

internal fun Throwable.isSmbMetadataToolCallParseFailure(): Boolean =
  generateSequence(this) { error -> error.cause }
    .mapNotNull(Throwable::message)
    .any { message ->
      message.contains("Failed to parse tool calls", ignoreCase = true) ||
        message.contains("Failed to parse FC tool calls", ignoreCase = true)
    }

internal fun buildSmbMetadataNormalizationPrompt(
  currentFileName: String,
  promptTemplate: String = DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT,
): String = buildString {
  append(renderSmbMetadataNormalizationPrompt(promptTemplate, currentFileName))
  val stem = currentFileName.substringBeforeLast('.', currentFileName).trim()
  trailingBareSeriesPositionHint(stem)?.let { hint ->
    append("\n\n")
    append("現在のファイル名末尾の ${hint.position} は巻数候補です。")
    append("表紙と照合し、巻数なら seriesName と seriesPosition を指定し、巻数でなければ巻数として扱わないでください。")
  }
  append("\n\n")
  append(SMB_METADATA_STRUCTURED_OUTPUT_INSTRUCTION)
}

internal fun parseSmbBookMetadataProposal(arguments: Map<String, Any?>): SmbBookMetadataProposal {
  val allowed = setOf(
    "title",
    "authors",
    "publisher",
    "publishedDate",
    "isbn10",
    "isbn13",
    "seriesName",
    "seriesPosition",
    "confidence",
    "reason",
  )
  val extra = arguments.keys - allowed
  require(extra.isEmpty()) { "追加フィールド=${extra.joinToString()}" }
  require(arguments.containsKey("title")) { "title がありません" }
  require(arguments.containsKey("authors")) { "authors がありません" }

  val title = arguments.requireString("title", maxLength = 240)
  val authorsValue = arguments["authors"]
  require(authorsValue is List<*>) { "authors は文字列配列である必要があります" }
  require(authorsValue.size <= 12) { "authors は最大12件です" }
  val authors = authorsValue.map { value ->
    require(value is String) { "authors の各要素は文字列である必要があります" }
    value.trim().also {
      require(it.isNotEmpty() && it.length <= 120) { "authors の要素が不正です" }
    }
  }.distinctBy { it.lowercase(Locale.ROOT) }

  val publisher = arguments.optionalString("publisher", 160)
  val publishedDate = arguments.optionalString("publishedDate", 40)
  val isbn10 = arguments.optionalString("isbn10", 20)
  val isbn13 = arguments.optionalString("isbn13", 20)
  val seriesName = arguments.optionalString("seriesName", 240)
  val seriesPosition = arguments.optionalInt("seriesPosition")?.also {
    require(it > 0) { "seriesPosition は1以上である必要があります" }
  }
  if (seriesPosition != null) {
    require(seriesName != null) { "seriesPosition がある場合は seriesName も必要です" }
  }
  val confidence = arguments.optionalDouble("confidence")?.also {
    require(it in 0.0..1.0) { "confidence は0〜1である必要があります" }
  }?.toFloat()
  val reason = arguments.optionalString("reason", 500)

  return SmbBookMetadataProposal(
    title = title,
    authors = authors,
    publisher = publisher,
    publishedDate = publishedDate,
    isbn10 = isbn10,
    isbn13 = isbn13,
    seriesName = seriesName,
    seriesPosition = seriesPosition,
    confidence = confidence,
    reason = reason,
  )
}

internal fun completeSmbSeriesMetadataFromFileName(
  currentFileName: String,
  proposal: SmbBookMetadataProposal,
): SmbBookMetadataProposal {
  if (proposal.seriesPosition != null) return proposal

  val stem = currentFileName.substringBeforeLast('.', currentFileName).trim()
  val hint = trailingBareSeriesPositionHint(stem) ?: return proposal
  if (!sameBibliographicText(hint.titlePart, proposal.title)) return proposal

  return proposal.copy(
    seriesName = proposal.seriesName ?: proposal.title,
    seriesPosition = hint.position,
  )
}

internal fun normalizedSmbBookFileName(
  originalFileName: String,
  proposal: SmbBookMetadataProposal,
): String {
  val extension = originalFileName.substringAfterLast('.', "").trim().lowercase(Locale.ROOT)
  require(extension.isNotEmpty()) { "元ファイルの拡張子を判定できません" }
  var stem = proposal.title
    .trim()
    .replace(INVALID_FILE_NAME_CHARS, " ")
    .replace(WHITESPACE, " ")
    .trim(' ', '.')
  require(stem.isNotEmpty()) { "タイトルからファイル名を生成できません" }

  proposal.seriesPosition?.takeIf { it > 0 }?.let { position ->
    val volumeLabel = "第${position}巻"
    if (!stem.endsWith(volumeLabel)) stem = "$stem $volumeLabel"
  }
  stem = stem.replace(WHITESPACE, " ").trim()

  val suffix = ".$extension"
  val maxStemLength = (MAX_NORMALIZED_FILE_NAME_CHARS - suffix.length).coerceAtLeast(1)
  if (stem.length > maxStemLength) stem = stem.take(maxStemLength).trimEnd(' ', '.')
  return validateProposedSmbFileName(originalFileName, stem + suffix)
}

private fun trailingBareSeriesPositionHint(stem: String): SeriesPositionHint? {
  val match = TRAILING_BARE_SERIES_POSITION.matchEntire(stem) ?: return null
  val position = match.groupValues[2].toSeriesPositionOrNull()
    ?.takeIf { it in 1..MAX_INFERRED_TRAILING_SERIES_POSITION }
    ?: return null
  val titlePart = match.groupValues[1].trimEnd(' ', '_', '-', '.', '・')
  if (titlePart.isBlank()) return null
  return SeriesPositionHint(titlePart = titlePart, position = position)
}

private fun sameBibliographicText(first: String, second: String): Boolean {
  val firstKey = bibliographicTextKey(first)
  val secondKey = bibliographicTextKey(second)
  return firstKey.isNotEmpty() && firstKey == secondKey
}

private fun bibliographicTextKey(value: String): String = buildString(value.length) {
  value.lowercase(Locale.ROOT).forEach { character ->
    if (character.isLetterOrDigit()) append(character)
  }
}

private fun String.toSeriesPositionOrNull(): Int? = buildString(length) {
  for (character in this@toSeriesPositionOrNull) {
    append(
      if (character in '０'..'９') {
        ('0'.code + character.code - '０'.code).toChar()
      } else {
        character
      },
    )
  }
}.toIntOrNull()

private fun Map<String, Any?>.requireString(name: String, maxLength: Int): String {
  val value = get(name)
  require(value is String) { "$name は文字列である必要があります" }
  return value.trim().also {
    require(it.isNotEmpty()) { "$name は空にできません" }
    require(it.length <= maxLength) { "$name が長すぎます" }
  }
}

private fun Map<String, Any?>.optionalString(name: String, maxLength: Int): String? {
  if (!containsKey(name)) return null
  val value = get(name) ?: return null
  require(value is String) { "$name は文字列である必要があります" }
  return value.trim().takeIf(String::isNotEmpty)?.also {
    require(it.length <= maxLength) { "$name が長すぎます" }
  }
}

private fun Map<String, Any?>.optionalInt(name: String): Int? {
  if (!containsKey(name)) return null
  val value = get(name) ?: return null
  val number = when (value) {
    is Number -> value.toDouble()
    is String -> value.toDoubleOrNull()
    else -> null
  }
  require(number != null && number.isFinite()) { "$name は整数である必要があります" }
  val int = number.toInt()
  require(number == int.toDouble()) { "$name は整数である必要があります" }
  return int
}

private fun Map<String, Any?>.optionalDouble(name: String): Double? {
  if (!containsKey(name)) return null
  val value = get(name) ?: return null
  val number = when (value) {
    is Number -> value.toDouble()
    is String -> value.toDoubleOrNull()
    else -> null
  }
  require(number != null && number.isFinite()) { "$name は数値である必要があります" }
  return number
}

private val SMB_METADATA_OUTPUT_TOOL = LocalInferenceTool(
  name = SMB_METADATA_OUTPUT_TOOL_NAME,
  description = "表紙画像と現在のファイル名から推定した書誌情報を、解析結果として提出する。漫画等の巻数情報を title から除いた場合も捨てず、seriesName と seriesPosition に保持する。書誌解析が完了したら必ずこのツールを1回だけ呼び出す。",
  arguments = listOf(
    LocalInferenceToolArgument(
      name = "title",
      description = "書籍タイトル。巻数表現は含めない。巻数を認識した場合は削除するだけでなく seriesPosition へ移す。1〜240文字。",
      required = true,
    ),
    LocalInferenceToolArgument(
      name = "authors",
      description = "著者名の配列。判別できない場合は空配列。最大12件。ファイル名がローマ字・英字表記の場合も書誌同定の手がかりにする。",
      required = true,
      type = LocalInferenceToolArgumentType.STRING_ARRAY,
    ),
    LocalInferenceToolArgument("publisher", "出版社。判別できない場合は省略する。"),
    LocalInferenceToolArgument("publishedDate", "出版日。判別できない場合は省略する。"),
    LocalInferenceToolArgument("isbn10", "ISBN-10。明確に読み取れる場合だけ指定する。"),
    LocalInferenceToolArgument("isbn13", "ISBN-13。明確に読み取れる場合だけ指定する。"),
    LocalInferenceToolArgument(
      "seriesName",
      "シリーズ名。漫画・ライトノベル・小説等で巻数を判別できた場合は必須。独立したシリーズ名の表記がなければ、巻数を除いた作品タイトルを指定する。",
    ),
    LocalInferenceToolArgument(
      name = "seriesPosition",
      description = "シリーズ内の数値の巻数。1以上の整数。第8巻、Vol.8、末尾の08などを巻数と判別した場合は省略せず 8 を指定し、seriesName とセットで提出する。",
      type = LocalInferenceToolArgumentType.INTEGER,
    ),
    LocalInferenceToolArgument(
      name = "confidence",
      description = "推定全体の確信度。0〜1の数値。判定できない場合は省略する。",
      type = LocalInferenceToolArgumentType.NUMBER,
    ),
    LocalInferenceToolArgument("reason", "表紙とファイル名のどの情報を根拠にしたかを500文字以内で簡潔に説明する。"),
  ),
  allowAdditionalArguments = false,
  execute = { "accepted" },
)

private const val SMB_METADATA_OUTPUT_TOOL_NAME = "submit_book_metadata"
private const val SMB_METADATA_STRUCTURED_OUTPUT_INSTRUCTION =
  "漫画等のシリーズ作品で巻数を認識した場合、title から巻数を除くだけで終わらせず、seriesName と seriesPosition を必ず保持してください。解析結果の説明文は返さず、必ず submit_book_metadata ツールを1回だけ呼び出してください。"
private const val SMB_METADATA_SYSTEM_INSTRUCTION =
  "あなたは書籍の表紙画像とファイル名を照合して書誌情報を抽出するアシスタントです。シリーズ作品では巻数を見落とさず、巻数を title から除いた場合も seriesName と seriesPosition に保持してください。ファイル名のローマ字・英字情報も重要な根拠として利用し、最終結果は説明文ではなく指定された出力ツールだけで提出してください。"
private val INVALID_FILE_NAME_CHARS = Regex("""[<>:"/\\|?*\x00-\x1F]""")
private val WHITESPACE = Regex("\\s+")
private val TRAILING_BARE_SERIES_POSITION = Regex("""^(.*[^0-9０-９])([0-9０-９]{1,3})$""")
private const val MAX_INFERRED_TRAILING_SERIES_POSITION = 300
private const val MAX_NORMALIZED_FILE_NAME_CHARS = 240
private const val MAX_COVER_INPUT_BYTES = 8 * 1024 * 1024
private const val MAX_VALIDATION_ERROR_CHARS = 500

private data class SeriesPositionHint(
  val titlePart: String,
  val position: Int,
)