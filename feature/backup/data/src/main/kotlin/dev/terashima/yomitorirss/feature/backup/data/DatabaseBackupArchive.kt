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
  private val backupPreferences = BackupPreferences(appContext)

  fun writeTo(output: OutputStream) {
    withTempFile("backup-snapshot", ".db") { snapshot ->
      database.createSnapshot(snapshot)
      database.markSnapshot(snapshot)
      val databaseSha256 = snapshot.sha256()
      val preferencesBytes = backupPreferences.encode()
      val manifest = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", Instant.now().toString())
        .put("databaseName", BACKUP_DATABASE_NAME)
        .put("schemaVersion", database.schemaVersion)
        .put("databaseBytes", snapshot.length())
        .put("databaseSha256", databaseSha256)
        .put("preferencesBytes", preferencesBytes.size)
        .put("preferencesSha256", preferencesBytes.sha256())

      ZipOutputStream(BufferedOutputStream(output)).use { zip ->
        zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
        zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        zip.putNextEntry(ZipEntry(DATABASE_ENTRY))
        FileInputStream(snapshot).use { it.copyTo(zip) }
        zip.closeEntry()

        zip.putNextEntry(ZipEntry(PREFERENCES_ENTRY))
        zip.write(preferencesBytes)
        zip.closeEntry()
      }
    }
  }

  fun validate(input: InputStream) {
    extract(input) { manifest, snapshot, preferencesBytes ->
      validateManifestAndSnapshot(manifest, snapshot, preferencesBytes)
    }
  }

  fun restore(input: InputStream) {
    extract(input) { manifest, snapshot, preferencesBytes ->
      validateManifestAndSnapshot(manifest, snapshot, preferencesBytes)
      val currentPreferences = backupPreferences.encode()
      try {
        backupPreferences.restore(preferencesBytes)
        database.replaceWithSnapshot(snapshot)
      } catch (error: Throwable) {
        runCatching { backupPreferences.restore(currentPreferences) }
        throw error
      }
    }
  }

  private fun validateManifestAndSnapshot(
    manifest: JSONObject,
    snapshot: File,
    preferencesBytes: ByteArray,
  ) {
    require(manifest.optString("format") == FORMAT && manifest.optInt("version") == VERSION) {
      "対応していないMosaicバックアップです"
    }
    require(manifest.optString("databaseName") == BACKUP_DATABASE_NAME) {
      "バックアップDB名が一致しません"
    }
    val schemaVersion = manifest.optInt("schemaVersion", -1)
    require(schemaVersion in MIN_SUPPORTED_SCHEMA_VERSION..database.schemaVersion) {
      "対応していないDB schema versionです (backup=$schemaVersion, app=${database.schemaVersion})"
    }
    val declaredBytes = manifest.optLong("databaseBytes", -1L)
    require(declaredBytes == snapshot.length()) { "バックアップDBのサイズが一致しません" }
    val declaredSha256 = manifest.optString("databaseSha256")
    require(declaredSha256.length == SHA256_HEX_LENGTH && declaredSha256 == snapshot.sha256()) {
      "バックアップDBのチェックサムが一致しません"
    }
    val declaredPreferencesBytes = manifest.optLong("preferencesBytes", -1L)
    require(declaredPreferencesBytes == preferencesBytes.size.toLong()) {
      "設定バックアップのサイズが一致しません"
    }
    val declaredPreferencesSha256 = manifest.optString("preferencesSha256")
    require(
      declaredPreferencesSha256.length == SHA256_HEX_LENGTH &&
        declaredPreferencesSha256 == preferencesBytes.sha256(),
    ) {
      "設定バックアップのチェックサムが一致しません"
    }
    backupPreferences.validate(preferencesBytes)
    val actualSchemaVersion = database.validateSnapshot(snapshot)
    require(actualSchemaVersion == schemaVersion) { "バックアップDBのschema versionが一致しません" }
  }

  private fun extract(
    input: InputStream,
    block: (manifest: JSONObject, snapshot: File, preferencesBytes: ByteArray) -> Unit,
  ) {
    withTempFile("backup-restore", ".db") { snapshot ->
      var manifest: JSONObject? = null
      var databaseFound = false
      var preferencesBytes: ByteArray? = null
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
            PREFERENCES_ENTRY -> {
              require(preferencesBytes == null) { "preferences entryが重複しています" }
              preferencesBytes = zip.readLimited(MAX_PREFERENCES_BYTES)
            }
            else -> require(false) { "未知のbackup entryです: ${entry.name}" }
          }
          zip.closeEntry()
        }
      }
      val parsedManifest = requireNotNull(manifest) { "manifestがありません" }
      require(databaseFound && snapshot.isFile) { "database entryがありません" }
      val parsedPreferences = requireNotNull(preferencesBytes) { "preferences entryがありません" }
      block(parsedManifest, snapshot, parsedPreferences)
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
    private const val FORMAT = "mosaic-database-backup"
    private const val VERSION = 1
    private const val BACKUP_DATABASE_NAME = "mosaic.db"
    private const val MIN_SUPPORTED_SCHEMA_VERSION = 23
    private const val MANIFEST_ENTRY = "manifest.json"
    private const val DATABASE_ENTRY = "database/mosaic.db"
    private const val PREFERENCES_ENTRY = "preferences/user-preferences.json"
    private const val SHA256_HEX_LENGTH = 64
    private const val MAX_MANIFEST_BYTES = 64 * 1024L
    private const val MAX_PREFERENCES_BYTES = 16 * 1024 * 1024L
    private const val MAX_DATABASE_BYTES = 4L * 1024L * 1024L * 1024L
  }
}

private fun File.sha256(): String = FileInputStream(this).use { it.sha256() }

private fun ByteArray.sha256(): String = inputStream().use { it.sha256() }

private fun InputStream.sha256(): String {
  val digest = MessageDigest.getInstance("SHA-256")
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
  while (true) {
    val read = read(buffer)
    if (read < 0) break
    if (read > 0) digest.update(buffer, 0, read)
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
