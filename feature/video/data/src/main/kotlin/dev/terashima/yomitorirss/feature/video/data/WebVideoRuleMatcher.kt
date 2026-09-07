package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule
import java.net.URI

internal fun validateWebVideoExtractorRule(rule: WebVideoExtractorRule) {
  require(rule.urlPattern.startsWith("https://")) { "URLパターンは https:// から始めてください" }
  require(rule.timeoutSeconds in 1..60) { "タイムアウトは1〜60秒で指定してください" }
  require(
    listOf(rule.titleExtractorCode, rule.thumbnailExtractorCode, rule.playbackExtractorCode)
      .any { !it.isNullOrBlank() },
  ) { "少なくとも1つの抽出スクリプトを設定してください" }
}

internal fun findMatchingWebVideoExtractorRule(
  rules: List<WebVideoExtractorRule>,
  url: String,
): WebVideoExtractorRule? {
  val uri = runCatching { URI(url) }.getOrNull() ?: return null
  if (!uri.scheme.equals("https", ignoreCase = true)) return null
  return rules
    .asSequence()
    .filter { globMatches(it.urlPattern, url) }
    .sortedWith(
      compareByDescending<WebVideoExtractorRule> { ruleSpecificity(it.urlPattern) }
        .thenByDescending { it.updatedAtEpochMillis },
    )
    .firstOrNull()
}

private fun ruleSpecificity(pattern: String): Int = pattern.count { it != '*' && it != '?' }

private fun globMatches(pattern: String, value: String): Boolean {
  val regex = buildString {
    append('^')
    pattern.forEach { char ->
      when (char) {
        '*' -> append(".*")
        '?' -> append('.')
        '.', '(', ')', '[', ']', '{', '}', '+', '^', '$', '|', '\\' -> {
          append('\\')
          append(char)
        }
        else -> append(char)
      }
    }
    append('$')
  }
  return Regex(regex).matches(value)
}
