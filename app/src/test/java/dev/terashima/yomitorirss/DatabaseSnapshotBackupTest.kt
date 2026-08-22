package dev.terashima.yomitorirss

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = YomitoriApplication::class, sdk = [35])
class DatabaseSnapshotBackupTest {
  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @After
  fun tearDown() {
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `WALを含むDBをsnapshotして置換復元できる`() {
    val database = YomitoriDatabase.create(context)
    val snapshot = File(context.cacheDir, "snapshot-roundtrip.db")
    snapshot.delete()
    try {
      database.writableDatabase.execSQL(
        "INSERT INTO tasks(id,title,description,created_at,sort_order) VALUES(?,?,?,?,?)",
        arrayOf<Any?>("task-1", "バックアップ前", "", "2026-08-18T00:00:00Z", 0),
      )

      database.createSnapshot(snapshot)
      database.markSnapshot(snapshot)
      assertEquals(appDatabaseSchema.version, database.validateSnapshot(snapshot))

      database.writableDatabase.execSQL(
        "UPDATE tasks SET title = ? WHERE id = ?",
        arrayOf("バックアップ後", "task-1"),
      )

      database.replaceWithSnapshot(snapshot)

      val restoredTitle = database.readableDatabase.rawQuery(
        "SELECT title FROM tasks WHERE id = ?",
        arrayOf("task-1"),
      ).use { cursor ->
        check(cursor.moveToFirst())
        cursor.getString(0)
      }
      assertEquals("バックアップ前", restoredTitle)
    } finally {
      database.close()
      snapshot.delete()
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun `Yomitori識別子のないSQLite fileは復元対象にしない`() {
    val database = YomitoriDatabase.create(context)
    val snapshot = File(context.cacheDir, "unmarked-snapshot.db")
    snapshot.delete()
    try {
      database.createSnapshot(snapshot)
      database.validateSnapshot(snapshot)
    } finally {
      database.close()
      snapshot.delete()
    }
  }

  @Test(expected = IllegalArgumentException::class)
  fun `現在と異なるschema versionのsnapshotは復元対象にしない`() {
    val database = YomitoriDatabase.create(context)
    val snapshot = File(context.cacheDir, "old-version-snapshot.db")
    snapshot.delete()
    try {
      database.createSnapshot(snapshot)
      database.markSnapshot(snapshot)
      SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
        it.version = appDatabaseSchema.version - 1
      }
      database.validateSnapshot(snapshot)
    } finally {
      database.close()
      snapshot.delete()
    }
  }
}
