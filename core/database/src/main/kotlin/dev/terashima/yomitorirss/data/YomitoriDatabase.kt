package dev.terashima.yomitorirss.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class YomitoriDatabase private constructor(
  context: Context,
  private val schema: DatabaseSchema,
) : SQLiteOpenHelper(context, DB_NAME, null, schema.version) {
  override fun onConfigure(db: SQLiteDatabase) {
    super.onConfigure(db)
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun onCreate(db: SQLiteDatabase) {
    schema.create(db)
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    schema.migrate(db, oldVersion, newVersion, DatabaseMigrationPhase.BEFORE_SCHEMA)
    schema.create(db)
    schema.migrate(db, oldVersion, newVersion, DatabaseMigrationPhase.AFTER_SCHEMA)
  }

  companion object {
    const val DB_NAME = "yomitori-rss.db"

    fun create(context: Context): YomitoriDatabase {
      val app = context.applicationContext
      val provider = app as? DatabaseSchemaProvider
        ?: error("Application must implement DatabaseSchemaProvider to create YomitoriDatabase.")
      return create(app, provider.databaseSchema)
    }

    fun create(context: Context, schema: DatabaseSchema): YomitoriDatabase {
      val app = context.applicationContext
      return YomitoriDatabase(app, schema).also {
        it.setWriteAheadLoggingEnabled(true)
        it.writableDatabase
      }
    }
  }
}
