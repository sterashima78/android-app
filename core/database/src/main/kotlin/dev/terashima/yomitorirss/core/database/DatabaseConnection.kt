package dev.terashima.yomitorirss.core.database

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseConnection(
  private val helper: SQLiteOpenHelper,
) {
  val readable: SQLiteDatabase
    get() = helper.readableDatabase

  val writable: SQLiteDatabase
    get() = helper.writableDatabase

  fun <T> transaction(block: SQLiteDatabase.() -> T): T {
    val database = writable
    database.beginTransaction()
    return try {
      val value = database.block()
      database.setTransactionSuccessful()
      value
    } finally {
      database.endTransaction()
    }
  }
}
