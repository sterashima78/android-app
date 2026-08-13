package dev.terashima.yomitorirss.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

class YomitoriDatabase private constructor(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
  override fun onConfigure(db: SQLiteDatabase) {
    super.onConfigure(db)
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun onCreate(db: SQLiteDatabase) = schema(db)

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 10) migrateToVersion10(db)
    schema(db)
    if (oldVersion < 6) migrateToVersion6(db)
    if (oldVersion < 7) migrateToVersion7(db)
    if (oldVersion < 8) migrateToVersion8(db)
    if (oldVersion < 9) migrateToVersion9(db)
    if (oldVersion < 11) migrateToVersion11(db)
    if (oldVersion < 12) migrateToVersion12(db)
  }

  private fun schema(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS feed_folders(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,created_at TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS feeds(id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,feed_url TEXT NOT NULL UNIQUE,site_url TEXT,etag TEXT,last_modified TEXT,last_fetched_at TEXT,last_error TEXT,created_at TEXT NOT NULL,folder_id TEXT REFERENCES feed_folders(id) ON DELETE SET NULL)")
    db.execSQL("CREATE INDEX IF NOT EXISTS feeds_folder_id ON feeds(folder_id,title)")
    db.execSQL("CREATE TABLE IF NOT EXISTS articles(id TEXT PRIMARY KEY NOT NULL,feed_id TEXT REFERENCES feeds(id) ON DELETE SET NULL,external_id TEXT,identity_key TEXT NOT NULL,url TEXT NOT NULL,title TEXT NOT NULL,published_at TEXT NOT NULL,fetched_at TEXT NOT NULL,read_at TEXT,saved_at TEXT,source_title TEXT NOT NULL,source_feed_url TEXT NOT NULL)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS article_feed_identity ON articles(feed_id,identity_key) WHERE feed_id IS NOT NULL")
    db.execSQL("CREATE INDEX IF NOT EXISTS article_unread_date ON articles(read_at,published_at DESC)")
    db.execSQL("CREATE INDEX IF NOT EXISTS article_saved_date ON articles(saved_at,published_at DESC)")
    db.execSQL("CREATE INDEX IF NOT EXISTS article_read_date ON articles(read_at DESC)")
    db.execSQL("CREATE INDEX IF NOT EXISTS article_url ON articles(url)")
    db.execSQL("CREATE TABLE IF NOT EXISTS tags(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,created_at TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS article_tags(article_id TEXT NOT NULL REFERENCES articles(id) ON DELETE CASCADE,tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,PRIMARY KEY(article_id,tag_id))")
    db.execSQL("CREATE TABLE IF NOT EXISTS bookmark_folders(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,system_kind TEXT,created_at TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS article_folders(article_id TEXT PRIMARY KEY NOT NULL REFERENCES articles(id) ON DELETE CASCADE,folder_id TEXT NOT NULL REFERENCES bookmark_folders(id) ON DELETE CASCADE)")
    db.execSQL("CREATE INDEX IF NOT EXISTS article_folder_folder_id ON article_folders(folder_id,article_id)")
    db.execSQL("CREATE TABLE IF NOT EXISTS article_summaries(article_id TEXT PRIMARY KEY NOT NULL REFERENCES articles(id) ON DELETE CASCADE,summary TEXT NOT NULL,model_id TEXT NOT NULL,created_at TEXT NOT NULL)")
    db.execSQL("CREATE TABLE IF NOT EXISTS summary_tasks(article_id TEXT PRIMARY KEY NOT NULL REFERENCES articles(id) ON DELETE CASCADE,state TEXT NOT NULL,force_refresh INTEGER NOT NULL DEFAULT 0,queued_at TEXT NOT NULL,started_at TEXT,finished_at TEXT,error TEXT,progress_stage TEXT,progress_current INTEGER,progress_total INTEGER)")
    db.execSQL("CREATE INDEX IF NOT EXISTS summary_task_state ON summary_tasks(state,queued_at)")
    db.execSQL("CREATE TABLE IF NOT EXISTS mail_accounts(id TEXT PRIMARY KEY NOT NULL,email TEXT NOT NULL UNIQUE,display_name TEXT,last_history_id TEXT,last_synced_at INTEGER,sync_state TEXT NOT NULL DEFAULT 'idle',sync_processed_threads INTEGER NOT NULL DEFAULT 0,sync_error TEXT,sync_page_token TEXT,sync_start_history_id TEXT,sync_generation TEXT)")
    db.execSQL("CREATE TABLE IF NOT EXISTS mail_labels(account_id TEXT NOT NULL REFERENCES mail_accounts(id) ON DELETE CASCADE,id TEXT NOT NULL,name TEXT NOT NULL,type TEXT NOT NULL,PRIMARY KEY(account_id,id))")
    db.execSQL("CREATE TABLE IF NOT EXISTS mail_threads(account_id TEXT NOT NULL REFERENCES mail_accounts(id) ON DELETE CASCADE,id TEXT NOT NULL,subject TEXT NOT NULL,snippet TEXT NOT NULL,last_message_at INTEGER NOT NULL,message_count INTEGER NOT NULL,in_inbox INTEGER NOT NULL DEFAULT 0,is_unread INTEGER NOT NULL DEFAULT 0,is_starred INTEGER NOT NULL DEFAULT 0,archived_locally INTEGER NOT NULL DEFAULT 0,read_later_locally INTEGER NOT NULL DEFAULT 0,sync_generation TEXT,PRIMARY KEY(account_id,id))")
    db.execSQL("CREATE INDEX IF NOT EXISTS mail_threads_date ON mail_threads(last_message_at DESC)")
    db.execSQL("CREATE INDEX IF NOT EXISTS mail_threads_account_inbox ON mail_threads(account_id,in_inbox,last_message_at DESC)")
    db.execSQL("CREATE INDEX IF NOT EXISTS mail_threads_account_unread ON mail_threads(account_id,is_unread,last_message_at DESC)")
    db.execSQL("CREATE TABLE IF NOT EXISTS mail_messages(account_id TEXT NOT NULL REFERENCES mail_accounts(id) ON DELETE CASCADE,id TEXT NOT NULL,thread_id TEXT NOT NULL,sender TEXT NOT NULL,recipients TEXT NOT NULL,subject TEXT NOT NULL,snippet TEXT NOT NULL,body TEXT NOT NULL,html_body TEXT,received_at INTEGER NOT NULL,label_ids TEXT NOT NULL,is_unread INTEGER NOT NULL DEFAULT 0,is_starred INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(account_id,id))")
    db.execSQL("CREATE INDEX IF NOT EXISTS mail_messages_thread ON mail_messages(account_id,thread_id,received_at)")
  }

  private fun migrateToVersion6(db: SQLiteDatabase) {
    addColumnIfMissing(db, "mail_accounts", "sync_state", "sync_state TEXT NOT NULL DEFAULT 'idle'")
    addColumnIfMissing(
      db,
      "mail_accounts",
      "sync_processed_threads",
      "sync_processed_threads INTEGER NOT NULL DEFAULT 0",
    )
    addColumnIfMissing(db, "mail_accounts", "sync_error", "sync_error TEXT")
    addColumnIfMissing(db, "mail_accounts", "sync_page_token", "sync_page_token TEXT")
    addColumnIfMissing(db, "mail_accounts", "sync_start_history_id", "sync_start_history_id TEXT")
    addColumnIfMissing(db, "mail_accounts", "sync_generation", "sync_generation TEXT")
    addColumnIfMissing(db, "mail_threads", "sync_generation", "sync_generation TEXT")
  }

  private fun migrateToVersion7(db: SQLiteDatabase) {
    addColumnIfMissing(
      db,
      "mail_threads",
      "archived_locally",
      "archived_locally INTEGER NOT NULL DEFAULT 0",
    )
  }

  private fun migrateToVersion8(db: SQLiteDatabase) {
    // v7 changed the initial-sync query to in:inbox. Page tokens created by the old
    // unfiltered query cannot safely be reused with that query, so unfinished syncs
    // must restart from the first page. Cached mail is intentionally preserved.
    db.execSQL(
      """
        UPDATE mail_accounts
        SET sync_state = 'idle',
            sync_processed_threads = 0,
            sync_error = NULL,
            sync_page_token = NULL,
            sync_start_history_id = NULL,
            sync_generation = NULL
        WHERE last_history_id IS NULL
          AND (
            sync_page_token IS NOT NULL
            OR sync_generation IS NOT NULL
            OR sync_state IN ('syncing', 'waiting_for_network', 'error')
          )
      """.trimIndent(),
    )
  }

  private fun migrateToVersion9(db: SQLiteDatabase) {
    addColumnIfMissing(db, "mail_messages", "html_body", "html_body TEXT")
  }

  private fun migrateToVersion10(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS feed_folders(id TEXT PRIMARY KEY NOT NULL,name TEXT NOT NULL,normalized_name TEXT NOT NULL UNIQUE,created_at TEXT NOT NULL)")
    addColumnIfMissing(
      db,
      "feeds",
      "folder_id",
      "folder_id TEXT REFERENCES feed_folders(id) ON DELETE SET NULL",
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS feeds_folder_id ON feeds(folder_id,title)")
  }

  private fun migrateToVersion11(db: SQLiteDatabase) {
    addColumnIfMissing(
      db,
      "mail_threads",
      "read_later_locally",
      "read_later_locally INTEGER NOT NULL DEFAULT 0",
    )
  }

  private fun migrateToVersion12(db: SQLiteDatabase) {
    addColumnIfMissing(db, "summary_tasks", "progress_stage", "progress_stage TEXT")
    addColumnIfMissing(db, "summary_tasks", "progress_current", "progress_current INTEGER")
    addColumnIfMissing(db, "summary_tasks", "progress_total", "progress_total INTEGER")
  }

  private fun addColumnIfMissing(
    db: SQLiteDatabase,
    table: String,
    column: String,
    definition: String,
  ) {
    val exists = db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
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
    if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $definition")
  }

  companion object {
    const val DB_NAME = "yomitori-rss.db"
    private const val DB_VERSION = 12

    fun create(context: Context): YomitoriDatabase {
      val app = context.applicationContext
      val target = app.getDatabasePath(DB_NAME)
      val legacy = File(app.filesDir, "SQLite/$DB_NAME")
      if (!target.exists() && legacy.isFile) {
        target.parentFile?.mkdirs()
        legacy.copyTo(target)
        listOf("-wal", "-shm").forEach { suffix ->
          File(legacy.path + suffix).takeIf(File::isFile)?.copyTo(File(target.path + suffix), true)
        }
      }
      return YomitoriDatabase(app).also {
        it.setWriteAheadLoggingEnabled(true)
        it.writableDatabase
      }
    }
  }
}
