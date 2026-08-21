package dev.terashima.yomitorirss

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = YomitoriApplication::class)
class SmbMetadataNormalizationDatabaseMigrationTest {
  private lateinit var context: Context
  private var database: YomitoriDatabase? = null

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @After
  fun tearDown() {
    database?.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `version 26 database adds SMB metadata normalization tables without losing existing data`() {
    val legacySchema = DatabaseSchema(
      version = 26,
      contributions = appDatabaseSchema.contributions.map { contribution ->
        DatabaseSchemaContribution(
          owner = contribution.owner,
          createSchema = contribution.createSchema,
          migrations = contribution.migrations.filter { migration -> migration.targetVersion <= 26 },
        )
      },
    )
    val legacyDatabase = YomitoriDatabase.create(context, legacySchema)
    val legacyDb = legacyDatabase.writableDatabase
    legacyDb.execSQL("DROP TABLE smb_metadata_normalization_items")
    legacyDb.execSQL("DROP TABLE smb_metadata_normalization_batches")
    legacyDb.execSQL("DROP TABLE smb_metadata_normalization_decisions")
    legacyDb.insertOrThrow(
      "tasks",
      null,
      ContentValues().apply {
        put("id", "preserved-task")
        put("title", "移行前タスク")
        put("description", "")
        put("created_at", "2026-08-21T00:00:00Z")
        put("sort_order", 0)
      },
    )
    assertEquals(26, legacyDb.version)
    assertFalse(tableExistsForNormalizationMigration(legacyDb, "smb_metadata_normalization_batches"))
    assertFalse(tableExistsForNormalizationMigration(legacyDb, "smb_metadata_normalization_items"))
    assertFalse(tableExistsForNormalizationMigration(legacyDb, "smb_metadata_normalization_decisions"))
    legacyDatabase.close()

    val upgraded = YomitoriDatabase.create(context, appDatabaseSchema).also { database = it }
    val upgradedDb = upgraded.writableDatabase

    assertEquals(27, upgradedDb.version)
    assertTrue(tableExistsForNormalizationMigration(upgradedDb, "smb_metadata_normalization_batches"))
    assertTrue(tableExistsForNormalizationMigration(upgradedDb, "smb_metadata_normalization_items"))
    assertTrue(tableExistsForNormalizationMigration(upgradedDb, "smb_metadata_normalization_decisions"))
    assertEquals(
      "移行前タスク",
      upgradedDb.rawQuery("SELECT title FROM tasks WHERE id = ?", arrayOf("preserved-task")).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
      },
    )
  }
}

private fun tableExistsForNormalizationMigration(db: SQLiteDatabase, table: String): Boolean = db.rawQuery(
  "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
  arrayOf(table),
).use { it.moveToFirst() }
