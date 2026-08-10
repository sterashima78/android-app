package dev.terashima.yomitorirss.core.airuntime
import java.security.MessageDigest

object SummaryPrompt {
  const val ARTICLE_PLACEHOLDER = "{{article}}"
  const val MAX_LENGTH = 4_000

  val DEFAULT = """
    次の記事を日本語で要約してください。
    - 重要な点を3項目の箇条書きにする
    - 各項目は1〜2文にする
    - 固有名詞、数値、結論を優先する
    - 本文にない推測や意見を加えない
    - 前置きや見出しは付けない

    記事本文:
    $ARTICLE_PLACEHOLDER
  """.trimIndent()

  fun normalize(value: String): String {
    val normalized = value.trim()
    require(normalized.isNotBlank()) { "要約プロンプトを入力してください" }
    require(normalized.length <= MAX_LENGTH) { "要約プロンプトは${MAX_LENGTH}文字以内で入力してください" }
    return normalized
  }

  fun render(template: String, article: String): String {
    val normalized = normalize(template)
    return if (ARTICLE_PLACEHOLDER in normalized) {
      normalized.replace(ARTICLE_PLACEHOLDER, article)
    } else {
      "$normalized\n\n記事本文:\n$article"
    }
  }

  fun cacheKey(modelId: String, template: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
      .digest(normalize(template).toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return "$modelId:${digest.take(16)}"
  }
}
