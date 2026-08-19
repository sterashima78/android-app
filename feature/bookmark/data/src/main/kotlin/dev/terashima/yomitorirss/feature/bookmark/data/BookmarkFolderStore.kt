package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkFolder
import dev.terashima.yomitorirss.feature.bookmark.READ_LATER_FOLDER_ID
import dev.terashima.yomitorirss.feature.bookmark.READ_LATER_FOLDER_KIND
import dev.terashima.yomitorirss.feature.bookmark.YOUTUBE_FOLDER_KIND
import dev.terashima.yomitorirss.feature.bookmark.YOUTUBE_FOLDER_NAME
import java.time.Instant
import java.util.UUID

/** Owns folder catalog lifecycle and system-folder invariants. */
internal class BookmarkFolderStore(
  private val database: DatabaseConnection,
) {
  fun listFolders(): List<BookmarkFolder> = database.readable.rawQuery(
    "SELECT * FROM bookmark_folders ORDER BY CASE WHEN system_kind=? THEN 0 ELSE 1 END, normalized_name",
    arrayOf(READ_LATER_FOLDER_KIND),
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.folder()) } }

  fun createFolder(name: String): BookmarkFolder {
    val display = displayName(name)
    require(display.isNotBlank()) { "フォルダ名を入力してください" }
    val folder = BookmarkFolder(
      id = UUID.randomUUID().toString(),
      name = display,
      normalizedName = normalizeName(display),
      systemKind = null,
      createdAt = nowIso(),
    )
    check(
      database.writable.insertWithOnConflict(
        "bookmark_folders",
        null,
        folder.values(),
        SQLiteDatabase.CONFLICT_ABORT,
      ) != -1L,
    ) { "同じ名前のフォルダがあります" }
    return folder
  }

  fun renameFolder(id: String, name: String) {
    val display = displayName(name)
    require(display.isNotBlank()) { "フォルダ名を入力してください" }
    requireFolderCanBeEdited(id)
    database.writable.update(
      "bookmark_folders",
      values("name" to display, "normalized_name" to normalizeName(display)),
      "id=?",
      arrayOf(id),
    )
  }

  fun deleteFolder(id: String) {
    requireFolderCanBeEdited(id)
    database.writable.delete("bookmark_folders", "id=?", arrayOf(id))
  }

  fun ensureYouTubeFolder() {
    val normalizedName = normalizeName(YOUTUBE_FOLDER_NAME)
    database.transaction {
      rawQuery(
        "SELECT id,name,system_kind FROM bookmark_folders WHERE normalized_name=? LIMIT 1",
        arrayOf(normalizedName),
      ).use { cursor ->
        if (cursor.moveToFirst()) {
          val id = cursor.getString(0)
          val name = cursor.getString(1)
          val systemKind = if (cursor.isNull(2)) null else cursor.getString(2)
          require(systemKind == null || systemKind == YOUTUBE_FOLDER_KIND) {
            "YouTubeフォルダ名は別のシステムフォルダで使用されています"
          }
          if (name != YOUTUBE_FOLDER_NAME || systemKind != YOUTUBE_FOLDER_KIND) {
            update(
              "bookmark_folders",
              values(
                "name" to YOUTUBE_FOLDER_NAME,
                "normalized_name" to normalizedName,
                "system_kind" to YOUTUBE_FOLDER_KIND,
              ),
              "id=?",
              arrayOf(id),
            )
          }
          return@transaction
        }
      }

      rawQuery(
        "SELECT id FROM bookmark_folders WHERE system_kind=? LIMIT 1",
        arrayOf(YOUTUBE_FOLDER_KIND),
      ).use { cursor ->
        if (cursor.moveToFirst()) {
          update(
            "bookmark_folders",
            values("name" to YOUTUBE_FOLDER_NAME, "normalized_name" to normalizedName),
            "id=?",
            arrayOf(cursor.getString(0)),
          )
          return@transaction
        }
      }

      insertOrThrow(
        "bookmark_folders",
        null,
        values(
          "id" to UUID.randomUUID().toString(),
          "name" to YOUTUBE_FOLDER_NAME,
          "normalized_name" to normalizedName,
          "system_kind" to YOUTUBE_FOLDER_KIND,
          "created_at" to nowIso(),
        ),
      )
    }
  }

  private fun requireFolderCanBeEdited(id: String) {
    database.readable.rawQuery("SELECT system_kind FROM bookmark_folders WHERE id=?", arrayOf(id)).use { cursor ->
      require(cursor.moveToFirst()) { "フォルダが見つかりません" }
      require(cursor.isNull(0)) { "システムフォルダは変更できません" }
    }
  }
}

object BookmarkDatabaseInitializer {
  fun initialize(database: DatabaseConnection) {
    database.transaction {
      ensureReadLaterFolder(this, nowIso())
    }
  }
}

internal fun ensureReadLaterFolder(database: SQLiteDatabase, createdAt: String) {
  database.insertWithOnConflict(
    "bookmark_folders",
    null,
    values(
      "id" to READ_LATER_FOLDER_ID,
      "name" to READ_LATER_FOLDER_NAME,
      "normalized_name" to normalizeName(READ_LATER_FOLDER_NAME),
      "system_kind" to READ_LATER_FOLDER_KIND,
      "created_at" to createdAt,
    ),
    SQLiteDatabase.CONFLICT_IGNORE,
  )
}

private fun BookmarkFolder.values(): ContentValues = values(
  "id" to id,
  "name" to name,
  "normalized_name" to normalizedName,
  "system_kind" to systemKind,
  "created_at" to createdAt,
)

private fun Cursor.folder(): BookmarkFolder = BookmarkFolder(
  id = string("id"),
  name = string("name"),
  normalizedName = string("normalized_name"),
  systemKind = nullableString("system_kind"),
  createdAt = string("created_at"),
)

private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))

private fun Cursor.nullableString(name: String): String? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getString(index) }

private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}

private fun displayName(name: String): String = name.trim().replace(Regex("\\s+"), " ")
private fun normalizeName(name: String): String = displayName(name).lowercase()
private fun nowIso(): String = Instant.now().toString()
private const val READ_LATER_FOLDER_NAME = "あとで読む"
