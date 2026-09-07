package dev.terashima.yomitorirss.feature.library

data class SmbMediaLocation(
  val serverId: String,
  val share: String,
  val rootPath: String = "",
)

data class SmbMediaFile(
  val serverId: String,
  val share: String,
  val rootPath: String,
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
 * Read-only capability for consumers that need files from an SMB connection profile.
 * Credentials and connection details never cross this contract.
 */
interface SmbMediaFileAccess {
  suspend fun listMediaFiles(
    location: SmbMediaLocation,
    extensions: Set<String>,
  ): List<SmbMediaFile>

  fun openMediaFile(
    location: SmbMediaLocation,
    path: String,
  ): SmbMediaReadHandle
}
