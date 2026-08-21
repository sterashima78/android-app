package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.security.keystore.KeyProperties
import android.util.Base64
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.LibrarySource
import dev.terashima.yomitorirss.feature.library.SmbBookFormat
import dev.terashima.yomitorirss.feature.library.SmbServerSettings
import java.io.File
import java.security.KeyStore
import java.util.EnumSet
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal sealed interface SmbCoverPrefetchOutcome {
  data object Completed : SmbCoverPrefetchOutcome
  data class Skipped(val reason: String) : SmbCoverPrefetchOutcome
}

internal class SmbCoverPrefetchProcessor(
  context: Context,
  private val database: DatabaseConnection,
) {
  private val appContext = context.applicationContext
  private val credentialReader = SmbCoverPrefetchCredentialReader(appContext)
  private val coverCacheCoordinator = SmbCoverCacheCoordinator(appContext, database)
  private val bookCacheRoot = File(appContext.cacheDir, BOOK_CACHE_DIRECTORY)
  private val tempRoot = File(appContext.cacheDir, TEMP_DIRECTORY).apply { mkdirs() }

  fun prefetchCover(
    sourceId: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
  ): SmbCoverPrefetchOutcome {
    val book = queryBook(sourceId)
      ?: return SmbCoverPrefetchOutcome.Skipped("蔵書から削除されています")
    val location = parseBookLocation(book.infoUrl)
      ?: return SmbCoverPrefetchOutcome.Skipped("SMB書籍の場所情報を読み取れません")

    existingSmbBookCoverUrl(
      context = appContext,
      sourceId = sourceId,
      size = location.size,
      modifiedAt = location.modifiedAt,
    )?.let { coverUrl ->
      updateThumbnail(sourceId, coverUrl)
      return SmbCoverPrefetchOutcome.Completed
    }

    val cachedBookFile = cachedBookFile(sourceId, location)
    if (cachedBookFile.isFile && cachedBookFile.length() == location.size) {
      val coverUrl = ensureSmbBookCoverFromLocal(
        context = appContext,
        sourceId = sourceId,
        size = location.size,
        modifiedAt = location.modifiedAt,
        format = location.format,
        localBookFile = cachedBookFile,
      ) ?: return skippedWithFileSize("キャッシュ済み書籍から表紙を生成できませんでした", location.size)
      updateThumbnail(sourceId, coverUrl)
      return SmbCoverPrefetchOutcome.Completed
    }

    val server = queryServer(location.serverId)
      ?: return skippedWithFileSize("SMBサーバ設定が見つかりません", location.size)
    val password = credentialReader.load(server.id)
      ?: error("${server.name} のSMB認証情報を読み取れません")

    return when (location.format) {
      SmbBookFormat.ZIP -> prefetchZipCover(
        sourceId = sourceId,
        location = location,
        server = server,
        password = password,
      )

      SmbBookFormat.PDF -> prefetchPdfCover(
        sourceId = sourceId,
        location = location,
        server = server,
        password = password,
        onProgress = onProgress,
      )
    }
  }

  private fun prefetchZipCover(
    sourceId: String,
    location: SmbCoverBookLocation,
    server: SmbServerSettings,
    password: String,
  ): SmbCoverPrefetchOutcome = withShare(server, password) { share ->
    val coverUrl = prefetchRemoteSmbZipCover(
      context = appContext,
      share = share,
      remotePath = location.path,
      sourceId = sourceId,
      size = location.size,
      modifiedAt = location.modifiedAt,
    ) ?: return@withShare skippedWithFileSize(
      "ZIP先頭${SMB_ZIP_COVER_SCAN_MAX_BYTES / (1024 * 1024)}MiB以内から表紙画像を見つけられませんでした",
      location.size,
    )
    updateThumbnail(sourceId, coverUrl)
    SmbCoverPrefetchOutcome.Completed
  }

  private fun prefetchPdfCover(
    sourceId: String,
    location: SmbCoverBookLocation,
    server: SmbServerSettings,
    password: String,
    onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
  ): SmbCoverPrefetchOutcome {
    if (!shouldPrefetchPdf(location.size)) {
      return skippedWithFileSize(
        "PDFが${SMB_PDF_COVER_PREFETCH_MAX_BYTES / (1024 * 1024)}MiBを超えるため自動取得しません",
        location.size,
      )
    }

    val temp = File(tempRoot, "$sourceId-${location.size}-${location.modifiedAt}.pdf.tmp")
    temp.delete()
    return try {
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
              onProgress(downloaded, location.size)
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
      require(temp.length() == location.size) { "PDFの一時取得が途中で終了しました" }
      val coverUrl = ensureSmbBookCoverFromLocal(
        context = appContext,
        sourceId = sourceId,
        size = location.size,
        modifiedAt = location.modifiedAt,
        format = SmbBookFormat.PDF,
        localBookFile = temp,
      ) ?: return skippedWithFileSize("PDFの1ページ目から表紙を生成できませんでした", location.size)
      updateThumbnail(sourceId, coverUrl)
      SmbCoverPrefetchOutcome.Completed
    } finally {
      temp.delete()
    }
  }

  private fun skippedWithFileSize(reason: String, size: Long): SmbCoverPrefetchOutcome.Skipped =
    SmbCoverPrefetchOutcome.Skipped(smbCoverPrefetchSkippedReason(reason, size))

  private fun queryBook(sourceId: String): SmbCoverBookRow? = database.readable.rawQuery(
    "SELECT info_url FROM library_items WHERE source = ? AND source_id = ? LIMIT 1",
    arrayOf(LibrarySource.SMB.name, sourceId),
  ).use { cursor ->
    if (!cursor.moveToFirst() || cursor.isNull(0)) null else SmbCoverBookRow(cursor.getString(0))
  }

  private fun queryServer(serverId: String): SmbServerSettings? = database.readable.rawQuery(
    """
      SELECT id, name, host, port, share_name, root_path, username, domain_name
      FROM smb_library_servers
      WHERE id = ?
      LIMIT 1
    """.trimIndent(),
    arrayOf(serverId),
  ).use { cursor ->
    if (!cursor.moveToFirst()) return@use null
    SmbServerSettings(
      id = cursor.getString(0),
      name = cursor.getString(1),
      host = cursor.getString(2),
      port = cursor.getInt(3),
      share = cursor.getString(4),
      rootPath = cursor.getString(5),
      username = cursor.getString(6),
      domain = cursor.getString(7),
      credentialConfigured = true,
    )
  }

  private fun parseBookLocation(infoUrl: String): SmbCoverBookLocation? {
    val uri = runCatching { Uri.parse(infoUrl) }.getOrNull() ?: return null
    if (uri.scheme != "yomitori" || uri.host != "smb-book" || uri.path != "/open") return null
    val serverId = uri.getQueryParameter("serverId")?.takeIf(String::isNotBlank) ?: return null
    val path = uri.getQueryParameter("path")?.takeIf(String::isNotBlank) ?: return null
    val size = uri.getQueryParameter("size")?.toLongOrNull()?.takeIf { it >= 0L } ?: return null
    val modifiedAt = uri.getQueryParameter("modified")?.toLongOrNull() ?: return null
    val format = uri.getQueryParameter("format")
      ?.let { runCatching { SmbBookFormat.valueOf(it) }.getOrNull() }
      ?: return null
    return SmbCoverBookLocation(serverId, path, size, modifiedAt, format)
  }

  private fun cachedBookFile(sourceId: String, location: SmbCoverBookLocation): File {
    val extension = if (location.format == SmbBookFormat.PDF) "pdf" else "zip"
    return File(File(bookCacheRoot, sourceId), "${location.size}-${location.modifiedAt}.$extension")
  }

  private fun updateThumbnail(sourceId: String, coverUrl: String) {
    database.writable.update(
      "library_items",
      ContentValues().apply { put("thumbnail_url", coverUrl) },
      "source = ? AND source_id = ?",
      arrayOf(LibrarySource.SMB.name, sourceId),
    )
    coverCacheCoordinator.trim(protectedUrl = coverUrl)
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

  private data class SmbCoverBookRow(val infoUrl: String)

  private data class SmbCoverBookLocation(
    val serverId: String,
    val path: String,
    val size: Long,
    val modifiedAt: Long,
    val format: SmbBookFormat,
  )

  private companion object {
    const val BOOK_CACHE_DIRECTORY = "smb-books"
    const val TEMP_DIRECTORY = "smb-cover-prefetch-temp"
    const val COPY_BUFFER_SIZE = 128 * 1024
  }
}

internal const val SMB_PDF_COVER_PREFETCH_MAX_BYTES = 256L * 1024 * 1024

internal fun shouldPrefetchPdf(size: Long): Boolean =
  size in 0L..SMB_PDF_COVER_PREFETCH_MAX_BYTES

internal fun smbCoverPrefetchSkippedReason(reason: String, size: Long): String =
  "$reason（ファイルサイズ: ${formatSmbBookFileSize(size)}）"

internal fun formatSmbBookFileSize(bytes: Long): String = when {
  bytes >= 1024L * 1024L * 1024L -> String.format(
    Locale.US,
    "%.1f GiB",
    bytes.toDouble() / (1024.0 * 1024.0 * 1024.0),
  )
  bytes >= 1024L * 1024L -> String.format(
    Locale.US,
    "%.1f MiB",
    bytes.toDouble() / (1024.0 * 1024.0),
  )
  bytes >= 1024L -> String.format(Locale.US, "%.1f KiB", bytes.toDouble() / 1024.0)
  else -> "$bytes B"
}

private class SmbCoverPrefetchCredentialReader(context: Context) {
  private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun load(serverId: String): String? {
    val value = preferences.getString(serverId, null) ?: return null
    val parts = value.split(':', limit = 2)
    if (parts.size != 2) return null
    return runCatching {
      val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
      val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return null
      require(secretKey.algorithm == KeyProperties.KEY_ALGORITHM_AES)
      val iv = Base64.decode(parts[0], Base64.NO_WRAP)
      val encrypted = Base64.decode(parts[1], Base64.NO_WRAP)
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
      cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()
  }

  private companion object {
    const val PREFERENCES_NAME = "smb_library_credentials"
    const val KEY_ALIAS = "yomitori.smb.library.credentials.v1"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
  }
}
