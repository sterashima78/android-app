package dev.terashima.yomitorirss.core.database

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseConnection(
  private val helper: SQLiteOpenHelper,
  private val persistenceChanges: PersistenceChangeNotifier = PersistenceChangeNotifier.shared,
) {
  private val transactionScope = ThreadLocal<TransactionScope?>()

  val readable: SQLiteDatabase
    get() = helper.readableDatabase

  /**
   * Raw writable connection for schema initialization and migration operations.
   *
   * Runtime mutations should use [write]/[transaction] for durable user data, or
   * [localWrite]/[localTransaction] for explicitly backup-excluded cache/transient state.
   */
  val writable: SQLiteDatabase
    get() = helper.writableDatabase

  /** Runs one durable mutation atomically and notifies observers after a successful commit. */
  fun <T> write(block: SQLiteDatabase.() -> T): T = transact(notifyPersistenceChange = true, block)

  /** Runs multiple durable mutations atomically and notifies observers once after commit. */
  fun <T> transaction(block: SQLiteDatabase.() -> T): T = transact(notifyPersistenceChange = true, block)

  /**
   * Runs one mutation of backup-excluded local/cache/transient state atomically without publishing
   * a persistence change.
   */
  fun <T> localWrite(block: SQLiteDatabase.() -> T): T =
    transact(notifyPersistenceChange = false, block)

  /**
   * Runs backup-excluded local/cache/transient mutations atomically without publishing a
   * persistence change. If a durable [write] or [transaction] is nested inside this transaction,
   * the outer commit is promoted to a persistence change so a durable mutation cannot be hidden.
   */
  fun <T> localTransaction(block: SQLiteDatabase.() -> T): T =
    transact(notifyPersistenceChange = false, block)

  private fun <T> transact(
    notifyPersistenceChange: Boolean,
    block: SQLiteDatabase.() -> T,
  ): T {
    val database = writable
    if (database.inTransaction()) {
      if (notifyPersistenceChange) transactionScope.get()?.publishPersistenceChange = true
      return database.block()
    }

    database.beginTransaction()
    val scope = TransactionScope(publishPersistenceChange = notifyPersistenceChange)
    transactionScope.set(scope)
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
      try {
        database.endTransaction()
      } finally {
        transactionScope.remove()
      }
    }
    if (changed && scope.publishPersistenceChange) persistenceChanges.notifyChanged()
    return value
  }

  private data class TransactionScope(
    var publishPersistenceChange: Boolean,
  )
}

private fun SQLiteDatabase.totalChanges(): Long =
  rawQuery("SELECT total_changes()", null).use { cursor ->
    check(cursor.moveToFirst()) { "SQLite total_changes() returned no row" }
    cursor.getLong(0)
  }
