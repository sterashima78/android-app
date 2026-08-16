package dev.terashima.yomitorirss.feature.article

enum class ContentType {
  ARTICLE,
  COMIC,
}

fun resolveContentType(
  articleOverride: ContentType?,
  feedOverride: ContentType?,
  folderOverride: ContentType?,
): ContentType = articleOverride ?: feedOverride ?: folderOverride ?: ContentType.ARTICLE

fun ContentType.allowsAutomaticAiEnrichment(): Boolean = this == ContentType.ARTICLE

fun String?.toContentTypeOrNull(): ContentType? =
  this?.let { value -> ContentType.entries.firstOrNull { it.name == value } }
