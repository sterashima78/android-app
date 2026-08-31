package dev.terashima.yomitorirss.feature.chat.data

internal data class RetrievalField(
  val value: String,
  val weight: Int = 1,
) {
  init {
    require(weight > 0) { "Retrieval field weight must be positive" }
  }
}

/**
 * Ranks candidates with deterministic lexical matching before any model-side query reformulation.
 *
 * Every whitespace-delimited term must match at least one field. Terms may match different fields,
 * so a query such as "Android memory" can match a title containing Android and a summary containing
 * memory without requiring the exact phrase to exist in one field. Exact phrase matches receive a
 * bonus, and higher-weight fields rank ahead of lower-weight metadata matches.
 */
internal fun <T> rankByQuery(
  items: List<T>,
  query: String,
  fields: (T) -> List<RetrievalField>,
): List<T> {
  val normalizedQuery = query.trim()
  if (normalizedQuery.isBlank()) return items

  val terms = normalizedQuery
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)

  return items.mapIndexedNotNull { index, item ->
    val candidateFields = fields(item).filter { it.value.isNotBlank() }
    val termScores = terms.map { term ->
      candidateFields.maxOfOrNull { field ->
        if (field.value.contains(term, ignoreCase = true)) field.weight else 0
      } ?: 0
    }
    if (termScores.any { it == 0 }) {
      null
    } else {
      val phraseBonus = candidateFields.maxOfOrNull { field ->
        if (field.value.contains(normalizedQuery, ignoreCase = true)) field.weight * PHRASE_BONUS_MULTIPLIER else 0
      } ?: 0
      RankedCandidate(
        item = item,
        score = termScores.sum() + phraseBonus,
        originalIndex = index,
      )
    }
  }.sortedWith(
    compareByDescending<RankedCandidate<T>> { it.score }
      .thenBy { it.originalIndex },
  ).map(RankedCandidate<T>::item)
}

internal fun compactExcerpt(value: String, maxChars: Int): String {
  require(maxChars > 0) { "maxChars must be positive" }
  val compact = value.replace(Regex("\\s+"), " ").trim()
  if (compact.length <= maxChars) return compact
  if (maxChars == 1) return "…"
  return compact.take(maxChars - 1).trimEnd() + "…"
}

private data class RankedCandidate<T>(
  val item: T,
  val score: Int,
  val originalIndex: Int,
)

private const val PHRASE_BONUS_MULTIPLIER = 4
