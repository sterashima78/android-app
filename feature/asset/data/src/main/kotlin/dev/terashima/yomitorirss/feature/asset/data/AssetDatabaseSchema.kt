package dev.terashima.yomitorirss.feature.asset.data

import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution

val assetDatabaseSchema = DatabaseSchemaContribution(
  owner = "asset",
  createSchema = { db ->
    db.execSQL(
      """
        CREATE TABLE IF NOT EXISTS asset_entries(
          id INTEGER PRIMARY KEY AUTOINCREMENT,
          snapshot_date TEXT NOT NULL,
          name TEXT NOT NULL,
          amount INTEGER NOT NULL,
          account TEXT NOT NULL DEFAULT '',
          source TEXT NOT NULL
        )
      """.trimIndent(),
    )
    db.execSQL(
      """
        CREATE TABLE IF NOT EXISTS asset_categories(
          asset_name TEXT PRIMARY KEY NOT NULL,
          category TEXT NOT NULL
        )
      """.trimIndent(),
    )
    createCategoryDefinitions(db)
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_asset_entries_date ON asset_entries(snapshot_date)")
    db.execSQL("CREATE INDEX IF NOT EXISTS idx_asset_entries_name ON asset_entries(name)")
  },
)

private fun createCategoryDefinitions(db: android.database.sqlite.SQLiteDatabase) {
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS asset_category_definitions(
        category TEXT PRIMARY KEY NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    "INSERT OR IGNORE INTO asset_category_definitions(category) VALUES(?)",
    arrayOf(DEFAULT_CATEGORY),
  )
  db.execSQL(
    """
      INSERT OR IGNORE INTO asset_category_definitions(category)
      SELECT DISTINCT TRIM(category)
      FROM asset_categories
      WHERE TRIM(category) <> ''
    """.trimIndent(),
  )
}

private const val DEFAULT_CATEGORY = "その他"
