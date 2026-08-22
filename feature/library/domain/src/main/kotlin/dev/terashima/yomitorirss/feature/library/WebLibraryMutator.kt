package dev.terashima.yomitorirss.feature.library

interface WebLibraryMutator {
  suspend fun addWebBook(url: String): LibraryBook
  suspend fun removeWebBook(book: LibraryBook)
}

interface WebLibraryMutatorProvider {
  val webLibraryMutator: WebLibraryMutator
}
