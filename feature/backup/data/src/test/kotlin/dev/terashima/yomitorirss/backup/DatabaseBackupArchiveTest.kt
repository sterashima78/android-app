package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseBackupArchiveTest {
  private lateinit var context: Context
  private lateinit var database: YomitoriDatabase

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
    BackupPreferences.BACKED_UP_PREFERENCES.forEach { name ->
      context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }
    database = YomitoriDatabase.create(
      context,
      DatabaseSchema(
        version = TEST_SCHEMA_VERSION,
        contributions = listOf(
          DatabaseSchemaContribution(
            owner = "test",
            createSchema = { db ->
              db.execSQL("CREATE TABLE IF NOT EXISTS backup_test(id TEXT PRIMARY KEY NOT NULL,value TEXT NOT NULL)")
            },
          ),
        ),
      ),
    )
  }

  @After
  fun tearDown() {
    database.close()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `archiveからDBとユーザー設定を復元できる`() {
    database.writableDatabase.execSQL(
      "INSERT INTO backup_test(id,value) VALUES(?,?)",
      arrayOf("1", "before"),
    )
    context.getSharedPreferences("summary_preferences", Context.MODE_PRIVATE)
      .edit().putString("summary_prompt", "before-prompt").commit()

    val archive = DatabaseBackupArchive(context, database)
    val output = ByteArrayOutputStream()
    archive.writeTo(output)
    val bytes = output.toByteArray()

    assertTrue(bytes.size > 4)
    assertTrue(bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(0x50, 0x4b, 0x03, 0x04)))

    database.writableDatabase.execSQL(
      "UPDATE backup_test SET value = ? WHERE id = ?",
      arrayOf("after", "1"),
    )
    context.getSharedPreferences("summary_preferences", Context.MODE_PRIVATE)
      .edit().putString("summary_prompt", "after-prompt").commit()

    archive.restore(ByteArrayInputStream(bytes))

    val restored = database.readableDatabase.rawQuery(
      "SELECT value FROM backup_test WHERE id = ?",
      arrayOf("1"),
    ).use { cursor ->
      check(cursor.moveToFirst())
      cursor.getString(0)
    }
    assertEquals("before", restored)
    assertEquals(
      "before-prompt",
      context.getSharedPreferences("summary_preferences", Context.MODE_PRIVATE)
        .getString("summary_prompt", null),
    )
  }

  @Test
  fun `checksumが壊れたarchiveを復元しない`() {
    database.writableDatabase.execSQL(
      "INSERT INTO backup_test(id,value) VALUES(?,?)",
      arrayOf("1", "current"),
    )
    val archive = DatabaseBackupArchive(context, database)
    val output = ByteArrayOutputStream()
    archive.writeTo(output)
    val bytes = rewriteManifest(output.toByteArray()) { manifest ->
      manifest.put("databaseSha256", "0".repeat(64))
    }

    runCatching { archive.restore(ByteArrayInputStream(bytes)) }
      .onSuccess { error("壊れたarchiveが復元されました") }

    val current = database.readableDatabase.rawQuery(
      "SELECT value FROM backup_test WHERE id = ?",
      arrayOf("1"),
    ).use { cursor ->
      check(cursor.moveToFirst())
      cursor.getString(0)
    }
    assertEquals("current", current)
  }

  @Test
  fun `現在と異なるschema versionのarchiveを復元しない`() {
    val archive = DatabaseBackupArchive(context, database)
    val output = ByteArrayOutputStream()
    archive.writeTo(output)
    val bytes = rewriteManifest(output.toByteArray()) { manifest ->
      manifest.put("schemaVersion", TEST_SCHEMA_VERSION - 1)
    }

    runCatching { archive.restore(ByteArrayInputStream(bytes)) }
      .onSuccess { error("異なるschema versionのarchiveが復元されました") }
  }

  @Test
  fun `archiveはMosaic形式の名前を使う`() {
    val archive = DatabaseBackupArchive(context, database)
    val output = ByteArrayOutputStream()
    archive.writeTo(output)

    val entries = mutableSetOf<String>()
    var manifest: JSONObject? = null
    ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { input ->
      while (true) {
        val entry = input.nextEntry ?: break
        entries += entry.name
        if (entry.name == "manifest.json") {
          manifest = JSONObject(input.readBytes().toString(Charsets.UTF_8))
        }
        input.closeEntry()
      }
    }

    assertEquals("mosaic-database-backup", manifest?.getString("format"))
    assertEquals("mosaic.db", manifest?.getString("databaseName"))
    assertEquals(TEST_SCHEMA_VERSION, manifest?.getInt("schemaVersion"))
    assertEquals(
      setOf("manifest.json", "database/mosaic.db", "preferences/user-preferences.json"),
      entries,
    )
  }

  @Test
  fun `JSONバックアップは受け付けない`() {
    val archive = DatabaseBackupArchive(context, database)
    val bytes = "{\"format\":\"legacy-json\",\"version\":8}".toByteArray()

    runCatching { archive.restore(ByteArrayInputStream(bytes)) }
      .onSuccess { error("JSONバックアップが復元されました") }
  }

  private fun rewriteManifest(bytes: ByteArray, transform: (JSONObject) -> JSONObject): ByteArray {
    val output = ByteArrayOutputStream()
    ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
      ZipOutputStream(output).use { zip ->
        while (true) {
          val entry = input.nextEntry ?: break
          zip.putNextEntry(ZipEntry(entry.name))
          if (entry.name == "manifest.json") {
            val manifest = transform(JSONObject(input.readBytes().toString(Charsets.UTF_8)))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
          } else {
            input.copyTo(zip)
          }
          zip.closeEntry()
          input.closeEntry()
        }
      }
    }
    return output.toByteArray()
  }

  private companion object {
    const val TEST_SCHEMA_VERSION = 27
  }
}
