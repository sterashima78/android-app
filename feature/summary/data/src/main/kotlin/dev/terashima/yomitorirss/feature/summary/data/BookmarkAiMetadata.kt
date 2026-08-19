package dev.terashima.yomitorirss.feature.summary.data

internal fun normalizeGeneratedTags(candidates: Iterable<String>): List<String> = candidates
  .asSequence()
  .map(::normalizeGeneratedTag)
  .filter { it.length in 1..40 }
  .distinctBy { it.lowercase() }
  .take(MAX_AUTO_TAGS)
  .toList()

private fun normalizeGeneratedTag(candidate: String): String {
  var value = candidate.trim()
    .removePrefix("-")
    .removePrefix("•")
    .trim()
    .trim('"', '\'', '`')
    .replace(Regex("^\\d+[.)、:]\\s*"), "")
    .trim()

  val prefixes = listOf("tags:", "tag:", "タグ:", "tags：", "tag：", "タグ：")
  prefixes.firstOrNull { prefix -> value.startsWith(prefix, ignoreCase = true) }
    ?.let { prefix -> value = value.drop(prefix.length).trim() }
  return value
}

internal const val MAX_AUTO_TAGS = 5
