package dev.terashima.yomitorirss

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
class SmbCoverPrefetchDatabaseMigrationTest {
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
  fun `version 25 database adds SMB cover prefetch queue while upgrading`() {
    val legacySchema = DatabaseSchema(
      version = 25,
      contributions = appDatabaseSchema.contributions.map { contribution ->
        DatabaseSchemaContribution(
          owner = contribution.owner,
          createSchema = contribution.createSchema,
          migrations = contribution.migrations.filter { migration -> migration.targetVersion <= 25 },
        )
      },
    )
    val legacyDatabase = YomitoriDatabase.create(context, legacySchema)
    legacyDatabase.writableDatabase.execSQL("DROP TABLE smb_cover_prefetch_queue")
    assertFalse(tableExists(legacyDatabase.writableDatabase, "smb_cover_prefetch_queue"))
    legacyDatabase.close()

    val upgraded = YomitoriDatabase.create(context, appDatabaseSchema).also { database = it }

    assertEquals(27, upgraded.writableDatabase.version)
    assertTrue(tableExists(upgraded.writableDatabase, "smb_cover_prefetch_queue"))
  }
}

private fun tableExists(db: SQLiteDatabase, table: String): Boolean = db.rawQuery(
  "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1",
  arrayOf(table),
).use { it.moveToFirst() }
