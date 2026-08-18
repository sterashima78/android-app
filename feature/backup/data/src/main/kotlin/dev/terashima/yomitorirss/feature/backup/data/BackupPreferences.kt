package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal class BackupPreferences(context: Context) {
  private val appContext = context.applicationContext

  fun encode(): ByteArray {
    val files = JSONObject()
    BACKED_UP_PREFERENCES.sorted().forEach { name ->
      val preferences = appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
      if (preferences.all.isNotEmpty()) {
        files.put(name, encodeFile(preferences))
      }
    }
    return JSONObject()
      .put("format", FORMAT)
      .put("version", VERSION)
      .put("files", files)
      .toString(2)
      .toByteArray(Charsets.UTF_8)
  }

  fun validate(bytes: ByteArray) {
    decode(bytes)
  }

  fun restore(bytes: ByteArray) {
    val decoded = decode(bytes)
    BACKED_UP_PREFERENCES.forEach { name ->
      val values = decoded[name].orEmpty()
      val editor = appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
      values.forEach { (key, value) -> editor.putValue(key, value) }
      check(editor.commit()) { "設定を復元できませんでした: $name" }
    }
  }

  private fun encodeFile(preferences: SharedPreferences): JSONObject = JSONObject().apply {
    preferences.all.toSortedMap().forEach { (key, value) ->
      put(key, encodeValue(value))
    }
  }

  private fun encodeValue(value: Any?): JSONObject = when (value) {
    is String -> typed("string", value)
    is Int -> typed("int", value.toString())
    is Long -> typed("long", value.toString())
    is Float -> typed("float", value.toString())
    is Boolean -> typed("boolean", value)
    is Set<*> -> {
      require(value.all { it is String }) { "対応していないSharedPreferences setです" }
      typed("stringSet", JSONArray(value.filterIsInstance<String>().sorted()))
    }
    else -> error("対応していないSharedPreferences型です: ${value?.javaClass?.name ?: "null"}")
  }

  private fun typed(type: String, value: Any): JSONObject = JSONObject()
    .put("type", type)
    .put("value", value)

  private fun decode(bytes: ByteArray): Map<String, Map<String, PreferenceValue>> {
    require(bytes.size <= MAX_PREFERENCES_BYTES) { "設定バックアップが大きすぎます" }
    val root = JSONObject(bytes.toString(Charsets.UTF_8))
    require(root.optString("format") == FORMAT && root.optInt("version") == VERSION) {
      "対応していない設定バックアップです"
    }
    val files = root.optJSONObject("files") ?: error("設定バックアップにfilesがありません")
    val unknownFiles = files.keys().asSequence().filterNot(BACKED_UP_PREFERENCES::contains).toList()
    require(unknownFiles.isEmpty()) { "未知の設定ファイルが含まれています: ${unknownFiles.joinToString()}" }

    return BACKED_UP_PREFERENCES.associateWith { name ->
      val file = files.optJSONObject(name) ?: return@associateWith emptyMap()
      file.keys().asSequence().associateWith { key -> decodeValue(file.getJSONObject(key)) }
    }
  }

  private fun decodeValue(json: JSONObject): PreferenceValue {
    val type = json.getString("type")
    return when (type) {
      "string" -> PreferenceValue.StringValue(json.getString("value"))
      "int" -> PreferenceValue.IntValue(json.getString("value").toInt())
      "long" -> PreferenceValue.LongValue(json.getString("value").toLong())
      "float" -> PreferenceValue.FloatValue(json.getString("value").toFloat())
      "boolean" -> PreferenceValue.BooleanValue(json.getBoolean("value"))
      "stringSet" -> {
        val array = json.getJSONArray("value")
        PreferenceValue.StringSetValue(
          buildSet {
            repeat(array.length()) { index -> add(array.getString(index)) }
          },
        )
      }
      else -> error("未知のSharedPreferences型です: $type")
    }
  }

  private fun SharedPreferences.Editor.putValue(key: String, value: PreferenceValue) {
    when (value) {
      is PreferenceValue.StringValue -> putString(key, value.value)
      is PreferenceValue.IntValue -> putInt(key, value.value)
      is PreferenceValue.LongValue -> putLong(key, value.value)
      is PreferenceValue.FloatValue -> putFloat(key, value.value)
      is PreferenceValue.BooleanValue -> putBoolean(key, value.value)
      is PreferenceValue.StringSetValue -> putStringSet(key, value.value)
    }
  }

  private sealed interface PreferenceValue {
    data class StringValue(val value: String) : PreferenceValue
    data class IntValue(val value: Int) : PreferenceValue
    data class LongValue(val value: Long) : PreferenceValue
    data class FloatValue(val value: Float) : PreferenceValue
    data class BooleanValue(val value: Boolean) : PreferenceValue
    data class StringSetValue(val value: Set<String>) : PreferenceValue
  }

  companion object {
    private const val FORMAT = "yomitori-user-preferences"
    private const val VERSION = 1
    private const val MAX_PREFERENCES_BYTES = 16 * 1024 * 1024

    // Explicit allowlist: never add credentials, persisted URI permissions, device-specific
    // benchmarks, transient queue state, or crash diagnostics here.
    internal val BACKED_UP_PREFERENCES = setOf(
      "background_data_fetch",
      "book_reader_position",
      "local_ai_background_execution",
      "local_summary_models",
      "summary_preferences",
      "workout",
      "x_viewer_preferences",
    )
  }
}
