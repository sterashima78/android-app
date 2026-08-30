package dev.terashima.yomitorirss.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class DatabaseConnectionTest {
  private lateinit var helper: SQLiteOpenHelper

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    helper = object : SQLiteOpenHelper(context, null, null, 1) {
      override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT NOT NULL)")
      }

      override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }
  }

  @After
  fun tearDown() {
    helper.close()
  }

  @Test
  fun `writeは実際に変更されたときだけ通知する`() {
    val notifier = PersistenceChangeNotifier()
    val database = DatabaseConnection(helper, notifier)

    database.write {
      insertOrThrow("items", null, values("first"))
    }
    assertEquals(1L, notifier.version.value)

    database.write {
      update("items", values("missing"), "id = ?", arrayOf("999"))
    }
    assertEquals(1L, notifier.version.value)
  }

  @Test
  fun `writeが失敗した場合はrollbackして通知しない`() {
    val notifier = PersistenceChangeNotifier()
    val database = DatabaseConnection(helper, notifier)

    assertThrows(IllegalStateException::class.java) {
      database.write {
        insertOrThrow("items", null, values("rolled-back"))
        error("rollback")
      }
    }

    assertEquals(0L, notifier.version.value)
    assertEquals(0, database.readable.rawQuery("SELECT COUNT(*) FROM items", null).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    })
  }

  @Test
  fun `transactionは複数変更をcommit単位で一度だけ通知する`() {
    val notifier = PersistenceChangeNotifier()
    val database = DatabaseConnection(helper, notifier)

    database.transaction {
      insertOrThrow("items", null, values("first"))
      insertOrThrow("items", null, values("second"))
    }

    assertEquals(1L, notifier.version.value)
    assertEquals(2, database.readable.rawQuery("SELECT COUNT(*) FROM items", null).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    })
  }

  @Test
  fun `外側transaction内のwriteは外側commitで一度だけ通知する`() {
    val notifier = PersistenceChangeNotifier()
    val database = DatabaseConnection(helper, notifier)

    database.transaction {
      insertOrThrow("items", null, values("first"))
      database.write {
        update("items", values("updated"), "id = ?", arrayOf("1"))
      }
      assertEquals(0L, notifier.version.value)
    }

    assertEquals(1L, notifier.version.value)
    assertEquals("updated", database.readable.rawQuery("SELECT value FROM items WHERE id = 1", null).use { cursor ->
      cursor.moveToFirst()
      cursor.getString(0)
    })
  }

  @Test
  fun `transactionがrollbackされた場合は通知しない`() {
    val notifier = PersistenceChangeNotifier()
    val database = DatabaseConnection(helper, notifier)

    assertThrows(IllegalStateException::class.java) {
      database.transaction {
        insertOrThrow("items", null, values("rolled-back"))
        error("rollback")
      }
    }

    assertEquals(0L, notifier.version.value)
    assertEquals(0, database.readable.rawQuery("SELECT COUNT(*) FROM items", null).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    })
  }

  @Test
  fun `localWriteはcommitしても永続化変更を通知しない`() {
    val notifier = PersistenceChangeNotifier()
    val database = DatabaseConnection(helper, notifier)

    database.localWrite {
      insertOrThrow("items", null, values("local"))
    }

    assertEquals(0L, notifier.version.value)
    assertEquals(1, database.readable.rawQuery("SELECT COUNT(*) FROM items", null).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    })
  }

  @Test
  fun `localTransaction内のdurable writeは外側commit後に通知する`() {
    val notifier = PersistenceChangeNotifier()
    val database = DatabaseConnection(helper, notifier)

    database.localTransaction {
      insertOrThrow("items", null, values("local"))
      database.write {
        insertOrThrow("items", null, values("durable"))
      }
      assertEquals(0L, notifier.version.value)
    }

    assertEquals(1L, notifier.version.value)
    assertEquals(2, database.readable.rawQuery("SELECT COUNT(*) FROM items", null).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    })
  }

  @Test
  fun `別wrapperのdurable writeもlocalTransactionの外側commit後に通知する`() {
    val notifier = PersistenceChangeNotifier()
    val outer = DatabaseConnection(helper, notifier)
    val inner = DatabaseConnection(helper, notifier)

    outer.localTransaction {
      insertOrThrow("items", null, values("local"))
      inner.write {
        insertOrThrow("items", null, values("durable"))
      }
      assertEquals(0L, notifier.version.value)
    }

    assertEquals(1L, notifier.version.value)
    assertEquals(2, outer.readable.rawQuery("SELECT COUNT(*) FROM items", null).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    })
  }

  @Test
  fun `localTransaction内のdurable writeがrollbackされた場合は通知しない`() {
    val notifier = PersistenceChangeNotifier()
    val database = DatabaseConnection(helper, notifier)

    assertThrows(IllegalStateException::class.java) {
      database.localTransaction {
        insertOrThrow("items", null, values("local"))
        database.write {
          insertOrThrow("items", null, values("durable"))
        }
        error("rollback")
      }
    }

    assertEquals(0L, notifier.version.value)
    assertEquals(0, database.readable.rawQuery("SELECT COUNT(*) FROM items", null).use { cursor ->
      cursor.moveToFirst()
      cursor.getInt(0)
    })
  }

  private fun values(value: String): ContentValues = ContentValues().apply {
    put("value", value)
  }
}
