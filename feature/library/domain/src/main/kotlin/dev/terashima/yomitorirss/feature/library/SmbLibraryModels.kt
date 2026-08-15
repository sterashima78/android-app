package dev.terashima.yomitorirss.feature.library

data class SmbServerSettings(
  val id: String,
  val name: String,
  val host: String,
  val port: Int = 445,
  val share: String,
  val rootPath: String = "",
  val username: String,
  val domain: String = "",
  val credentialConfigured: Boolean = false,
)

data class PreparedLibraryBook(
  val sourceId: String,
  val title: String,
  val localPath: String,
  val format: SmbBookFormat,
)

enum class SmbBookFormat {
  ZIP,
  PDF,
}

interface SmbLibraryRepository {
  suspend fun servers(): List<SmbServerSettings>

  suspend fun saveServer(
    settings: SmbServerSettings,
    password: String?,
  ): SmbServerSettings

  suspend fun deleteServer(serverId: String)

  suspend fun sync(): LibrarySyncResult

  suspend fun renameBook(
    book: LibraryBook,
    newFileName: String,
  ): LibraryBook

  suspend fun deleteBook(book: LibraryBook)

  suspend fun prepareBook(
    book: LibraryBook,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
  ): PreparedLibraryBook
}
