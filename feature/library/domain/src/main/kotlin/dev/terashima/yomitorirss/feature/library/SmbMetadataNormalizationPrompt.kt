package dev.terashima.yomitorirss.feature.library

const val SMB_METADATA_NORMALIZATION_FILE_NAME_PLACEHOLDER = "{{fileName}}"
const val SMB_METADATA_NORMALIZATION_PROMPT_MAX_LENGTH = 4_000
private const val SMB_METADATA_NORMALIZATION_FILE_NAME_MAX_LENGTH = 500

val DEFAULT_SMB_METADATA_NORMALIZATION_PROMPT = """
  表紙画像と現在のファイル名の両方から、日本語を含む書籍の書誌情報を推定してください。
  現在のファイル名は重要な書誌情報の根拠です。誤りやノイズ、表記揺れがあり得ても、捨てずに表紙画像と照合してください。
  ファイル名にローマ字・英字で書籍名、著者名、シリーズ名、巻数が含まれている場合は、日本語の書誌情報を同定する手がかりとして積極的に利用してください。
  表紙とファイル名が矛盾する場合は、片方を機械的に優先せず、両方の一致点と書誌としての自然さから判断してください。

  特に漫画・ライトノベル・小説などのシリーズ作品では、シリーズ名と巻数を見落とさないことを最優先してください。
  シリーズ物では title に巻数表現を含めず、巻数を除いた作品タイトルを title、シリーズ名を seriesName、数値の巻数を seriesPosition に分離してください。
  title から巻数を取り除いた場合、その巻数を捨ててはいけません。判別した巻数は必ず seriesPosition に移し、seriesName と seriesPosition を両方指定してください。
  巻数が明確でも独立したシリーズ名の表記がない場合は、巻数を除いた作品タイトルを seriesName として扱ってください。

  「第12巻」「12巻」「Vol.12」「Volume 12」「vol12」「v12」「12Kan」「12kan」などは巻数の明確な根拠です。
  また、ファイル名末尾の1〜3桁の数字も重要な巻数候補です。数字を除いた部分が表紙の作品タイトルと一致し、シリーズ物として自然なら巻数として扱ってください。
  先頭の0は巻数値には含めません。例えば 08 は seriesPosition = 8 です。

  例:
  - 架空冒険譚08.pdf -> title = 架空冒険譚, seriesName = 架空冒険譚, seriesPosition = 8
  - 架空探偵録12.cbz -> title = 架空探偵録, seriesName = 架空探偵録, seriesPosition = 12
  - Kakuu_Bouken_Tan_12Kan.zip かつ表紙が「架空冒険譚 第12巻」 -> title = 架空冒険譚, seriesName = 架空冒険譚, seriesPosition = 12

  ただし、出版年、ISBN、版数、話数、日付、管理番号、または「1984」のように数字自体がタイトルの一部である場合は巻数にしないでください。
  ファイル名がローマ字・英字でも、表紙から日本語の正式名称を確認できる場合は title と seriesName に日本語の名称を使用してください。
  判別できない任意項目は推測で埋めず、ツール引数を省略してください。著者を判別できない場合は authors を空配列にしてください。
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