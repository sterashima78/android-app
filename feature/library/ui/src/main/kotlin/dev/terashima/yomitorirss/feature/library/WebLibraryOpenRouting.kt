package dev.terashima.yomitorirss.feature.library

internal fun webLibraryOpenUrls(books: List<LibraryBook>): Set<String> =
  books.asSequence()
    .filter { it.source == LibrarySource.WEB }
    .mapNotNull { it.openUrl()?.trim()?.takeIf(String::isNotEmpty) }
    .toSet()
