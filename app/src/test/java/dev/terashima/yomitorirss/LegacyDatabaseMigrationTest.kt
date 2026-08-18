package dev.terashima.yomitorirss

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = YomitoriApplication::class)
class LegacyDatabaseMigrationTest {
  private lateinit var context: Context
  private var database: YomitoriDatabase? = null

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    DATABASE_NAMES.forEach { context.deleteDatabase(it) }
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @After
  fun tearDown() {
    database?.close()
    DATABASE_NAMES.forEach { context.deleteDatabase(it) }
    context.deleteDatabase(YomitoriDatabase.DB_NAME)
  }

  @Test
  fun `standalone databases are copied into the shared database and retired`() {
    createTaskDatabase(hasDescription = true)
    createChatDatabase()
    createYouTubeDatabase(hasWatchLater = true)

    val db = openAndMigrate()

    assertEquals("details", scalarString(db, "SELECT description FROM tasks WHERE id='task-child'"))
    assertEquals("hello", scalarString(db, "SELECT content FROM chat_messages WHERE id=7"))
    assertEquals(1, scalarInt(db, "SELECT is_watch_later FROM videos WHERE video_id='video-1'"))
    DATABASE_NAMES.forEach { name ->
      assertFalse("$name should be deleted after migration", context.getDatabasePath(name).exists())
    }
  }

  @Test
  fun `older standalone schemas receive defaults while migrating`() {
    createTaskDatabase(hasDescription = false)
    createYouTubeDatabase(hasWatchLater = false)

    val db = openAndMigrate()

    assertEquals("", scalarString(db, "SELECT description FROM tasks WHERE id='task-child'"))
    assertEquals(0, scalarInt(db, "SELECT is_watch_later FROM videos WHERE video_id='video-1'"))
    assertFalse(context.getDatabasePath(CHAT_DATABASE).exists())
    assertFalse(context.getDatabasePath(TASK_DATABASE).exists())
    assertFalse(context.getDatabasePath(YOUTUBE_DATABASE).exists())
  }

  private fun openAndMigrate(): SQLiteDatabase {
    val helper = YomitoriDatabase.create(context, appDatabaseSchema)
    database = helper
    LegacyDatabaseMigration.migrate(context, helper)
    return helper.writableDatabase
  }

  private fun createTaskDatabase(hasDescription: Boolean) {
    context.openOrCreateDatabase(TASK_DATABASE, Context.MODE_PRIVATE, null).use { db ->
      val descriptionColumn = if (hasDescription) ",description TEXT NOT NULL DEFAULT ''" else ""
      db.execSQL(
        "CREATE TABLE tasks(" +
          "id TEXT PRIMARY KEY NOT NULL," +
          "title TEXT NOT NULL" + descriptionColumn + "," +
          "parent_id TEXT REFERENCES tasks(id) ON DELETE CASCADE," +
          "due_date TEXT," +
          "completed_at TEXT," +
          "created_at TEXT NOT NULL," +
          "sort_order INTEGER NOT NULL)",
      )
      insertTask(db, "task-parent", null, hasDescription, "parent")
      insertTask(db, "task-child", "task-parent", hasDescription, "child")
    }
  }

  private fun insertTask(
    db: SQLiteDatabase,
    id: String,
    parentId: String?,
    hasDescription: Boolean,
    title: String,
  ) {
    db.insertOrThrow(
      "tasks",
      null,
      ContentValues().apply {
        put("id", id)
        put("title", title)
        if (hasDescription) put("description", if (id == "task-child") "details" else "")
        if (parentId == null) putNull("parent_id") else put("parent_id", parentId)
        putNull("due_date")
        putNull("completed_at")
        put("created_at", "2026-08-18T00:00:00Z")
        put("sort_order", if (parentId == null) 0 else 1)
      },
    )
  }

  private fun createChatDatabase() {
    context.openOrCreateDatabase(CHAT_DATABASE, Context.MODE_PRIVATE, null).use { db ->
      db.execSQL(
        "CREATE TABLE chat_sessions(" +
          "id TEXT PRIMARY KEY NOT NULL," +
          "title TEXT NOT NULL," +
          "created_at TEXT NOT NULL," +
          "updated_at TEXT NOT NULL)",
      )
      db.execSQL(
        "CREATE TABLE chat_messages(" +
          "id INTEGER PRIMARY KEY AUTOINCREMENT," +
          "session_id TEXT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE," +
          "role TEXT NOT NULL," +
          "content TEXT NOT NULL," +
          "created_at TEXT NOT NULL)",
      )
      db.execSQL(
        "INSERT INTO chat_sessions(id,title,created_at,updated_at) " +
          "VALUES('chat-1','sample','2026-08-18T00:00:00Z','2026-08-18T00:00:00Z')",
      )
      db.execSQL(
        "INSERT INTO chat_messages(id,session_id,role,content,created_at) " +
          "VALUES(7,'chat-1','USER','hello','2026-08-18T00:00:01Z')",
      )
    }
  }

  private fun createYouTubeDatabase(hasWatchLater: Boolean) {
    context.openOrCreateDatabase(YOUTUBE_DATABASE, Context.MODE_PRIVATE, null).use { db ->
      db.execSQL(
        "CREATE TABLE channels(" +
          "channel_id TEXT PRIMARY KEY NOT NULL," +
          "title TEXT NOT NULL," +
          "channel_url TEXT NOT NULL," +
          "added_at INTEGER NOT NULL)",
      )
      val watchLaterColumn = if (hasWatchLater) ",is_watch_later INTEGER NOT NULL DEFAULT 0" else ""
      db.execSQL(
        "CREATE TABLE videos(" +
          "video_id TEXT PRIMARY KEY NOT NULL," +
          "channel_id TEXT NOT NULL," +
          "title TEXT NOT NULL," +
          "video_url TEXT NOT NULL," +
          "published_at INTEGER NOT NULL," +
          "is_read INTEGER NOT NULL DEFAULT 0" + watchLaterColumn + "," +
          "FOREIGN KEY(channel_id) REFERENCES channels(channel_id) ON DELETE CASCADE)",
      )
      db.execSQL(
        "INSERT INTO channels(channel_id,title,channel_url,added_at) " +
          "VALUES('channel-1','Channel','https://www.youtube.com/channel/channel-1',1)",
      )
      val values = ContentValues().apply {
        put("video_id", "video-1")
        put("channel_id", "channel-1")
        put("title", "Video")
        put("video_url", "https://www.youtube.com/watch?v=video-1")
        put("published_at", 1L)
        put("is_read", 0)
        if (hasWatchLater) put("is_watch_later", 1)
      }
      db.insertOrThrow("videos", null, values)
    }
  }

  private fun scalarString(db: SQLiteDatabase, sql: String): String = db.rawQuery(sql, null).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getString(0)
  }

  private fun scalarInt(db: SQLiteDatabase, sql: String): Int = db.rawQuery(sql, null).use { cursor ->
    check(cursor.moveToFirst())
    cursor.getInt(0)
  }

  private companion object {
    const val TASK_DATABASE = "yomitori-tasks.db"
    const val CHAT_DATABASE = "yomitori-chat.db"
    const val YOUTUBE_DATABASE = "youtube.db"
    val DATABASE_NAMES = listOf(TASK_DATABASE, CHAT_DATABASE, YOUTUBE_DATABASE)
  }
}
