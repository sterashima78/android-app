package dev.terashima.yomitorirss.feature.library.data

import android.content.ContentValues
import android.database.Cursor
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.library.DEFAULT_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS
import dev.terashima.yomitorirss.feature.library.MAX_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS
import dev.terashima.yomitorirss.feature.library.MIN_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractor
import dev.terashima.yomitorirss.feature.library.WebLibraryMetadataExtractorRepository
import java.util.UUID

class DefaultWebLibraryMetadataExtractorRepository(
  private val database: DatabaseConnection,
) : WebLibraryMetadataExtractorRepository {
  override fun list(): List<WebLibraryMetadataExtractor> {
    ensureWebLibraryMetadataExtractorSchema(database.writable)
    return database.readable.rawQuery(
      "SELECT id, url_pattern, function_code, timeout_seconds, updated_at " +
        "FROM web_library_metadata_extractors ORDER BY updated_at DESC, id",
      emptyArray<String>(),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) add(cursor.webLibraryMetadataExtractor())
      }
    }
  }

  override fun save(
    id: String?,
    urlPattern: String,
    functionCode: String,
    timeoutSeconds: Int,
  ): WebLibraryMetadataExtractor {
    ensureWebLibraryMetadataExtractorSchema(database.writable)
    val normalizedPattern = urlPattern.trim()
    val normalizedFunction = functionCode.trim()
    validateWebLibraryMetadataExtractor(normalizedPattern, normalizedFunction, timeoutSeconds)
    val normalizedId = id?.trim()?.takeIf(String::isNotEmpty)
    val extractorId = normalizedId ?: UUID.randomUUID().toString()
    val updatedAt = System.currentTimeMillis()

    database.transaction {
      rawQuery(
        "SELECT id FROM web_library_metadata_extractors WHERE url_pattern = ? AND id <> ? LIMIT 1",
        arrayOf(normalizedPattern, extractorId),
      ).use { cursor ->
        require(!cursor.moveToFirst()) { "同じ URL パターンの取得ルールが登録されています" }
      }

      val values = ContentValues().apply {
        put("id", extractorId)
        put("url_pattern", normalizedPattern)
        put("function_code", normalizedFunction)
        put("timeout_seconds", timeoutSeconds)
        put("updated_at", updatedAt)
      }
      if (normalizedId == null) {
        insertOrThrow("web_library_metadata_extractors", null, values)
      } else {
        val changed = update(
          "web_library_metadata_extractors",
          values,
          "id = ?",
          arrayOf(extractorId),
        )
        require(changed == 1) { "取得ルールが見つかりません" }
      }
    }

    return WebLibraryMetadataExtractor(
      id = extractorId,
      urlPattern = normalizedPattern,
      functionCode = normalizedFunction,
      timeoutSeconds = timeoutSeconds,
      updatedAt = updatedAt,
    )
  }

  override fun delete(id: String) {
    ensureWebLibraryMetadataExtractorSchema(database.writable)
    database.write {
      delete(
        "web_library_metadata_extractors",
        "id = ?",
        arrayOf(id),
      )
    }
  }
}

internal fun ensureWebLibraryMetadataExtractorSchema(db: android.database.sqlite.SQLiteDatabase) {
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS web_library_metadata_extractors(
        id TEXT PRIMARY KEY NOT NULL,
        url_pattern TEXT NOT NULL UNIQUE,
        function_code TEXT NOT NULL,
        timeout_seconds INTEGER NOT NULL DEFAULT $DEFAULT_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS,
        updated_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  val hasTimeoutColumn = db.rawQuery(
    "PRAGMA table_info(web_library_metadata_extractors)",
    null,
  ).use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    var found = false
    while (cursor.moveToNext()) {
      if (cursor.getString(nameIndex) == "timeout_seconds") {
        found = true
        break
      }
    }
    found
  }
  if (!hasTimeoutColumn) {
    db.execSQL(
      "ALTER TABLE web_library_metadata_extractors " +
        "ADD COLUMN timeout_seconds INTEGER NOT NULL DEFAULT $DEFAULT_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS",
    )
  }
}

internal fun validateWebLibraryMetadataExtractor(
  urlPattern: String,
  functionCode: String,
  timeoutSeconds: Int = DEFAULT_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS,
) {
  require(urlPattern.isNotBlank()) { "URL パターンを入力してください" }
  require(urlPattern.length <= MAX_URL_PATTERN_LENGTH) { "URL パターンが長すぎます" }
  require(urlPattern.startsWith("https://", ignoreCase = true)) {
    "URL パターンは https:// から始めてください"
  }
  require(functionCode.isNotBlank()) { "関数コードを入力してください" }
  require(functionCode.length <= MAX_FUNCTION_CODE_LENGTH) { "関数コードが長すぎます" }
  require(timeoutSeconds in MIN_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS..MAX_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS) {
    "タイムアウトは $MIN_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS〜$MAX_WEB_LIBRARY_METADATA_TIMEOUT_SECONDS 秒で指定してください"
  }
}

internal fun webLibraryUrlPatternMatches(pattern: String, url: String): Boolean {
  val normalizedUrl = runCatching { normalizeWebUrl(url) }.getOrNull() ?: return false
  val trimmedPattern = pattern.trim()
  if (!trimmedPattern.startsWith("https://", ignoreCase = true)) return false
  val regex = buildString(trimmedPattern.length * 2) {
    append('^')
    trimmedPattern.forEach { character ->
      when (character) {
        '*' -> append(".*")
        '?' -> append('.')
        '\\', '.', '^', '$', '|', '(', ')', '[', ']', '{', '}', '+' -> {
          append('\\')
          append(character)
        }
        else -> append(character)
      }
    }
    append('$')
  }
  return Regex(regex, RegexOption.IGNORE_CASE).matches(normalizedUrl)
}

internal fun findMatchingWebLibraryMetadataExtractor(
  extractors: List<WebLibraryMetadataExtractor>,
  url: String,
): WebLibraryMetadataExtractor? = extractors
  .asSequence()
  .filter { extractor -> webLibraryUrlPatternMatches(extractor.urlPattern, url) }
  .maxWithOrNull(
    compareBy<WebLibraryMetadataExtractor> { extractor ->
      extractor.urlPattern.count { it != '*' && it != '?' }
    }.thenBy { it.updatedAt },
  )

private fun Cursor.webLibraryMetadataExtractor(): WebLibraryMetadataExtractor = WebLibraryMetadataExtractor(
  id = getString(getColumnIndexOrThrow("id")),
  urlPattern = getString(getColumnIndexOrThrow("url_pattern")),
  functionCode = getString(getColumnIndexOrThrow("function_code")),
  timeoutSeconds = getInt(getColumnIndexOrThrow("timeout_seconds")),
  updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
)

private const val MAX_URL_PATTERN_LENGTH = 2_048
private const val MAX_FUNCTION_CODE_LENGTH = 32_768
