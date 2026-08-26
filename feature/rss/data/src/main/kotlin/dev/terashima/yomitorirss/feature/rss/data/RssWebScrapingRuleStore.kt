package dev.terashima.yomitorirss.feature.rss.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.rss.DEFAULT_RSS_WEB_SCRAPING_TIMEOUT_SECONDS
import dev.terashima.yomitorirss.feature.rss.MAX_RSS_WEB_SCRAPING_TIMEOUT_SECONDS
import dev.terashima.yomitorirss.feature.rss.MIN_RSS_WEB_SCRAPING_TIMEOUT_SECONDS
import dev.terashima.yomitorirss.feature.rss.RssWebScrapingRule
import java.net.URI
import java.util.UUID

internal class RssWebScrapingRuleStore(
  private val database: DatabaseConnection,
) {
  fun list(): List<RssWebScrapingRule> {
    ensureRssWebScrapingRuleSchema(database.writable)
    return database.readable.rawQuery(
      "SELECT id, url_pattern, function_code, timeout_seconds, updated_at " +
        "FROM rss_web_scraping_rules ORDER BY updated_at DESC, id",
      emptyArray<String>(),
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) add(cursor.rssWebScrapingRule())
      }
    }
  }

  fun save(
    id: String?,
    urlPattern: String,
    functionCode: String,
    timeoutSeconds: Int,
  ): RssWebScrapingRule {
    ensureRssWebScrapingRuleSchema(database.writable)
    val normalizedPattern = urlPattern.trim()
    val normalizedFunction = functionCode.trim()
    validateRssWebScrapingRule(normalizedPattern, normalizedFunction, timeoutSeconds)
    val existingId = id?.trim()?.takeIf(String::isNotEmpty)
    val ruleId = existingId ?: UUID.randomUUID().toString()
    val updatedAt = System.currentTimeMillis()

    database.transaction {
      rawQuery(
        "SELECT id FROM rss_web_scraping_rules WHERE url_pattern = ? AND id <> ? LIMIT 1",
        arrayOf(normalizedPattern, ruleId),
      ).use { cursor ->
        require(!cursor.moveToFirst()) { "同じ URL パターンの取得ルールが登録されています" }
      }

      val values = ContentValues().apply {
        put("id", ruleId)
        put("url_pattern", normalizedPattern)
        put("function_code", normalizedFunction)
        put("timeout_seconds", timeoutSeconds)
        put("updated_at", updatedAt)
      }
      if (existingId == null) {
        insertOrThrow("rss_web_scraping_rules", null, values)
      } else {
        val changed = update("rss_web_scraping_rules", values, "id = ?", arrayOf(ruleId))
        require(changed == 1) { "取得ルールが見つかりません" }
      }
    }

    return RssWebScrapingRule(
      id = ruleId,
      urlPattern = normalizedPattern,
      functionCode = normalizedFunction,
      timeoutSeconds = timeoutSeconds,
      updatedAt = updatedAt,
    )
  }

  fun delete(id: String) {
    ensureRssWebScrapingRuleSchema(database.writable)
    database.writable.delete("rss_web_scraping_rules", "id = ?", arrayOf(id))
  }
}

internal fun ensureRssWebScrapingRuleSchema(db: SQLiteDatabase) {
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS rss_web_scraping_rules(
        id TEXT PRIMARY KEY NOT NULL,
        url_pattern TEXT NOT NULL UNIQUE,
        function_code TEXT NOT NULL,
        timeout_seconds INTEGER NOT NULL DEFAULT $DEFAULT_RSS_WEB_SCRAPING_TIMEOUT_SECONDS,
        updated_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
}

internal fun validateRssWebScrapingRule(
  urlPattern: String,
  functionCode: String,
  timeoutSeconds: Int = DEFAULT_RSS_WEB_SCRAPING_TIMEOUT_SECONDS,
) {
  require(urlPattern.isNotBlank()) { "URL パターンを入力してください" }
  require(urlPattern.length <= MAX_URL_PATTERN_LENGTH) { "URL パターンが長すぎます" }
  require(urlPattern.startsWith("https://", ignoreCase = true)) {
    "URL パターンは https:// から始めてください"
  }
  require(functionCode.isNotBlank()) { "関数コードを入力してください" }
  require(functionCode.length <= MAX_FUNCTION_CODE_LENGTH) { "関数コードが長すぎます" }
  require(timeoutSeconds in MIN_RSS_WEB_SCRAPING_TIMEOUT_SECONDS..MAX_RSS_WEB_SCRAPING_TIMEOUT_SECONDS) {
    "タイムアウトは $MIN_RSS_WEB_SCRAPING_TIMEOUT_SECONDS〜$MAX_RSS_WEB_SCRAPING_TIMEOUT_SECONDS 秒で指定してください"
  }
}

internal fun normalizeRssWebScrapingUrl(url: String): String {
  val trimmed = url.trim()
  val candidate = when {
    trimmed.startsWith("https://", ignoreCase = true) -> trimmed
    trimmed.startsWith("http://", ignoreCase = true) -> "https://${trimmed.substring(7)}"
    else -> "https://$trimmed"
  }
  val uri = URI(candidate).normalize()
  require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
    "Web スクレイピングは HTTPS URL のみ対応しています"
  }
  require(uri.port == -1 || uri.port == 443) {
    "Web スクレイピングは HTTPS の標準ポートのみ対応しています"
  }
  return uri.toString()
}

internal fun rssWebScrapingUrlPatternMatches(pattern: String, url: String): Boolean {
  val normalizedUrl = runCatching { normalizeRssWebScrapingUrl(url) }.getOrNull() ?: return false
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

internal fun findMatchingRssWebScrapingRule(
  rules: List<RssWebScrapingRule>,
  url: String,
): RssWebScrapingRule? = rules
  .asSequence()
  .filter { rssWebScrapingUrlPatternMatches(it.urlPattern, url) }
  .maxWithOrNull(
    compareBy<RssWebScrapingRule> { rule ->
      rule.urlPattern.count { it != '*' && it != '?' }
    }.thenBy { it.updatedAt },
  )

private fun Cursor.rssWebScrapingRule(): RssWebScrapingRule = RssWebScrapingRule(
  id = getString(getColumnIndexOrThrow("id")),
  urlPattern = getString(getColumnIndexOrThrow("url_pattern")),
  functionCode = getString(getColumnIndexOrThrow("function_code")),
  timeoutSeconds = getInt(getColumnIndexOrThrow("timeout_seconds")),
  updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
)

private const val MAX_URL_PATTERN_LENGTH = 2_048
private const val MAX_FUNCTION_CODE_LENGTH = 64 * 1_024
