package dev.terashima.yomitorirss.feature.library.data

import android.content.Context
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfile
import dev.terashima.yomitorirss.feature.library.SmbMediaFile
import dev.terashima.yomitorirss.feature.library.SmbMediaFileAccess
import dev.terashima.yomitorirss.feature.library.SmbMediaLocation
import dev.terashima.yomitorirss.feature.library.SmbMediaReadHandle
import java.util.EnumSet

/**
 * Read-only SMB media adapter. Connection details and credentials remain inside Library Data while
 * callers explicitly provide the share/root they own as feature settings.
 */
class DefaultSmbMediaFileAccess(
  context: Context,
  private val database: DatabaseConnection,
) : SmbMediaFileAccess {
  private val credentialStore = SmbCredentialReader(context.applicationContext)

  override suspend fun listMediaFiles(
    location: SmbMediaLocation,
    extensions: Set<String>,
  ): List<SmbMediaFile> {
    ensureLibrarySchema(database.writable)
    val normalized = normalizeLocation(location)
    val profile = queryProfile(normalized.serverId)
    val password = credentialStore.load(profile.id)
      ?: error("${profile.name} のSMB認証情報がありません")
    val normalizedExtensions = extensions.map { it.lowercase().trimStart('.') }.toSet()
    require(normalizedExtensions.isNotEmpty()) { "動画拡張子が指定されていません" }

    return withShare(profile, normalized.share, password) { share ->
      buildList {
        scanDirectory(
          share = share,
          location = normalized,
          path = normalized.rootPath,
          depth = 0,
          extensions = normalizedExtensions,
          result = this,
        )
      }
    }
  }

  override fun openMediaFile(
    location: SmbMediaLocation,
    path: String,
  ): SmbMediaReadHandle {
    ensureLibrarySchema(database.writable)
    val normalized = normalizeLocation(location)
    val normalizedPath = normalizeMediaSmbPath(path)
    require(isPathWithinRoot(normalizedPath, normalized.rootPath)) { "SMB動画のパスが設定範囲外です" }
    val profile = queryProfile(normalized.serverId)
    val password = credentialStore.load(profile.id)
      ?: error("${profile.name} のSMB認証情報がありません")

    val client = SMBClient()
    var connection: Connection? = null
    var session: Session? = null
    var share: DiskShare? = null
    var remoteFile: SmbFile? = null
    try {
      connection = client.connect(profile.host, profile.port)
      val auth = AuthenticationContext(profile.username, password.toCharArray(), profile.domain)
      session = connection.authenticate(auth)
      share = session.connectShare(normalized.share) as? DiskShare
        ?: error("SMB共有がディスク共有ではありません")
      remoteFile = share.openFile(
        normalizedPath,
        EnumSet.of(AccessMask.FILE_READ_DATA, AccessMask.FILE_READ_ATTRIBUTES),
        null,
        SMB2ShareAccess.ALL,
        SMB2CreateDisposition.FILE_OPEN,
        null,
      )
      val size = remoteFile.getLength()
      return OpenedSmbMediaReadHandle(
        client = client,
        connection = connection,
        session = session,
        share = share,
        remoteFile = remoteFile,
        size = size,
      )
    } catch (error: Throwable) {
      runCatching { remoteFile?.close() }
      runCatching { share?.close() }
      runCatching { session?.close() }
      runCatching { connection?.close() }
      runCatching { client.close() }
      throw error
    }
  }

  private fun queryProfile(serverId: String): SmbConnectionProfile = database.readable.rawQuery(
    """
      SELECT id, name, host, port, username, domain_name
      FROM smb_connection_profiles
      WHERE id = ?
      LIMIT 1
    """.trimIndent(),
    arrayOf(serverId),
  ).use { cursor ->
    if (!cursor.moveToFirst()) error("SMB接続設定がありません")
    SmbConnectionProfile(
      id = cursor.getString(0),
      name = cursor.getString(1),
      host = cursor.getString(2),
      port = cursor.getInt(3),
      username = cursor.getString(4),
      domain = cursor.getString(5),
      credentialConfigured = true,
    )
  }

  private fun scanDirectory(
    share: DiskShare,
    location: SmbMediaLocation,
    path: String,
    depth: Int,
    extensions: Set<String>,
    result: MutableList<SmbMediaFile>,
  ) {
    require(depth <= MAX_SCAN_DEPTH) { "SMBディレクトリ階層が深すぎます" }
    share.list(path).forEach { entry ->
      val name = entry.fileName
      if (name == "." || name == "..") return@forEach
      val childPath = joinMediaSmbPath(path, name)
      val isDirectory = entry.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
      if (isDirectory) {
        scanDirectory(share, location, childPath, depth + 1, extensions, result)
        return@forEach
      }
      val extension = name.substringAfterLast('.', "").lowercase()
      if (extension !in extensions) return@forEach
      require(result.size < MAX_MEDIA_FILES) { "SMB動画が上限の $MAX_MEDIA_FILES 件を超えています" }
      result += SmbMediaFile(
        serverId = location.serverId,
        share = location.share,
        rootPath = location.rootPath,
        path = childPath,
        name = name,
        size = entry.endOfFile,
        modifiedAtEpochMillis = entry.lastWriteTime.toEpochMillis(),
      )
    }
  }

  private fun <T> withShare(
    profile: SmbConnectionProfile,
    shareName: String,
    password: String,
    block: (DiskShare) -> T,
  ): T = SMBClient().use { client ->
    client.connect(profile.host, profile.port).use { connection ->
      val auth = AuthenticationContext(profile.username, password.toCharArray(), profile.domain)
      connection.authenticate(auth).use { session ->
        val share = session.connectShare(shareName) as? DiskShare
          ?: error("SMB共有がディスク共有ではありません")
        share.use(block)
      }
    }
  }

  private companion object {
    const val MAX_SCAN_DEPTH = 32
    const val MAX_MEDIA_FILES = 50_000
  }
}

private class OpenedSmbMediaReadHandle(
  private val client: SMBClient,
  private val connection: Connection,
  private val session: Session,
  private val share: DiskShare,
  private val remoteFile: SmbFile,
  override val size: Long,
) : SmbMediaReadHandle {
  private var closed = false

  override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
    check(!closed) { "SMB動画は既に閉じられています" }
    if (position >= size) return -1
    return remoteFile.read(buffer, position, offset, length)
  }

  override fun close() {
    if (closed) return
    closed = true
    runCatching { remoteFile.close() }
    runCatching { share.close() }
    runCatching { session.close() }
    runCatching { connection.close() }
    runCatching { client.close() }
  }
}

private fun normalizeLocation(location: SmbMediaLocation): SmbMediaLocation = location.copy(
  serverId = location.serverId.trim(),
  share = location.share.trim().trim('/', '\\'),
  rootPath = normalizeMediaSmbPath(location.rootPath),
).also { normalized ->
  require(normalized.serverId.isNotBlank()) { "SMB接続設定がありません" }
  require(normalized.share.isNotBlank()) { "SMB共有名がありません" }
}

internal fun normalizeMediaSmbPath(path: String): String {
  val segments = path
    .replace('/', '\\')
    .split('\\')
    .filter { it.isNotBlank() && it != "." }
  require(".." !in segments) { "SMB動画のパスに .. は使用できません" }
  return segments.joinToString("\\")
}

private fun joinMediaSmbPath(parent: String, child: String): String =
  listOf(normalizeMediaSmbPath(parent), normalizeMediaSmbPath(child))
    .filter(String::isNotEmpty)
    .joinToString("\\")

private fun isPathWithinRoot(path: String, rootPath: String): Boolean {
  val root = normalizeMediaSmbPath(rootPath)
  return root.isEmpty() || path == root || path.startsWith("$root\\")
}
