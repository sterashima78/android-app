package dev.terashima.yomitorirss.feature.library

data class SmbMediaFile(
  val serverId: String,
  val path: String,
  val name: String,
  val size: Long,
  val modifiedAtEpochMillis: Long,
)

interface SmbMediaReadHandle : AutoCloseable {
  val size: Long

  fun read(
    position: Long,
    buffer: ByteArray,
    offset: Int,
    length: Int,
  ): Int
}

/**
 * Read-only capability for consumers that need files from Library-owned SMB connections.
 * Credentials and connection settings never cross this contract.
 */
interface SmbMediaFileAccess {
  suspend fun listMediaFiles(extensions: Set<String>): List<SmbMediaFile>

  fun openMediaFile(
    serverId: String,
    path: String,
  ): SmbMediaReadHandle
}
