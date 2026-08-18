package dev.terashima.yomitorirss.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class YomitoriDatabase private constructor(
  private val appContext: Context,
  private val schema: DatabaseSchema,
) : SQLiteOpenHelper(appContext, DB_NAME, null, schema.version) {
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

  val schemaVersion: Int
    get() = schema.version

  @Synchronized
  fun createSnapshot(destination: File) {
    require(destination.absolutePath != databaseFile().absolutePath) {
      "Snapshot destination must differ from the live database"
    }
    destination.parentFile?.mkdirs()
    destination.delete()

    // WAL may contain committed rows that are not present in the main database file yet.
    // Switching back to rollback journaling checkpoints WAL first. Holding an exclusive
    // transaction after that keeps the main file stable while it is copied.
    writableDatabase
    var walDisabled = false
    try {
      setWriteAheadLoggingEnabled(false)
      walDisabled = true
      val stableDatabase = writableDatabase
      stableDatabase.beginTransaction()
      try {
        copyFileSynced(databaseFile(), destination)
      } finally {
        stableDatabase.endTransaction()
      }
    } finally {
      if (walDisabled) {
        setWriteAheadLoggingEnabled(true)
      }
    }
  }

  fun validateSnapshot(snapshot: File): Int {
    require(snapshot.isFile && snapshot.length() > 0L) { "バックアップDBが空です" }
    val db = SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
    return db.use {
      val applicationId = it.longPragma("application_id")
      require(applicationId == APPLICATION_ID.toLong()) { "YomitoriのバックアップDBではありません" }
      val version = it.version
      require(version in 1..schema.version) {
        "このアプリより新しいDB形式です (backup=$version, app=${schema.version})"
      }
      it.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
        require(cursor.moveToFirst() && cursor.getString(0) == "ok") { "バックアップDBが破損しています" }
      }
      version
    }
  }

  fun markSnapshot(snapshot: File) {
    val db = SQLiteDatabase.openDatabase(snapshot.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
    db.use {
      it.execSQL("PRAGMA application_id = $APPLICATION_ID")
      it.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
        require(cursor.moveToFirst() && cursor.getString(0) == "ok") { "作成したバックアップDBが破損しています" }
      }
    }
  }

  @Synchronized
  fun replaceWithSnapshot(snapshot: File) {
    validateSnapshot(snapshot)

    val current = databaseFile()
    val staged = File(current.parentFile, "$DB_NAME.restore-new")
    val previous = File(current.parentFile, "$DB_NAME.restore-previous")
    staged.delete()
    previous.delete()
    copyFileSynced(snapshot, staged)

    close()
    deleteSidecars(current)

    var currentMoved = false
    try {
      if (current.exists()) {
        check(current.renameTo(previous)) { "現在のDBを退避できませんでした" }
        currentMoved = true
      }
      check(staged.renameTo(current)) { "復元DBを配置できませんでした" }

      // Opening through SQLiteOpenHelper applies normal schema migrations when the
      // snapshot was produced by an older compatible app version.
      val restored = writableDatabase
      restored.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
        check(cursor.moveToFirst() && cursor.getString(0) == "ok") { "復元後のDB検証に失敗しました" }
      }
      previous.delete()
    } catch (error: Throwable) {
      runCatching { close() }
      current.delete()
      deleteSidecars(current)
      if (currentMoved && previous.exists()) {
        previous.renameTo(current)
        runCatching { writableDatabase }
      }
      throw error
    } finally {
      staged.delete()
    }
  }

  private fun databaseFile(): File = appContext.getDatabasePath(DB_NAME)

  private fun deleteSidecars(database: File) {
    File(database.absolutePath + "-wal").delete()
    File(database.absolutePath + "-shm").delete()
    File(database.absolutePath + "-journal").delete()
  }

  private fun copyFileSynced(source: File, destination: File) {
    FileInputStream(source).channel.use { input ->
      FileOutputStream(destination, false).use { outputStream ->
        val output = outputStream.channel
        var offset = 0L
        while (offset < input.size()) {
          offset += input.transferTo(offset, input.size() - offset, output)
        }
        output.force(true)
        outputStream.fd.sync()
      }
    }
  }

  private fun SQLiteDatabase.longPragma(name: String): Long =
    rawQuery("PRAGMA $name", null).use { cursor ->
      check(cursor.moveToFirst()) { "PRAGMA $name returned no rows" }
      cursor.getLong(0)
    }

  companion object {
    const val DB_NAME = "yomitori-rss.db"
    const val APPLICATION_ID = 0x594F4D49 // ASCII "YOMI"

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
