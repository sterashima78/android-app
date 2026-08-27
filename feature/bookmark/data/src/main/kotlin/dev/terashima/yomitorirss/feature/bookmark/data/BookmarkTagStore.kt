package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.Tag
import java.time.Instant
import java.util.UUID

/** Owns the tag catalog itself. Article/tag membership is handled by [BookmarkAssociationStore]. */
internal class BookmarkTagStore(
  private val database: DatabaseConnection,
) {
  fun listTags(): List<Tag> = database.readable
    .rawQuery("SELECT * FROM tags ORDER BY normalized_name", null)
    .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.tag()) } }

  fun createTag(name: String): Tag {
    val display = displayName(name)
    require(display.isNotBlank()) { "タグ名を入力してください" }
    val tag = Tag(UUID.randomUUID().toString(), display, normalizeName(display), nowIso())
    check(
      database.write {
        insertWithOnConflict("tags", null, tag.values(), SQLiteDatabase.CONFLICT_ABORT)
      } != -1L,
    ) { "同じ名前のタグがあります" }
    return tag
  }

  fun renameTag(id: String, name: String) {
    val display = displayName(name)
    require(display.isNotBlank()) { "タグ名を入力してください" }
    database.write {
      update(
        "tags",
        values("name" to display, "normalized_name" to normalizeName(display)),
        "id=?",
        arrayOf(id),
      )
    }
  }

  fun deleteTag(id: String) {
    database.write { delete("tags", "id=?", arrayOf(id)) }
  }

  fun deleteTags(ids: Set<String>): Int {
    if (ids.isEmpty()) return 0
    return database.transaction {
      ids.sumOf { id -> delete("tags", "id=?", arrayOf(id)) }
    }
  }
}

private fun Tag.values(): ContentValues = values(
  "id" to id,
  "name" to name,
  "normalized_name" to normalizedName,
  "created_at" to createdAt,
)

private fun Cursor.tag(): Tag = Tag(
  id = string("id"),
  name = string("name"),
  normalizedName = string("normalized_name"),
  createdAt = string("created_at"),
)

private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))

private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}

private fun displayName(name: String): String = name.trim().replace(Regex("\\s+"), " ")
private fun normalizeName(name: String): String = displayName(name).lowercase()
private fun nowIso(): String = Instant.now().toString()
