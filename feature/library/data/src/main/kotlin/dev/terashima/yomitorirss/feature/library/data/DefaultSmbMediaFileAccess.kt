package dev.terashima.yomitorirss.feature.library.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
import dev.terashima.yomitorirss.feature.library.SmbMediaFile
import dev.terashima.yomitorirss.feature.library.SmbMediaFileAccess
import dev.terashima.yomitorirss.feature.library.SmbMediaReadHandle
import dev.terashima.yomitorirss.feature.library.SmbServerSettings
import java.security.KeyStore
import java.util.EnumSet
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Read-only SMB media adapter owned by Library. It reuses the same Library server table and
 * encrypted credential store but never exposes credential values through the Domain contract.
 */
class DefaultSmbMediaFileAccess(
  context: Context,
  private val database: DatabaseConnection,
) : SmbMediaFileAccess {
  private val credentialStore = MediaSmbCredentialReader(context.applicationContext)

  override suspend fun listMediaFiles(extensions: Set<String>): List<SmbMediaFile> {
    ensureLibrarySchema(database.writable)
    val normalizedExtensions = extensions.map { it.lowercase().trimStart('.') }.toSet()
    require(normalizedExtensions.isNotEmpty()) { "動画拡張子が指定されていません" }

    return buildList {
      queryServers().forEach { server ->
        val password = credentialStore.load(server.id)
          ?: error("${server.name} のSMB認証情報がありません")
        withShare(server, password) { share ->
          scanDirectory(
            share = share,
            server = server,
            path = server.rootPath,
            depth = 0,
            extensions = normalizedExtensions,
            result = this,
          )
        }
        require(size <= MAX_MEDIA_FILES) { "SMB動画が上限の $MAX_MEDIA_FILES 件を超えています" }
      }
    }
  }

  override fun openMediaFile(serverId: String, path: String): SmbMediaReadHandle {
    ensureLibrarySchema(database.writable)
    val server = queryServers().firstOrNull { it.id == serverId }
      ?: error("SMBサーバ設定がありません")
    val normalizedPath = normalizeMediaSmbPath(path)
    require(isPathWithinRoot(normalizedPath, server.rootPath)) { "SMB動画のパスが設定範囲外です" }
    val password = credentialStore.load(server.id)
      ?: error("${server.name} のSMB認証情報がありません")

    val client = SMBClient()
    var connection: Connection? = null
    var session: Session? = null
    var share: DiskShare? = null
    var remoteFile: SmbFile? = null
    try {
      connection = client.connect(server.host, server.port)
      val auth = AuthenticationContext(server.username, password.toCharArray(), server.domain)
      session = connection.authenticate(auth)
      share = session.connectShare(server.share) as? DiskShare
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

  private fun queryServers(): List<SmbServerSettings> = database.readable.rawQuery(
    """
      SELECT id, name, host, port, share_name, root_path, username, domain_name
      FROM smb_library_servers
      ORDER BY name COLLATE NOCASE, id
    """.trimIndent(),
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(
          SmbServerSettings(
            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            host = cursor.getString(cursor.getColumnIndexOrThrow("host")),
            port = cursor.getInt(cursor.getColumnIndexOrThrow("port")),
            share = cursor.getString(cursor.getColumnIndexOrThrow("share_name")),
            rootPath = cursor.getString(cursor.getColumnIndexOrThrow("root_path")),
            username = cursor.getString(cursor.getColumnIndexOrThrow("username")),
            domain = cursor.getString(cursor.getColumnIndexOrThrow("domain_name")),
            credentialConfigured = true,
          ),
        )
      }
    }
  }

  private fun scanDirectory(
    share: DiskShare,
    server: SmbServerSettings,
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
        scanDirectory(share, server, childPath, depth + 1, extensions, result)
        return@forEach
      }
      val extension = name.substringAfterLast('.', "").lowercase()
      if (extension !in extensions) return@forEach
      require(result.size < MAX_MEDIA_FILES) { "SMB動画が上限の $MAX_MEDIA_FILES 件を超えています" }
      result += SmbMediaFile(
        serverId = server.id,
        path = childPath,
        name = name,
        size = entry.endOfFile,
        modifiedAtEpochMillis = entry.lastWriteTime.toEpochMillis(),
      )
    }
  }

  private fun <T> withShare(
    server: SmbServerSettings,
    password: String,
    block: (DiskShare) -> T,
  ): T = SMBClient().use { client ->
    client.connect(server.host, server.port).use { connection ->
      val auth = AuthenticationContext(server.username, password.toCharArray(), server.domain)
      connection.authenticate(auth).use { session ->
        val share = session.connectShare(server.share) as? DiskShare
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

private class MediaSmbCredentialReader(context: Context) {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun load(serverId: String): String? {
    val value = preferences.getString(serverId, null) ?: return null
    val parts = value.split(':', limit = 2)
    if (parts.size != 2) return null
    return runCatching {
      val iv = Base64.decode(parts[0], Base64.NO_WRAP)
      val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
      cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()
  }

  private fun key(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .build(),
    )
    return generator.generateKey()
  }

  private companion object {
    const val PREFERENCES_NAME = "smb_library_credentials"
    const val KEY_ALIAS = "yomitori.smb.library.credentials.v1"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
  }
}
