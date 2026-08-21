package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.airuntime.LocalInferenceTool
import dev.terashima.yomitorirss.core.airuntime.LocalInferenceToolArgument
import dev.terashima.yomitorirss.core.airuntime.LocalInferenceToolArgumentType
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import java.util.Locale

internal class LocalSmbMetadataNormalizationSuggester(
  private val modelManager: LocalModelManager,
) {
  fun suggest(
    currentFileName: String,
    coverBytes: ByteArray,
  ): SmbBookMetadataProposal {
    require(currentFileName.isNotBlank()) { "現在のファイル名がありません" }
    require(coverBytes.isNotEmpty()) { "表紙画像がありません" }
    require(coverBytes.size <= MAX_COVER_INPUT_BYTES) { "表紙画像が大きすぎます" }

    val initialPrompt = buildSmbMetadataNormalizationPrompt(currentFileName)
    return try {
      generateStructuredProposal(initialPrompt, coverBytes)
    } catch (firstError: IllegalArgumentException) {
      val repairPrompt = buildString {
        append(initialPrompt)
        append("\n\n前回の構造化出力は検証に失敗しました。")
        append("次の検証エラーだけを修正し、説明文を返さず submit_book_metadata を1回だけ呼び出してください。\n")
        append(firstError.message.orEmpty().take(MAX_VALIDATION_ERROR_CHARS))
      }
      try {
        generateStructuredProposal(repairPrompt, coverBytes)
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

internal fun buildSmbMetadataNormalizationPrompt(currentFileName: String): String = """
表紙画像と現在のファイル名の両方から、日本語を含む書籍の書誌情報を推定してください。
現在のファイル名は重要な書誌情報の根拠です。誤りやノイズ、表記揺れがあり得ても、捨てずに表紙画像と照合してください。
ファイル名にローマ字・英字で書籍名、著者名、シリーズ名、巻数が含まれている場合は、日本語の書誌情報を同定する手がかりとして積極的に利用してください。
表紙とファイル名が矛盾する場合は、片方を機械的に優先せず、両方の一致点と書誌としての自然さから判断してください。
判別できない任意項目は推測で埋めず、ツール引数を省略してください。著者を判別できない場合は authors を空配列にしてください。
シリーズ物では title に巻数表現を含めず、シリーズ名を seriesName、数値の巻数を seriesPosition に分離してください。
巻数を判別できた場合は seriesName と seriesPosition を必ず両方指定してください。例えば12巻目なら seriesPosition は 12 とします。
ISBNは表紙画像またはファイル名から明確に読み取れる場合だけ指定してください。
解析結果の説明文は返さず、必ず submit_book_metadata ツールを1回だけ呼び出してください。

現在のファイル名:
${currentFileName.trim().take(MAX_FILE_NAME_PROMPT_CHARS)}
""".trimIndent()

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
    val volumeLabel = explicitSeriesPositionLabel(originalFileName, position) ?: position.toString()
    if (!stem.endsWith(volumeLabel, ignoreCase = true)) stem = "$stem $volumeLabel"
  }
  stem = stem.replace(WHITESPACE, " ").trim()

  val suffix = ".$extension"
  val maxStemLength = (MAX_NORMALIZED_FILE_NAME_CHARS - suffix.length).coerceAtLeast(1)
  if (stem.length > maxStemLength) stem = stem.take(maxStemLength).trimEnd(' ', '.')
  return validateProposedSmbFileName(originalFileName, stem + suffix)
}

private fun explicitSeriesPositionLabel(originalFileName: String, position: Int): String? {
  val stem = originalFileName.substringBeforeLast('.', originalFileName)
  for (pattern in EXPLICIT_SERIES_POSITION_PATTERNS) {
    for (match in pattern.findAll(stem)) {
      val digits = match.groupValues.getOrNull(1) ?: continue
      if (digits.toSeriesPositionOrNull() == position) return match.value.trim()
    }
  }
  return null
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
  description = "表紙画像と現在のファイル名から推定した書誌情報を、解析結果として提出する。書誌解析が完了したら必ずこのツールを1回だけ呼び出す。",
  arguments = listOf(
    LocalInferenceToolArgument(
      name = "title",
      description = "書籍タイトル。巻数表現は含めない。1〜240文字。",
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
      "シリーズ名。巻数を判別できた場合は必ず指定する。シリーズ物でない、または判別できない場合は省略する。",
    ),
    LocalInferenceToolArgument(
      name = "seriesPosition",
      description = "シリーズ内の数値の巻数。1以上の整数。判別できた場合は seriesName とセットで指定する。",
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
private const val SMB_METADATA_SYSTEM_INSTRUCTION =
  "あなたは書籍の表紙画像とファイル名を照合して書誌情報を抽出するアシスタントです。ファイル名のローマ字・英字情報も重要な根拠として利用し、最終結果は説明文ではなく指定された出力ツールだけで提出してください。"
private val INVALID_FILE_NAME_CHARS = Regex("""[<>:"/\\|?*\x00-\x1F]""")
private val WHITESPACE = Regex("\\s+")
private val EXPLICIT_SERIES_POSITION_PATTERNS = listOf(
  Regex("""第\s*([0-9０-９]+)\s*巻"""),
  Regex("""(?i:vol(?:ume)?\.?)\s*([0-9０-９]+)"""),
  Regex("""([0-9０-９]+)\s*巻"""),
)
private const val MAX_FILE_NAME_PROMPT_CHARS = 500
private const val MAX_NORMALIZED_FILE_NAME_CHARS = 240
private const val MAX_COVER_INPUT_BYTES = 8 * 1024 * 1024
private const val MAX_VALIDATION_ERROR_CHARS = 500
