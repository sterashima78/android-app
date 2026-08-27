package dev.terashima.yomitorirss.core.database

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseConnection(
  private val helper: SQLiteOpenHelper,
  private val persistenceChanges: PersistenceChangeNotifier = PersistenceChangeNotifier.shared,
) {
  val readable: SQLiteDatabase
    get() = helper.readableDatabase

  /**
   * Raw writable connection for schema initialization and maintenance operations.
   *
   * Durable user-data mutations should use [write] or [transaction] so persistence observers are
   * notified after a successful operation that actually changed rows.
   */
  val writable: SQLiteDatabase
    get() = helper.writableDatabase

  fun <T> write(block: SQLiteDatabase.() -> T): T {
    val database = writable
    val changesBefore = database.totalChanges()
    val value = database.block()
    if (database.totalChanges() > changesBefore) persistenceChanges.notifyChanged()
    return value
  }

  fun <T> transaction(block: SQLiteDatabase.() -> T): T {
    val database = writable
    val changesBefore = database.totalChanges()
    database.beginTransaction()
    val value = try {
      database.block().also { database.setTransactionSuccessful() }
    } finally {
      database.endTransaction()
    }
    if (database.totalChanges() > changesBefore) persistenceChanges.notifyChanged()
    return value
  }
}

private fun SQLiteDatabase.totalChanges(): Long =
  rawQuery("SELECT total_changes()", null).use { cursor ->
    check(cursor.moveToFirst()) { "SQLite total_changes() returned no row" }
    cursor.getLong(0)
  }
