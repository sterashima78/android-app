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

  /** Runs one durable mutation atomically and notifies observers after a successful commit. */
  fun <T> write(block: SQLiteDatabase.() -> T): T = transaction(block)

  fun <T> transaction(block: SQLiteDatabase.() -> T): T {
    val database = writable
    database.beginTransaction()
    var changed = false
    val value = try {
      // WAL may use multiple SQLite connections. Measuring both values while this transaction is
      // active pins the queries and mutations to the same connection.
      val changesBefore = database.totalChanges()
      val result = database.block()
      changed = database.totalChanges() > changesBefore
      database.setTransactionSuccessful()
      result
    } finally {
      database.endTransaction()
    }
    if (changed) persistenceChanges.notifyChanged()
    return value
  }
}

private fun SQLiteDatabase.totalChanges(): Long =
  rawQuery("SELECT total_changes()", null).use { cursor ->
    check(cursor.moveToFirst()) { "SQLite total_changes() returned no row" }
    cursor.getLong(0)
  }
