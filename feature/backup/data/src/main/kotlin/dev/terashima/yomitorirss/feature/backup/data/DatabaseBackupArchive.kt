package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject

internal class DatabaseBackupArchive(
  context: Context,
  private val database: YomitoriDatabase,
) {
  private val appContext = context.applicationContext

  fun writeTo(output: OutputStream) {
    withTempFile("backup-snapshot", ".db") { snapshot ->
      database.createSnapshot(snapshot)
      database.markSnapshot(snapshot)
      val sha256 = snapshot.sha256()
      val manifest = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", Instant.now().toString())
        .put("databaseName", YomitoriDatabase.DB_NAME)
        .put("schemaVersion", database.schemaVersion)
        .put("databaseBytes", snapshot.length())
        .put("databaseSha256", sha256)

      ZipOutputStream(BufferedOutputStream(output)).use { zip ->
        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
        zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
        FileInputStream(snapshot).use { it.copyTo(zip) }
        zip.closeEntry()
      }
    }
  }

  fun validate(input: InputStream) {
    extract(input) { manifest, snapshot ->
      validateManifestAndSnapshot(manifest, snapshot)
    }
  }

  fun restore(input: InputStream) {
    extract(input) { manifest, snapshot ->
      validateManifestAndSnapshot(manifest, snapshot)
      database.replaceWithSnapshot(snapshot)
    }
  }

  private fun validateManifestAndSnapshot(manifest: JSONObject, snapshot: File) {
    require(manifest.optString("format") == FORMAT && manifest.optInt("version") == VERSION) {
      "対応していないバックアップです"
    }
    require(manifest.optString("databaseName") == YomitoriDatabase.DB_NAME) {
      "バックアップDB名が一致しません"
    }
    val schemaVersion = manifest.optInt("schemaVersion", -1)
    require(schemaVersion in 1..database.schemaVersion) {
      "このアプリより新しいバックアップです"
    }
    val declaredBytes = manifest.optLong("databaseBytes", -1L)
    require(declaredBytes == snapshot.length()) { "バックアップDBのサイズが一致しません" }
    val declaredSha256 = manifest.optString("databaseSha256")
    require(declaredSha256.length == SHA256_HEX_LENGTH && declaredSha256 == snapshot.sha256()) {
      "バックアップDBのチェックサムが一致しません"
    }
    val actualSchemaVersion = database.validateSnapshot(snapshot)
    require(actualSchemaVersion == schemaVersion) { "バックアップDBのschema versionが一致しません" }
  }

  private fun extract(
    input: InputStream,
    block: (manifest: JSONObject, snapshot: File) -> Unit,
  ) {
    withTempFile("backup-restore", ".db") { snapshot ->
      var manifest: JSONObject? = null
      var databaseFound = false
      ZipInputStream(BufferedInputStream(input)).use { zip ->
        while (true) {
          val entry = zip.nextEntry ?: break
          require(!entry.isDirectory) { "バックアップ内に不正なdirectory entryがあります" }
          when (entry.name) {
            MANIFEST_ENTRY -> {
              require(manifest == null) { "manifestが重複しています" }
              val bytes = zip.readLimited(MAX_MANIFEST_BYTES)
              manifest = JSONObject(bytes.toString(Charsets.UTF_8))
            }
            DATABASE_ENTRY -> {
              require(!databaseFound) { "database entryが重複しています" }
              databaseFound = true
              FileOutputStream(snapshot, false).use { output ->
                zip.copyLimited(output, MAX_DATABASE_BYTES)
                output.fd.sync()
              }
            }
            else -> require(false) { "未知のbackup entryです: ${entry.name}" }
          }
          zip.closeEntry()
        }
      }
      val parsedManifest = requireNotNull(manifest) { "manifestがありません" }
      require(databaseFound && snapshot.isFile) { "database entryがありません" }
      block(parsedManifest, snapshot)
    }
  }

  private fun withTempFile(prefix: String, suffix: String, block: (File) -> Unit) {
    val directory = File(appContext.cacheDir, "backup").apply { mkdirs() }
    val file = File.createTempFile(prefix, suffix, directory)
    try {
      block(file)
    } finally {
      file.delete()
    }
  }

  companion object {
    const val MIME_TYPE = "application/zip"
    const val MANUAL_FILE_EXTENSION = ".zip"
    private const val FORMAT = "yomitori-rss-database-backup"
    private const val VERSION = 1
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val DATABASE_ENTRY = "database/yomitori-rss.db"
    private const val SHA256_HEX_LENGTH = 64
    private const val MAX_MANIFEST_BYTES = 64 * 1024L
    private const val MAX_DATABASE_BYTES = 4L * 1024L * 1024L * 1024L

    fun looksLikeArchive(file: File): Boolean {
      if (!file.isFile || file.length() < ZIP_SIGNATURE.size) return false
      FileInputStream(file).use { input ->
        val bytes = ByteArray(ZIP_SIGNATURE.size)
        return input.read(bytes) == bytes.size && bytes.contentEquals(ZIP_SIGNATURE)
      }
    }

    private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
  }
}

private fun File.sha256(): String {
  val digest = MessageDigest.getInstance("SHA-256")
  FileInputStream(this).use { input ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val read = input.read(buffer)
      if (read < 0) break
      if (read > 0) digest.update(buffer, 0, read)
    }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun InputStream.readLimited(limit: Long): ByteArray {
  val output = java.io.ByteArrayOutputStream()
  copyLimited(output, limit)
  return output.toByteArray()
}

private fun InputStream.copyLimited(output: OutputStream, limit: Long) {
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
  var total = 0L
  while (true) {
    val read = read(buffer)
    if (read < 0) return
    if (read == 0) continue
    total += read
    require(total <= limit) { "バックアップentryが大きすぎます" }
    output.write(buffer, 0, read)
  }
}
