package dev.terashima.yomitorirss.core.aicloudopenai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val KEY_ALIAS = "mosaic_chatgpt_oauth_v1"
internal const val CHATGPT_OAUTH_PREFERENCES = "chatgpt_oauth_secure"
private const val PREF_IV = "iv"
private const val PREF_CIPHERTEXT = "ciphertext"

internal class AndroidChatGptCredentialStore(
  context: Context,
) : ChatGptCredentialStore {
  private val preferences = context.getSharedPreferences(CHATGPT_OAUTH_PREFERENCES, Context.MODE_PRIVATE)
  private val json = Json { ignoreUnknownKeys = true }

  override fun read(): ChatGptCredentials? {
    val iv = preferences.getString(PREF_IV, null) ?: return null
    val ciphertext = preferences.getString(PREF_CIPHERTEXT, null) ?: return null
    return runCatching {
      val cipher = Cipher.getInstance("AES/GCM/NoPadding")
      cipher.init(
        Cipher.DECRYPT_MODE,
        getOrCreateKey(),
        GCMParameterSpec(128, Base64.getDecoder().decode(iv)),
      )
      val plaintext = cipher.doFinal(Base64.getDecoder().decode(ciphertext))
      val body = json.parseToJsonElement(plaintext.toString(StandardCharsets.UTF_8)) as JsonObject
      ChatGptCredentials(
        accessToken = body.requiredString("access_token"),
        refreshToken = body.requiredString("refresh_token"),
        expiresAtEpochMillis = body.requiredString("expires_at").toLong(),
        accountId = body.requiredString("account_id"),
      )
    }.getOrNull()
  }

  override fun write(credentials: ChatGptCredentials) {
    val plaintext = buildJsonObject {
      put("access_token", JsonPrimitive(credentials.accessToken))
      put("refresh_token", JsonPrimitive(credentials.refreshToken))
      put("expires_at", JsonPrimitive(credentials.expiresAtEpochMillis.toString()))
      put("account_id", JsonPrimitive(credentials.accountId))
    }.toString().toByteArray(StandardCharsets.UTF_8)
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val ciphertext = cipher.doFinal(plaintext)
    preferences.edit()
      .putString(PREF_IV, Base64.getEncoder().encodeToString(cipher.iv))
      .putString(PREF_CIPHERTEXT, Base64.getEncoder().encodeToString(ciphertext))
      .apply()
  }

  override fun clear() {
    preferences.edit().clear().apply()
  }

  private fun getOrCreateKey(): SecretKey {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).apply {
      init(
        KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .build(),
      )
    }.generateKey()
  }
}

private fun JsonObject.requiredString(key: String): String =
  (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
    ?: error("Encrypted ChatGPT credential payload is missing $key")
