package dev.terashima.yomitorirss.feature.summary

import java.security.MessageDigest

const val SUMMARY_ARTICLE_PLACEHOLDER = "{{article}}"
const val SUMMARY_PROMPT_MAX_LENGTH = 4_000

val DEFAULT_SUMMARY_PROMPT = """
  次の記事を日本語で要約してください。
  - 重要な点を3項目の箇条書きにする
  - 各項目は1〜2文にする
  - 固有名詞、数値、結論を優先する
  - 本文にない推測や意見を加えない
  - 前置きや見出しは付けない

  記事本文:
  $SUMMARY_ARTICLE_PLACEHOLDER
""".trimIndent()

fun normalizeSummaryPrompt(value: String): String {
  val normalized = value.trim()
  require(normalized.isNotBlank()) { "要約プロンプトを入力してください" }
  require(normalized.length <= SUMMARY_PROMPT_MAX_LENGTH) {
    "要約プロンプトは${SUMMARY_PROMPT_MAX_LENGTH}文字以内で入力してください"
  }
  return normalized
}

fun renderSummaryPrompt(template: String, article: String): String {
  val normalized = normalizeSummaryPrompt(template)
  return if (SUMMARY_ARTICLE_PLACEHOLDER in normalized) {
    normalized.replace(SUMMARY_ARTICLE_PLACEHOLDER, article)
  } else {
    "$normalized\n\n記事本文:\n$article"
  }
}

fun summaryCacheKey(modelId: String, template: String, variant: String? = null): String {
  val digest = MessageDigest.getInstance("SHA-256")
    .digest(normalizeSummaryPrompt(template).toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
  val variantSuffix = variant?.takeIf(String::isNotBlank)?.let { ":$it" }.orEmpty()
  return "$modelId:${digest.take(16)}$variantSuffix"
}
