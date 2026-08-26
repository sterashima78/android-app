package dev.terashima.yomitorirss.feature.library

import java.net.URI

internal fun LibraryBook.needsWebMetadataRepair(): Boolean =
  missingWebMetadataLabels().isNotEmpty()

internal fun LibraryBook.missingWebMetadataLabels(): List<String> {
  if (source != LibrarySource.WEB) return emptyList()
  return buildList {
    if (isWebHostFallbackTitle()) add("タイトル")
    if (thumbnailUrl.isNullOrBlank()) add("表紙")
  }
}

private fun LibraryBook.isWebHostFallbackTitle(): Boolean {
  val candidateUrl = infoUrl?.takeIf(String::isNotBlank) ?: sourceId
  val host = runCatching { URI(candidateUrl).host?.removePrefix("www.") }.getOrNull()
  return !host.isNullOrBlank() && title.equals(host, ignoreCase = true)
}
