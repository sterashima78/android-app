package dev.terashima.yomitorirss.feature.library.data

internal const val KINDLE_JAPANESE_EDITION_SUFFIX = "(Japanese Edition)"

internal fun normalizeKindleBookTitle(title: String): String {
  val trimmed = title.trim()
  val normalized = trimmed
    .removeSuffix(KINDLE_JAPANESE_EDITION_SUFFIX)
    .trimEnd()
  return normalized.ifEmpty { trimmed }
}
