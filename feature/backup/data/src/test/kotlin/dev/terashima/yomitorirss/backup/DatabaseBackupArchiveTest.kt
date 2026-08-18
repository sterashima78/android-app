package dev.terashima.yomitorirss.feature.backup.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.DatabaseSchema
import dev.terashima.yomitorirss.core.database.DatabaseSchemaContribution
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
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

@RunWith(RobolectricTestRunner::class)
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
        version = 1,
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
    val bytes = withWrongChecksum(output.toByteArray())

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
  fun `ZIP以外は新形式と判定しない`() {
    val file = File(context.cacheDir, "legacy.json")
    file.writeText("{\"format\":\"yomitori-rss-backup\",\"version\":8}")
    try {
      assertEquals(false, DatabaseBackupArchive.looksLikeArchive(file))
    } finally {
      file.delete()
    }
  }

  private fun withWrongChecksum(bytes: ByteArray): ByteArray {
    val output = ByteArrayOutputStream()
    ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
      ZipOutputStream(output).use { zip ->
        while (true) {
          val entry = input.nextEntry ?: break
          zip.putNextEntry(ZipEntry(entry.name))
          if (entry.name == "manifest.json") {
            val manifest = JSONObject(input.readBytes().toString(Charsets.UTF_8))
              .put("databaseSha256", "0".repeat(64))
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
}
