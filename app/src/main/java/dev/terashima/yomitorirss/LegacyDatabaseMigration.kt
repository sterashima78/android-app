package dev.terashima.yomitorirss

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.YomitoriDatabase

internal object LegacyDatabaseMigration {
  private const val TASK_DATABASE = "yomitori-tasks.db"
  private const val CHAT_DATABASE = "yomitori-chat.db"
  private const val YOUTUBE_DATABASE = "youtube.db"

  fun migrate(context: Context, database: YomitoriDatabase) {
    val appContext = context.applicationContext
    val target = database.writableDatabase

    migrateLegacyDatabase(appContext, target, TASK_DATABASE, "legacy_tasks", ::copyTasks)
    migrateLegacyDatabase(appContext, target, CHAT_DATABASE, "legacy_chat", ::copyChat)
    migrateLegacyDatabase(appContext, target, YOUTUBE_DATABASE, "legacy_youtube", ::copyYouTube)
  }

  private fun migrateLegacyDatabase(
    context: Context,
    target: SQLiteDatabase,
    databaseName: String,
    alias: String,
    copy: (SQLiteDatabase, String) -> Boolean,
  ) {
    val sourceFile = context.getDatabasePath(databaseName)
    if (!sourceFile.exists()) return

    target.execSQL("ATTACH DATABASE ? AS $alias", arrayOf(sourceFile.absolutePath))
    var migrated = false
    try {
      target.beginTransaction()
      try {
        target.execSQL("PRAGMA defer_foreign_keys = ON")
        migrated = copy(target, alias)
        target.setTransactionSuccessful()
      } finally {
        target.endTransaction()
      }
    } finally {
      target.execSQL("DETACH DATABASE $alias")
    }

    if (migrated) {
      context.deleteDatabase(databaseName)
    }
  }

  private fun copyTasks(db: SQLiteDatabase, alias: String): Boolean {
    if (!db.tableExists(alias, "tasks")) return false
    val description = if (db.columnExists(alias, "tasks", "description")) "description" else "''"
    db.execSQL(
      """
      INSERT OR IGNORE INTO tasks(id,title,description,parent_id,due_date,completed_at,created_at,sort_order)
      SELECT id,title,$description,parent_id,due_date,completed_at,created_at,sort_order
      FROM $alias.tasks
      """.trimIndent(),
    )
    return true
  }

  private fun copyChat(db: SQLiteDatabase, alias: String): Boolean {
    if (!db.tableExists(alias, "chat_sessions") || !db.tableExists(alias, "chat_messages")) return false
    db.execSQL(
      """
      INSERT OR IGNORE INTO chat_sessions(id,title,created_at,updated_at)
      SELECT id,title,created_at,updated_at
      FROM $alias.chat_sessions
      """.trimIndent(),
    )
    db.execSQL(
      """
      INSERT OR IGNORE INTO chat_messages(id,session_id,role,content,created_at)
      SELECT id,session_id,role,content,created_at
      FROM $alias.chat_messages
      """.trimIndent(),
    )
    return true
  }

  private fun copyYouTube(db: SQLiteDatabase, alias: String): Boolean {
    if (!db.tableExists(alias, "channels") || !db.tableExists(alias, "videos")) return false
    val watchLater = if (db.columnExists(alias, "videos", "is_watch_later")) "is_watch_later" else "0"
    db.execSQL(
      """
      INSERT OR IGNORE INTO channels(channel_id,title,channel_url,added_at)
      SELECT channel_id,title,channel_url,added_at
      FROM $alias.channels
      """.trimIndent(),
    )
    db.execSQL(
      """
      INSERT OR IGNORE INTO videos(video_id,channel_id,title,video_url,published_at,is_read,is_watch_later)
      SELECT video_id,channel_id,title,video_url,published_at,is_read,$watchLater
      FROM $alias.videos
      """.trimIndent(),
    )
    return true
  }

  private fun SQLiteDatabase.tableExists(alias: String, table: String): Boolean = rawQuery(
    "SELECT 1 FROM $alias.sqlite_master WHERE type='table' AND name=? LIMIT 1",
    arrayOf(table),
  ).use { it.moveToFirst() }

  private fun SQLiteDatabase.columnExists(alias: String, table: String, column: String): Boolean =
    rawQuery("PRAGMA $alias.table_info($table)", null).use { cursor ->
      val nameIndex = cursor.getColumnIndexOrThrow("name")
      var found = false
      while (cursor.moveToNext()) {
        if (cursor.getString(nameIndex) == column) {
          found = true
          break
        }
      }
      found
    }
}
