package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibraryBook
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.LibrarySyncResult
import dev.terashima.yomitorirss.feature.library.PreparedLibraryBook
import dev.terashima.yomitorirss.feature.library.SmbBookFormat
import dev.terashima.yomitorirss.feature.library.SmbLibraryRepository
import dev.terashima.yomitorirss.feature.library.SmbServerSettings
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.util.EnumSet
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray

class DefaultSmbLibraryRepository(
  context: Context,
  private val database: DatabaseConnection,
) : SmbLibraryRepository {
  private val appContext = context.applicationContext
  private val credentialStore = SmbCredentialStore(appContext)
  private val cacheRoot = File(appContext.cacheDir, CACHE_DIRECTORY).apply { mkdirs() }

  override suspend fun servers(): List<SmbServerSettings> {
    ensureSchema()
    return queryServers()
  }

  override suspend fun saveServer(
    settings: SmbServerSettings,
    password: String?,
  ): SmbServerSettings {
    ensureSchema()
    val normalized = settings.normalized(
      id = settings.id.ifBlank { UUID.randomUUID().toString() },
    )
    validateServer(normalized)
    if (!credentialStore.has(normalized.id)) {
      require(!password.isNullOrEmpty()) { "SMBパスワードを入力してください" }
    }
    if (!password.isNullOrEmpty()) credentialStore.save(normalized.id, password)

    val values = ContentValues().apply {
      put("id", normalized.id)
      put("name", normalized.name)
      put("host", normalized.host)
      put("port", normalized.port)
      put("share_name", normalized.share)
      put("root_path", normalized.rootPath)
      put("username", normalized.username)
      put("domain_name", normalized.domain)
      put("updated_at", System.currentTimeMillis())
    }
    database.writable.insertWithOnConflict(
      SERVER_TABLE,
      null,
      values,
      SQLiteDatabase.CONFLICT_REPLACE,
    )
    return normalized.copy(credentialConfigured = true)
  }

  override suspend fun deleteServer(serverId: String) {
    ensureSchema()
    database.writable.delete(SERVER_TABLE, "id = ?", arrayOf(serverId))
    credentialStore.delete(serverId)
  }

  override suspend fun sync(): LibrarySyncResult {
    ensureSchema()
    val servers = queryServers()
    require(servers.isNotEmpty()) { "SMBサーバを設定してください" }

    val books = buildList {
      servers.forEach { server ->
        val password = credentialStore.load(server.id)
          ?: error("${server.name} のSMB認証情報がありません")
        addAll(scanServer(server, password))
        require(size <= MAX_BOOKS) { "SMB蔵書が上限の $MAX_BOOKS 冊を超えています" }
      }
    }

    val syncedAt = System.currentTimeMillis()
    database.transaction {
      delete("library_items", "source = ?", arrayOf(LibrarySource.SMB.name))
      books.forEach { book ->
        insertOrThrow("library_items", null, book.toValues(syncedAt))
      }
      val sourceValues = ContentValues().apply {
        put("source", LibrarySource.SMB.name)
        put("account_label", "${servers.size}台のSMBサーバ")
        put("last_synced_at", syncedAt)
      }
      insertWithOnConflict(
        "library_sources",
        null,
        sourceValues,
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
    return LibrarySyncResult(books.size, syncedAt)
  }

  override suspend fun renameBook(
    book: LibraryBook,
    newFileName: String,
  ): LibraryBook {
    require(book.source == LibrarySource.SMB) { "SMB由来の書籍ではありません" }
    ensureSchema()
    val location = parseBookLocation(book) ?: error("SMB書籍の場所情報が壊れています")
    val currentName = location.path.substringAfterLast('\\')
    val targetName = renamedSmbFileName(location.path, newFileName)
    if (targetName == currentName) return book

    val parentPath = location.path.substringBeforeLast('\\', "")
    val targetPath = joinSmbPath(parentPath, targetName)
    val targetSourceId = stableSourceId(location.serverId, targetPath)
    val targetInfoUrl = smbBookInfoUrl(
      sourceId = targetSourceId,
      serverId = location.serverId,
      path = targetPath,
      size = location.size,
      modifiedAt = location.modifiedAt,
      format = location.format,
    )
    val renamedBook = book.copy(
      sourceId = targetSourceId,
      title = targetName.substringBeforeLast('.').ifBlank { targetName },
      infoUrl = targetInfoUrl,
    )
    val server = serverFor(location.serverId)
    val password = passwordFor(server)

    withShare(server, password) { share ->
      val collision = share.list(parentPath).any { entry ->
        entry.fileName.equals(targetName, ignoreCase = true) &&
          !entry.fileName.equals(currentName, ignoreCase = true)
      }
      require(!collision) { "同じ名前のファイルが既に存在します" }
      share.openFile(
        location.path,
        EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
        null,
        SMB2ShareAccess.ALL,
        SMB2CreateDisposition.FILE_OPEN,
        null,
      ).use { remoteFile ->
        remoteFile.rename(targetPath)
      }
    }

    try {
      migrateBookIdentity(book, renamedBook)
    } catch (error: Throwable) {
      runCatching {
        withShare(server, password) { share ->
          share.openFile(
            targetPath,
            EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null,
          ).use { remoteFile ->
            remoteFile.rename(location.path)
          }
        }
      }
      throw error
    }

    migrateCache(book.sourceId, targetSourceId)
    return renamedBook
  }

  override suspend fun deleteBook(book: LibraryBook) {
    require(book.source == LibrarySource.SMB) { "SMB由来の書籍ではありません" }
    ensureSchema()
    val location = parseBookLocation(book) ?: error("SMB書籍の場所情報が壊れています")
    val server = serverFor(location.serverId)
    val password = passwordFor(server)

    withShare(server, password) { share ->
      share.rm(location.path)
    }

    database.transaction {
      deleteBookMetadata(book.sourceId)
    }
    File(cacheRoot, book.sourceId).deleteRecursively()
  }

  override suspend fun prepareBook(
    book: LibraryBook,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
  ): PreparedLibraryBook {
    require(book.source == LibrarySource.SMB) { "SMB由来の書籍ではありません" }
    ensureSchema()
    val location = parseBookLocation(book) ?: error("SMB書籍の場所情報が壊れています")
    val extension = if (location.format == SmbBookFormat.PDF) "pdf" else "zip"
    val cacheDirectory = File(cacheRoot, book.sourceId).apply { mkdirs() }
    val cacheFile = File(cacheDirectory, "${location.size}-${location.modifiedAt}.$extension")
    if (cacheFile.isFile && cacheFile.length() == location.size) {
      cacheFile.setLastModified(System.currentTimeMillis())
      return book.prepared(cacheFile, location.format)
    }

    val server = serverFor(location.serverId)
    val password = passwordFor(server)
    val temp = File(cacheDirectory, "download.tmp")
    temp.delete()

    try {
      withShare(server, password) { share ->
        share.openFile(
          location.path,
          EnumSet.of(AccessMask.FILE_READ_DATA),
          null,
          SMB2ShareAccess.ALL,
          SMB2CreateDisposition.FILE_OPEN,
          null,
        ).use { remoteFile ->
          temp.outputStream().buffered(COPY_BUFFER_SIZE).use { output ->
            remoteFile.getInputStream().use { input ->
              val buffer = ByteArray(COPY_BUFFER_SIZE)
              var downloaded = 0L
              while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                downloaded += count
                onProgress(downloaded, location.size)
              }
            }
          }
        }
      }
      require(temp.length() == location.size) { "SMB書籍のダウンロードが途中で終了しました" }
      if (cacheFile.exists()) cacheFile.delete()
      check(temp.renameTo(cacheFile)) { "SMB書籍をキャッシュへ保存できませんでした" }
      cacheFile.setLastModified(System.currentTimeMillis())
      cleanupCache()
      return book.prepared(cacheFile, location.format)
    } finally {
      temp.delete()
    }
  }

  private fun scanServer(server: SmbServerSettings, password: String): List<LibraryBook> =
    withShare(server, password) { share ->
      buildList { scanDirectory(share, server, server.rootPath, depth = 0, result = this) }
    }

  private fun scanDirectory(
    share: DiskShare,
    server: SmbServerSettings,
    path: String,
    depth: Int,
    result: MutableList<LibraryBook>,
  ) {
    require(depth <= MAX_SCAN_DEPTH) { "SMBディレクトリ階層が深すぎます" }
    share.list(path).forEach { entry ->
      val name = entry.fileName
      if (name == "." || name == "..") return@forEach
      val childPath = joinSmbPath(path, name)
      val isDirectory = entry.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
      if (isDirectory) {
        scanDirectory(share, server, childPath, depth + 1, result)
        return@forEach
      }

      val format = formatFor(name) ?: return@forEach
      require(result.size < MAX_BOOKS) { "SMB蔵書が上限の $MAX_BOOKS 冊を超えています" }
      val size = entry.endOfFile
      val modifiedAt = entry.lastWriteTime.toEpochMillis()
      val sourceId = stableSourceId(server.id, childPath)
      result += LibraryBook(
        source = LibrarySource.SMB,
        sourceId = sourceId,
        title = name.substringBeforeLast('.').ifBlank { name },
        authors = emptyList(),
        publisher = null,
        publishedDate = null,
        description = "SMB: ${server.name}",
        isbn10 = null,
        isbn13 = null,
        thumbnailUrl = null,
        infoUrl = smbBookInfoUrl(
          sourceId = sourceId,
          serverId = server.id,
          path = childPath,
          size = size,
          modifiedAt = modifiedAt,
          format = format,
        ),
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

  private fun serverFor(serverId: String): SmbServerSettings =
    queryServers().firstOrNull { it.id == serverId }
      ?: error("この書籍のSMBサーバ設定がありません")

  private fun passwordFor(server: SmbServerSettings): String =
    credentialStore.load(server.id) ?: error("${server.name} のSMB認証情報がありません")

  private fun queryServers(): List<SmbServerSettings> = database.readable.rawQuery(
    """
      SELECT id, name, host, port, share_name, root_path, username, domain_name
      FROM $SERVER_TABLE
      ORDER BY name COLLATE NOCASE, id
    """.trimIndent(),
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
        add(
          SmbServerSettings(
            id = id,
            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
            host = cursor.getString(cursor.getColumnIndexOrThrow("host")),
            port = cursor.getInt(cursor.getColumnIndexOrThrow("port")),
            share = cursor.getString(cursor.getColumnIndexOrThrow("share_name")),
            rootPath = cursor.getString(cursor.getColumnIndexOrThrow("root_path")),
            username = cursor.getString(cursor.getColumnIndexOrThrow("username")),
            domain = cursor.getString(cursor.getColumnIndexOrThrow("domain_name")),
            credentialConfigured = credentialStore.has(id),
          ),
        )
      }
    }
  }

  private suspend fun ensureSchema() {
    // The library repository owns the shared library tables and initializes them lazily.
    DefaultLibraryRepository(database).snapshot()
    database.writable.execSQL(
      """
        CREATE TABLE IF NOT EXISTS $SERVER_TABLE(
          id TEXT PRIMARY KEY NOT NULL,
          name TEXT NOT NULL,
          host TEXT NOT NULL,
          port INTEGER NOT NULL,
          share_name TEXT NOT NULL,
          root_path TEXT NOT NULL,
          username TEXT NOT NULL,
          domain_name TEXT NOT NULL,
          updated_at INTEGER NOT NULL
        )
      """.trimIndent(),
    )
  }

  private fun migrateBookIdentity(
    original: LibraryBook,
    renamed: LibraryBook,
  ) {
    database.transaction {
      require(
        rawQuery(
          "SELECT 1 FROM library_items WHERE source = ? AND source_id = ? LIMIT 1",
          arrayOf(LibrarySource.SMB.name, renamed.sourceId),
        ).use { !it.moveToFirst() },
      ) { "変更後のファイルは既に蔵書に存在します" }

      val itemValues = ContentValues().apply {
        put("source_id", renamed.sourceId)
        put("title", renamed.title)
        put("info_url", renamed.infoUrl)
      }
      check(
        update(
          "library_items",
          itemValues,
          "source = ? AND source_id = ?",
          arrayOf(LibrarySource.SMB.name, original.sourceId),
        ) == 1,
      ) { "蔵書のファイル情報を更新できませんでした" }

      listOf(
        "hidden_library_items",
        "library_item_series",
        "library_item_series_exclusions",
      ).forEach { table ->
        update(
          table,
          ContentValues().apply { put("source_id", renamed.sourceId) },
          "source = ? AND source_id = ?",
          arrayOf(LibrarySource.SMB.name, original.sourceId),
        )
      }
    }
  }

  private fun SQLiteDatabase.deleteBookMetadata(sourceId: String) {
    listOf(
      "library_items",
      "hidden_library_items",
      "library_item_series",
      "library_item_series_exclusions",
    ).forEach { table ->
      delete(
        table,
        "source = ? AND source_id = ?",
        arrayOf(LibrarySource.SMB.name, sourceId),
      )
    }
  }

  private fun migrateCache(originalSourceId: String, renamedSourceId: String) {
    if (originalSourceId == renamedSourceId) return
    val original = File(cacheRoot, originalSourceId)
    if (!original.exists()) return
    val renamed = File(cacheRoot, renamedSourceId)
    renamed.deleteRecursively()
    if (!original.renameTo(renamed)) original.deleteRecursively()
  }

  private fun cleanupCache() {
    val files = cacheRoot.walkTopDown().filter(File::isFile).filter { it.name != "download.tmp" }.toList()
    var total = files.sumOf(File::length)
    if (total <= MAX_CACHE_BYTES) return
    files.sortedBy(File::lastModified).forEach { file ->
      if (total <= MAX_CACHE_BYTES) return
      val length = file.length()
      if (file.delete()) total -= length
    }
  }

  private fun parseBookLocation(book: LibraryBook): SmbBookLocation? {
    val uri = book.infoUrl?.let(Uri::parse) ?: return null
    if (uri.scheme != SMB_BOOK_SCHEME || uri.host != SMB_BOOK_HOST || uri.path != "/open") return null
    val serverId = uri.getQueryParameter("serverId")?.takeIf(String::isNotBlank) ?: return null
    val path = uri.getQueryParameter("path")?.takeIf(String::isNotBlank) ?: return null
    val size = uri.getQueryParameter("size")?.toLongOrNull()?.takeIf { it >= 0L } ?: return null
    val modified = uri.getQueryParameter("modified")?.toLongOrNull() ?: return null
    val format = uri.getQueryParameter("format")?.let { runCatching { SmbBookFormat.valueOf(it) }.getOrNull() }
      ?: return null
    return SmbBookLocation(serverId, path, size, modified, format)
  }

  private fun LibraryBook.prepared(file: File, format: SmbBookFormat): PreparedLibraryBook =
    PreparedLibraryBook(sourceId, title, file.absolutePath, format)

  private fun LibraryBook.toValues(syncedAt: Long): ContentValues = ContentValues().apply {
    put("source", source.name)
    put("source_id", sourceId)
    put("title", title)
    put("authors", JSONArray(authors).toString())
    putNull("publisher")
    putNull("published_date")
    put("description", description)
    putNull("isbn10")
    putNull("isbn13")
    putNull("thumbnail_url")
    put("info_url", infoUrl)
    put("narrators", "[]")
    putNull("duration")
    put("synced_at", syncedAt)
  }

  private data class SmbBookLocation(
    val serverId: String,
    val path: String,
    val size: Long,
    val modifiedAt: Long,
    val format: SmbBookFormat,
  )

  private companion object {
    const val SERVER_TABLE = "smb_library_servers"
    const val CACHE_DIRECTORY = "smb-books"
    const val SMB_BOOK_SCHEME = "yomitori"
    const val SMB_BOOK_HOST = "smb-book"
    const val MAX_SCAN_DEPTH = 32
    const val MAX_BOOKS = 20_000
    const val COPY_BUFFER_SIZE = 128 * 1024
    const val MAX_CACHE_BYTES = 2L * 1024 * 1024 * 1024
  }
}

internal fun renamedSmbFileName(currentPath: String, requestedName: String): String {
  val currentName = normalizeSmbPath(currentPath).substringAfterLast('\\')
  val extension = currentName.substringAfterLast('.', "").takeIf(String::isNotBlank)
    ?: error("現在のファイル拡張子を判定できません")
  val requested = requestedName.trim()
  require(requested.isNotEmpty()) { "新しいファイル名を入力してください" }
  require(requested != "." && requested != "..") { "このファイル名は使用できません" }
  require('/' !in requested && '\\' !in requested) { "ファイル名に / または \\ は使用できません" }
  require(requested.none(Char::isISOControl)) { "ファイル名に制御文字は使用できません" }

  val suffix = ".$extension"
  val target = if (requested.endsWith(suffix, ignoreCase = true)) requested else "$requested$suffix"
  require(target.substringBeforeLast('.').isNotBlank()) { "ファイル名を入力してください" }
  return target
}

private fun smbBookInfoUrl(
  sourceId: String,
  serverId: String,
  path: String,
  size: Long,
  modifiedAt: Long,
  format: SmbBookFormat,
): String = Uri.Builder()
  .scheme("yomitori")
  .authority("smb-book")
  .appendPath("open")
  .appendQueryParameter("sourceId", sourceId)
  .appendQueryParameter("serverId", serverId)
  .appendQueryParameter("path", path)
  .appendQueryParameter("size", size.toString())
  .appendQueryParameter("modified", modifiedAt.toString())
  .appendQueryParameter("format", format.name)
  .build()
  .toString()

private fun SmbServerSettings.normalized(id: String): SmbServerSettings = copy(
  id = id,
  name = name.trim(),
  host = host.trim(),
  share = share.trim().trim('/', '\\'),
  rootPath = normalizeSmbPath(rootPath),
  username = username.trim(),
  domain = domain.trim(),
)

private fun validateServer(settings: SmbServerSettings) {
  require(settings.name.isNotEmpty()) { "SMBサーバ名を入力してください" }
  require(settings.host.isNotEmpty()) { "SMBホストを入力してください" }
  require(settings.port in 1..65535) { "SMBポートが不正です" }
  require(settings.share.isNotEmpty()) { "SMB共有名を入力してください" }
  require(settings.username.isNotEmpty()) { "SMBユーザー名を入力してください" }
}

private fun formatFor(name: String): SmbBookFormat? = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
  "zip", "cbz" -> SmbBookFormat.ZIP
  "pdf" -> SmbBookFormat.PDF
  else -> null
}

private fun stableSourceId(serverId: String, path: String): String {
  val digest = MessageDigest.getInstance("SHA-256")
    .digest("$serverId\n${normalizeSmbPath(path)}".toByteArray(Charsets.UTF_8))
  return digest.joinToString("") { "%02x".format(it) }
}

private fun joinSmbPath(parent: String, child: String): String =
  listOf(normalizeSmbPath(parent), normalizeSmbPath(child))
    .filter(String::isNotEmpty)
    .joinToString("\\")

private fun normalizeSmbPath(path: String): String = path
  .replace('/', '\\')
  .split('\\')
  .filter { it.isNotBlank() && it != "." }
  .joinToString("\\")

private class SmbCredentialStore(context: Context) {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun has(serverId: String): Boolean = preferences.contains(serverId)

  fun save(serverId: String, password: String) {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key())
    val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
    val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
      Base64.encodeToString(encrypted, Base64.NO_WRAP)
    preferences.edit().putString(serverId, value).apply()
  }

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

  fun delete(serverId: String) {
    preferences.edit().remove(serverId).apply()
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
