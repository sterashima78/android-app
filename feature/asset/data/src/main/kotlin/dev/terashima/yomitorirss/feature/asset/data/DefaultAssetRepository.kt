package dev.terashima.yomitorirss.feature.asset.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.asset.AssetCategorySetting
import dev.terashima.yomitorirss.feature.asset.AssetHistoryPoint
import dev.terashima.yomitorirss.feature.asset.AssetImportResult
import dev.terashima.yomitorirss.feature.asset.AssetOverview
import dev.terashima.yomitorirss.feature.asset.AssetRepository
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.json.JSONObject

class DefaultAssetRepository(
  context: Context,
  private val database: DatabaseConnection,
) : AssetRepository {
  private val appContext = context.applicationContext

  override suspend fun loadOverview(): AssetOverview {
    val historyByDate = linkedMapOf<LocalDate, MutableMap<String, Long>>()
    database.readable.rawQuery(
      """
        SELECT e.snapshot_date, COALESCE(c.category, ?), SUM(e.amount)
        FROM asset_entries e
        LEFT JOIN asset_categories c ON c.asset_name = e.name
        GROUP BY e.snapshot_date, COALESCE(c.category, ?)
        ORDER BY e.snapshot_date ASC
      """.trimIndent(),
      arrayOf(DEFAULT_CATEGORY, DEFAULT_CATEGORY),
    ).use { cursor ->
      while (cursor.moveToNext()) {
        val date = LocalDate.parse(cursor.getString(0))
        val category = cursor.getString(1)
        val amount = cursor.getLong(2)
        historyByDate.getOrPut(date) { linkedMapOf() }[category] = amount
      }
    }

    val history = historyByDate.map { (date, categories) ->
      AssetHistoryPoint(date, categories.values.sum(), categories.toMap())
    }
    val latest = history.lastOrNull()
    val settings = mutableListOf<AssetCategorySetting>()
    database.readable.rawQuery(
      """
        SELECT DISTINCT e.name, COALESCE(c.category, ?)
        FROM asset_entries e
        LEFT JOIN asset_categories c ON c.asset_name = e.name
        ORDER BY e.name COLLATE NOCASE
      """.trimIndent(),
      arrayOf(DEFAULT_CATEGORY),
    ).use { cursor ->
      while (cursor.moveToNext()) settings += AssetCategorySetting(cursor.getString(0), cursor.getString(1))
    }

    val persistedCategories = mutableListOf<String>()
    database.readable.rawQuery(
      "SELECT category FROM asset_category_definitions",
      null,
    ).use { cursor ->
      while (cursor.moveToNext()) persistedCategories += cursor.getString(0)
    }
    val registeredCategories = (
      persistedCategories + settings.map(AssetCategorySetting::category) + DEFAULT_CATEGORY
      )
      .map(String::trim)
      .filter(String::isNotBlank)
      .distinct()
      .sortedWith(String.CASE_INSENSITIVE_ORDER)
      .let { categories -> listOf(DEFAULT_CATEGORY) + categories.filterNot { it == DEFAULT_CATEGORY } }

    return AssetOverview(
      latestDate = latest?.date,
      total = latest?.total ?: 0L,
      latestByCategory = latest?.byCategory.orEmpty(),
      history = history,
      categorySettings = settings,
      registeredCategories = registeredCategories,
    )
  }

  override suspend fun importTsv(documentUri: String): AssetImportResult {
    val uri = Uri.parse(documentUri)
    val rows = appContext.contentResolver.openInputStream(uri)?.use { input ->
      BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use(::parseTsv)
    } ?: error("ファイルを開けませんでした")
    require(rows.isNotEmpty()) { "インポートできる資産データがありません" }
    replaceSnapshots(rows, SOURCE_FILE)
    return AssetImportResult(rows.size, rows.map { it.date }.distinct().size)
  }

  override suspend fun importMoneyForwardJson(json: String): AssetImportResult {
    val root = JSONObject(json)
    require(root.optString("format") == MONEY_FORWARD_FORMAT) { "MoneyForward データ形式が一致しません" }
    require(root.optInt("version") == 1) { "未対応の MoneyForward データ形式です" }
    val date = LocalDate.parse(root.getString("date"))
    val entries = root.getJSONArray("entries")
    val rows = buildList {
      for (index in 0 until entries.length()) {
        val item = entries.getJSONObject(index)
        val baseName = item.getString("name").trim()
        val account = item.optString("account").trim()
        require(baseName.isNotBlank()) { "資産名が空です" }
        add(
          ParsedAssetRow(
            date = date,
            name = buildAssetRecordName(baseName, account),
            amount = item.getLong("amount"),
            account = account,
          ),
        )
      }
    }
    require(rows.isNotEmpty()) { "MoneyForward から資産データを取得できませんでした" }
    replaceSnapshots(rows, SOURCE_MONEY_FORWARD)
    return AssetImportResult(rows.size, 1)
  }

  override suspend fun addCategory(category: String) {
    val value = category.trim()
    require(value.isNotBlank()) { "カテゴリ名を入力してください" }
    val inserted = database.writable.insertWithOnConflict(
      "asset_category_definitions",
      null,
      ContentValues().apply { put("category", value) },
      SQLiteDatabase.CONFLICT_IGNORE,
    )
    require(inserted != -1L) { "同じカテゴリが既に登録されています" }
  }

  override suspend fun setCategory(assetName: String, category: String) {
    val name = assetName.trim()
    val value = category.trim()
    require(name.isNotBlank())
    require(value.isNotBlank()) { "カテゴリ名を選択してください" }
    database.transaction {
      insertWithOnConflict(
        "asset_category_definitions",
        null,
        ContentValues().apply { put("category", value) },
        SQLiteDatabase.CONFLICT_IGNORE,
      )
      insertWithOnConflict(
        "asset_categories",
        null,
        ContentValues().apply {
          put("asset_name", name)
          put("category", value)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
  }

  private fun replaceSnapshots(rows: List<ParsedAssetRow>, source: String) {
    database.transaction {
      rows.groupBy { it.date }.forEach { (date, dateRows) ->
        delete("asset_entries", "snapshot_date=?", arrayOf(date.toString()))
        dateRows.forEach { row ->
          insertOrThrow(
            "asset_entries",
            null,
            ContentValues().apply {
              put("snapshot_date", row.date.toString())
              put("name", row.name)
              put("amount", row.amount)
              put("account", row.account)
              put("source", source)
            },
          )
        }
      }
    }
  }
}

internal data class ParsedAssetRow(
  val date: LocalDate,
  val name: String,
  val amount: Long,
  val account: String,
)

internal fun parseTsv(reader: BufferedReader): List<ParsedAssetRow> {
  val result = mutableListOf<ParsedAssetRow>()
  reader.forEachLine { rawLine ->
    val line = rawLine.removePrefix("\uFEFF")
    if (line.isBlank()) return@forEachLine
    val columns = line.split('\t')
    if (columns.size < 3) error("TSV は3列以上の日付・資産名・金額が必要です")
    val date = parseDate(columns[0])
    if (date == null && columns[0].trim().lowercase() in setOf("日付", "date")) return@forEachLine
    requireNotNull(date) { "日付を解析できません: ${columns[0]}" }
    val baseName = columns[1].trim()
    require(baseName.isNotBlank()) { "資産名が空です" }
    val amount = parseAmount(columns[2]) ?: error("金額を解析できません: ${columns[2]}")
    val account = columns.getOrNull(3)?.trim().orEmpty()
    result += ParsedAssetRow(
      date = date,
      name = buildAssetRecordName(baseName, account),
      amount = amount,
      account = account,
    )
  }
  return result
}

internal fun parseAmount(value: String): Long? =
  value.replace(Regex("[,，円¥￥\\s]"), "").toLongOrNull()

internal fun buildAssetRecordName(name: String, account: String): String {
  val normalizedName = name.trim()
  val normalizedAccount = account.trim()
  return if (normalizedAccount.isBlank()) normalizedName else "$normalizedName / $normalizedAccount"
}

internal fun parseDate(value: String): LocalDate? {
  val text = value.trim()
  val formatters = listOf(
    DateTimeFormatter.ISO_LOCAL_DATE,
    DateTimeFormatter.ofPattern("yyyy/M/d"),
    DateTimeFormatter.ofPattern("yyyy/MM/dd"),
  )
  return formatters.firstNotNullOfOrNull { formatter ->
    runCatching { LocalDate.parse(text, formatter) }.getOrNull()
  }
}

private const val DEFAULT_CATEGORY = "その他"
private const val SOURCE_FILE = "file"
private const val SOURCE_MONEY_FORWARD = "moneyforward"
private const val MONEY_FORWARD_FORMAT = "moneyforward-asset-snapshot"
