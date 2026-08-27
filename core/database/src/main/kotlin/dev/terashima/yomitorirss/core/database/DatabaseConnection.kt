package dev.terashima.yomitorirss.core.database

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseConnection(
  private val helper: SQLiteOpenHelper,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier.shared,
) {
  val readable: SQLiteDatabase
    get() = helper.readableDatabase

  /**
   * Raw writable connection for schema initialization and maintenance operations.
   *
   * Durable user-data mutations should use [write] or [transaction] so persistence observers are
   * notified only after the mutation completes successfully.
   */
  val writable: SQLiteDatabase
    get() = helper.writableDatabase

  fun <T> write(block: SQLiteDatabase.() -> T): T {
    val value = writable.block()
    dataChanges.notifyChanged()
    return value
  }

  fun <T> transaction(block: SQLiteDatabase.() -> T): T {
    val database = writable
    database.beginTransaction()
    val value = try {
      database.block().also { database.setTransactionSuccessful() }
    } finally {
      database.endTransaction()
    }
    dataChanges.notifyChanged()
    return value
  }
}
