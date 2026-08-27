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
    val existingScopes = transactionScopes.get()
    if (database.inTransaction()) {
      if (notifyPersistenceChange) {
        val scope = existingScopes?.get(database)
          ?: error("Durable DatabaseConnection mutation cannot join an unmanaged SQLite transaction")
        scope.publishPersistenceChange = true
      }
      return database.block()
    }

    database.beginTransaction()
    val scopes = existingScopes ?: mutableMapOf<SQLiteDatabase, TransactionScope>().also(transactionScopes::set)
    val scope = TransactionScope(publishPersistenceChange = notifyPersistenceChange)
    check(scopes.put(database, scope) == null) { "SQLite transaction scope is already registered" }
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
        scopes.remove(database)
        if (scopes.isEmpty()) transactionScopes.remove()
      }
    }
    if (changed && scope.publishPersistenceChange) persistenceChanges.notifyChanged()
    return value
  }

  private data class TransactionScope(
    var publishPersistenceChange: Boolean,
  )

  private companion object {
    /**
     * SQLite transaction ownership is thread-scoped, not DatabaseConnection-instance-scoped.
     * Multiple wrappers around the same helper must therefore share the active scope so a nested
     * durable write cannot disappear inside a local transaction owned by another wrapper.
     */
    val transactionScopes = ThreadLocal<MutableMap<SQLiteDatabase, TransactionScope>?>()
  }
}

private fun SQLiteDatabase.totalChanges(): Long =
  rawQuery("SELECT total_changes()", null).use { cursor ->
    check(cursor.moveToFirst()) { "SQLite total_changes() returned no row" }
    cursor.getLong(0)
  }
