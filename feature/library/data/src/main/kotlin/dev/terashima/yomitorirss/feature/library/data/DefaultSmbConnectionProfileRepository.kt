package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfile
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfileRepository
import dev.terashima.yomitorirss.feature.library.SmbLibraryLocation
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class DefaultSmbConnectionProfileRepository(
  context: Context,
  private val database: DatabaseConnection,
) : SmbConnectionProfileRepository {
  private val credentialStore = SharedSmbCredentialStore(context.applicationContext)

  override suspend fun connectionProfiles(): List<SmbConnectionProfile> {
    ensureSchema()
    return queryProfiles()
  }

  override suspend fun saveConnectionProfile(
    profile: SmbConnectionProfile,
    password: String?,
  ): SmbConnectionProfile {
    ensureSchema()
    val normalized = profile.copy(
      id = profile.id.ifBlank { UUID.randomUUID().toString() },
      name = profile.name.trim(),
      host = profile.host.trim(),
      username = profile.username.trim(),
      domain = profile.domain.trim(),
    )
    validateProfile(normalized)
    if (!credentialStore.has(normalized.id)) {
      require(!password.isNullOrEmpty()) { "SMBパスワードを入力してください" }
    }
    if (!password.isNullOrEmpty()) credentialStore.save(normalized.id, password)

    val now = System.currentTimeMillis()
    database.transaction {
      insertWithOnConflict(
        PROFILE_TABLE,
        null,
        normalized.toValues(now),
        SQLiteDatabase.CONFLICT_REPLACE,
      )
      update(
        LIBRARY_SERVER_TABLE,
        ContentValues().apply {
          put("name", normalized.name)
          put("host", normalized.host)
          put("port", normalized.port)
          put("username", normalized.username)
          put("domain_name", normalized.domain)
          put("updated_at", now)
        },
        "id = ?",
        arrayOf(normalized.id),
      )
    }
    return normalized.copy(credentialConfigured = true)
  }

  override suspend fun deleteConnectionProfile(profileId: String) {
    ensureSchema()
    database.transaction {
      delete(LIBRARY_SERVER_TABLE, "id = ?", arrayOf(profileId))
      delete(PROFILE_TABLE, "id = ?", arrayOf(profileId))
    }
    credentialStore.delete(profileId)
  }

  override suspend fun libraryLocations(): List<SmbLibraryLocation> {
    ensureSchema()
    return database.readable.rawQuery(
      "SELECT id, share_name, root_path FROM $LIBRARY_SERVER_TABLE ORDER BY name COLLATE NOCASE, id",
      null,
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            SmbLibraryLocation(
              serverId = cursor.getString(0),
              share = cursor.getString(1),
              rootPath = cursor.getString(2),
            ),
          )
        }
      }
    }
  }

  override suspend fun saveLibraryLocation(location: SmbLibraryLocation): SmbLibraryLocation {
    ensureSchema()
    val normalized = location.copy(
      share = location.share.trim().trim('/', '\\'),
      rootPath = normalizeSmbPath(location.rootPath),
    )
    require(normalized.serverId.isNotBlank()) { "SMB接続設定を選択してください" }
    require(normalized.share.isNotBlank()) { "SMB共有名を入力してください" }
    val profile = queryProfiles().firstOrNull { it.id == normalized.serverId }
      ?: error("SMB接続設定がありません")
    require(credentialStore.has(profile.id)) { "${profile.name} のSMB認証情報がありません" }
    val now = System.currentTimeMillis()
    database.write {
      insertWithOnConflict(
        LIBRARY_SERVER_TABLE,
        null,
        ContentValues().apply {
          put("id", profile.id)
          put("name", profile.name)
          put("host", profile.host)
          put("port", profile.port)
          put("share_name", normalized.share)
          put("root_path", normalized.rootPath)
          put("username", profile.username)
          put("domain_name", profile.domain)
          put("updated_at", now)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
    return normalized
  }

  override suspend fun deleteLibraryLocation(serverId: String) {
    ensureSchema()
    database.write {
      delete(LIBRARY_SERVER_TABLE, "id = ?", arrayOf(serverId))
    }
  }

  private fun queryProfiles(): List<SmbConnectionProfile> = database.readable.rawQuery(
    """
      SELECT id, name, host, port, username, domain_name
      FROM $PROFILE_TABLE
      ORDER BY name COLLATE NOCASE, id
    """.trimIndent(),
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        val id = cursor.getString(0)
        add(
          SmbConnectionProfile(
            id = id,
            name = cursor.getString(1),
            host = cursor.getString(2),
            port = cursor.getInt(3),
            username = cursor.getString(4),
            domain = cursor.getString(5),
            credentialConfigured = credentialStore.has(id),
          ),
        )
      }
    }
  }

  private fun ensureSchema() {
    ensureLibrarySchema(database.writable)
  }

  private companion object {
    const val PROFILE_TABLE = "smb_connection_profiles"
    const val LIBRARY_SERVER_TABLE = "smb_library_servers"
  }
}

private fun SmbConnectionProfile.toValues(updatedAt: Long): ContentValues = ContentValues().apply {
  put("id", id)
  put("name", name)
  put("host", host)
  put("port", port)
  put("username", username)
  put("domain_name", domain)
  put("updated_at", updatedAt)
}

private fun validateProfile(profile: SmbConnectionProfile) {
  require(profile.name.isNotBlank()) { "SMBサーバ名を入力してください" }
  require(profile.host.isNotBlank()) { "SMBホストを入力してください" }
  require(profile.port in 1..65535) { "SMBポートが不正です" }
  require(profile.username.isNotBlank()) { "SMBユーザー名を入力してください" }
}

private fun normalizeSmbPath(path: String): String {
  val segments = path
    .replace('/', '\\')
    .split('\\')
    .filter { it.isNotBlank() && it != "." }
  require(".." !in segments) { "SMBパスに .. は使用できません" }
  return segments.joinToString("\\")
}

private class SharedSmbCredentialStore(context: Context) {
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
