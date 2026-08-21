package dev.terashima.yomitorirss.feature.library

const val SMB_METADATA_NORMALIZATION_FILE_NAME_PLACEHOLDER = "{{fileName}}"
const val SMB_METADATA_NORMALIZATION_PROMPT_MAX_LENGTH = 4_000
private const val SMB_METADATA_NORMALIZATION_FILE_NAME_MAX_LENGTH = 500

val DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT = """
  表紙画像と現在のファイル名の両方から、日本語を含む書籍の書誌情報を推定してください。
  現在のファイル名は重要な書誌情報の根拠です。誤りやノイズ、表記揺れがあり得ても、捨てずに表紙画像と照合してください。
  ファイル名にローマ字・英字で書籍名、著者名、シリーズ名、巻数が含まれている場合は、日本語の書誌情報を同定する手がかりとして積極的に利用してください。
  表紙とファイル名が矛盾する場合は、片方を機械的に優先せず、両方の一致点と書誌としての自然さから判断してください。
  判別できない任意項目は推測で埋めず、ツール引数を省略してください。著者を判別できない場合は authors を空配列にしてください。
  シリーズ物では title に巻数表現を含めず、シリーズ名を seriesName、数値の巻数を seriesPosition に分離してください。
  巻数を判別できた場合は seriesName と seriesPosition を必ず両方指定してください。例えば12巻目なら seriesPosition は 12 とします。
  ISBNは表紙画像またはファイル名から明確に読み取れる場合だけ指定してください。

  現在のファイル名:
  $SMB_METADATA_NORMALIZATION_FILE_NAME_PLACEHOLDER
""".trimIndent()

fun normalizeSmbMetadataNormalizationPrompt(value: String): String {
  val normalized = value.trim()
  require(normalized.isNotBlank()) { "書誌正規化プロンプトを入力してください" }
  require(normalized.length <= SMB_METADATA_NORMALIZATION_PROMPT_MAX_LENGTH) {
    "書誌正規化プロンプトは${SMB_METADATA_NORMALIZATION_PROMPT_MAX_LENGTH}文字以内で入力してください"
  }
  return normalized
}

fun renderSmbMetadataNormalizationPrompt(
  template: String,
  currentFileName: String,
): String {
  val normalized = normalizeSmbMetadataNormalizationPrompt(template)
  val fileName = currentFileName.trim().take(SMB_METADATA_NORMALIZATION_FILE_NAME_MAX_LENGTH)
  require(fileName.isNotBlank()) { "現在のファイル名がありません" }
  return if (SMB_METADATA_NORMALIZATION_FILE_NAME_PLACEHOLDER in normalized) {
    normalized.replace(SMB_METADATA_NORMALIZATION_FILE_NAME_PLACEHOLDER, fileName)
  } else {
    "$normalized\n\n現在のファイル名:\n$fileName"
  }
}

interface SmbMetadataNormalizationPromptRepository {
  fun prompt(): String

  fun update(prompt: String)

  fun reset()
}