package dev.terashima.yomitorirss.feature.library.data

import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.feature.library.SmbBookMetadataProposal
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

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
    val first = modelManager.generateWithImage(initialPrompt, coverBytes)
    return try {
      parseSmbBookMetadataProposal(first)
    } catch (firstError: IllegalArgumentException) {
      val repairPrompt = buildString {
        append(initialPrompt)
        append("\n\n前回の出力はJSON Schema検証に失敗しました。")
        append("次の検証エラーだけを修正し、JSONオブジェクトだけを再生成してください。\n")
        append(firstError.message.orEmpty().take(MAX_VALIDATION_ERROR_CHARS))
      }
      parseSmbBookMetadataProposal(modelManager.generateWithImage(repairPrompt, coverBytes))
    }
  }
}

internal fun buildSmbMetadataNormalizationPrompt(currentFileName: String): String = """
あなたは日本語を含む書籍の書誌情報を表紙画像と現在のファイル名から推定します。
現在のファイル名は誤りや表記揺れを含む可能性があるため、表紙画像を強い根拠として利用してください。
判別できない値は推測で埋めず null または空配列にしてください。
シリーズ物では title に巻数表現を含めず、seriesName と seriesPosition に分離してください。
ISBNは表紙画像またはファイル名から明確に読み取れる場合だけ返してください。

現在のファイル名:
${currentFileName.trim().take(MAX_FILE_NAME_PROMPT_CHARS)}

次のJSON Schemaに厳密に従うJSONオブジェクトだけを返してください。Markdownや説明文を付けないでください。
{
  "type":"object",
  "additionalProperties":false,
  "required":["title","authors","publisher","publishedDate","isbn10","isbn13","seriesName","seriesPosition","confidence","reason"],
  "properties":{
    "title":{"type":"string","minLength":1,"maxLength":240},
    "authors":{"type":"array","maxItems":12,"items":{"type":"string","minLength":1,"maxLength":120}},
    "publisher":{"type":["string","null"],"maxLength":160},
    "publishedDate":{"type":["string","null"],"maxLength":40},
    "isbn10":{"type":["string","null"],"maxLength":20},
    "isbn13":{"type":["string","null"],"maxLength":20},
    "seriesName":{"type":["string","null"],"maxLength":240},
    "seriesPosition":{"type":["integer","null"],"minimum":1},
    "confidence":{"type":["number","null"],"minimum":0,"maximum":1},
    "reason":{"type":["string","null"],"maxLength":500}
  }
}
""".trimIndent()

internal fun parseSmbBookMetadataProposal(raw: String): SmbBookMetadataProposal {
  val text = raw.trim()
  require(text.startsWith('{') && text.endsWith('}')) { "JSONオブジェクトだけを返してください" }
  val json = try {
    JSONObject(text)
  } catch (error: Throwable) {
    throw IllegalArgumentException("JSONとして解析できません", error)
  }
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
  val keys = json.keys().asSequence().toSet()
  require(keys == allowed) {
    val missing = allowed - keys
    val extra = keys - allowed
    "必須フィールド不足=${missing.joinToString()} 追加フィールド=${extra.joinToString()}"
  }

  val title = json.requireString("title", maxLength = 240)
  val authorsValue = json.get("authors")
  require(authorsValue is JSONArray) { "authors は文字列配列である必要があります" }
  require(authorsValue.length() <= 12) { "authors は最大12件です" }
  val authors = buildList {
    for (index in 0 until authorsValue.length()) {
      val value = authorsValue.get(index)
      require(value is String) { "authors の各要素は文字列である必要があります" }
      val author = value.trim()
      require(author.isNotEmpty() && author.length <= 120) { "authors の要素が不正です" }
      add(author)
    }
  }.distinctBy { it.lowercase(Locale.ROOT) }

  val publisher = json.requireNullableString("publisher", 160)
  val publishedDate = json.requireNullableString("publishedDate", 40)
  val isbn10 = json.requireNullableString("isbn10", 20)
  val isbn13 = json.requireNullableString("isbn13", 20)
  val seriesName = json.requireNullableString("seriesName", 240)
  val seriesPosition = json.requireNullableInt("seriesPosition")?.also {
    require(it > 0) { "seriesPosition は1以上である必要があります" }
  }
  val confidence = json.requireNullableDouble("confidence")?.also {
    require(it in 0.0..1.0) { "confidence は0〜1である必要があります" }
  }?.toFloat()
  val reason = json.requireNullableString("reason", 500)

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
    val volumeLabel = "第${position}巻"
    if (!stem.contains(volumeLabel, ignoreCase = true)) stem = "$stem $volumeLabel"
  }
  stem = stem.replace(WHITESPACE, " ").trim()

  val suffix = ".$extension"
  val maxStemLength = (MAX_NORMALIZED_FILE_NAME_CHARS - suffix.length).coerceAtLeast(1)
  if (stem.length > maxStemLength) stem = stem.take(maxStemLength).trimEnd(' ', '.')
  return validateProposedSmbFileName(originalFileName, stem + suffix)
}

private fun JSONObject.requireString(name: String, maxLength: Int): String {
  val value = get(name)
  require(value is String) { "$name は文字列である必要があります" }
  return value.trim().also {
    require(it.isNotEmpty()) { "$name は空にできません" }
    require(it.length <= maxLength) { "$name が長すぎます" }
  }
}

private fun JSONObject.requireNullableString(name: String, maxLength: Int): String? {
  val value = get(name)
  if (value === JSONObject.NULL) return null
  require(value is String) { "$name は文字列またはnullである必要があります" }
  return value.trim().takeIf(String::isNotEmpty)?.also {
    require(it.length <= maxLength) { "$name が長すぎます" }
  }
}

private fun JSONObject.requireNullableInt(name: String): Int? {
  val value = get(name)
  if (value === JSONObject.NULL) return null
  require(value is Number) { "$name は整数またはnullである必要があります" }
  val double = value.toDouble()
  val int = value.toInt()
  require(double == int.toDouble()) { "$name は整数である必要があります" }
  return int
}

private fun JSONObject.requireNullableDouble(name: String): Double? {
  val value = get(name)
  if (value === JSONObject.NULL) return null
  require(value is Number) { "$name は数値またはnullである必要があります" }
  return value.toDouble()
}

private val INVALID_FILE_NAME_CHARS = Regex("""[<>:"/\\|?*\x00-\x1F]""")
private val WHITESPACE = Regex("\\s+")
private const val MAX_FILE_NAME_PROMPT_CHARS = 500
private const val MAX_NORMALIZED_FILE_NAME_CHARS = 240
private const val MAX_COVER_INPUT_BYTES = 8 * 1024 * 1024
private const val MAX_VALIDATION_ERROR_CHARS = 500
