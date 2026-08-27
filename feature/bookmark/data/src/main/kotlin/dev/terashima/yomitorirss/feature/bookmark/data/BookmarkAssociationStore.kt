package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.READ_LATER_FOLDER_ID
import dev.terashima.yomitorirss.feature.bookmark.UNCATEGORIZED_FOLDER_ID
import java.time.Instant
import java.util.UUID

/** Owns article-to-Curation associations such as tags, folders, and read-later membership. */
internal class BookmarkAssociationStore(
  private val database: DatabaseConnection,
) {
  fun listAssociatedTagIds(articleIds: Set<String>): Set<String> {
    if (articleIds.isEmpty()) return emptySet()
    return buildSet {
      articleIds.chunked(ASSOCIATION_QUERY_CHUNK_SIZE).forEach { ids ->
        val placeholders = ids.joinToString(",") { "?" }
        database.readable.rawQuery(
          "SELECT DISTINCT tag_id FROM article_tags WHERE article_id IN($placeholders)",
          ids.toTypedArray(),
        ).use { cursor ->
          while (cursor.moveToNext()) add(cursor.getString(0))
        }
      }
    }
  }

  fun replaceArticleTags(articleId: String, tagIds: Set<String>) {
    database.transaction {
      val existingTagIds = rawQuery(
        "SELECT tag_id FROM article_tags WHERE article_id=?",
        arrayOf(articleId),
      ).use { cursor ->
        buildSet {
          while (cursor.moveToNext()) add(cursor.getString(0))
        }
      }

      (existingTagIds - tagIds).forEach { tagId ->
        delete(
          "article_tags",
          "article_id=? AND tag_id=?",
          arrayOf(articleId, tagId),
        )
      }
      (tagIds - existingTagIds).forEach { tagId ->
        insertWithOnConflict(
          "article_tags",
          null,
          values("article_id" to articleId, "tag_id" to tagId),
          SQLiteDatabase.CONFLICT_IGNORE,
        )
      }
    }
  }

  fun moveArticleToFolder(articleId: String, folderId: String?) {
    database.transaction {
      if (folderId == null || folderId == UNCATEGORIZED_FOLDER_ID) {
        delete("article_folders", "article_id=?", arrayOf(articleId))
      } else {
        rawQuery("SELECT id FROM bookmark_folders WHERE id=?", arrayOf(folderId)).use { cursor ->
          require(cursor.moveToFirst()) { "フォルダが見つかりません" }
        }
        insertWithOnConflict(
          "article_folders",
          null,
          values("article_id" to articleId, "folder_id" to folderId),
          SQLiteDatabase.CONFLICT_REPLACE,
        )
      }
    }
  }

  fun addReadLater(articleId: String) {
    database.transaction {
      val now = nowIso()
      ensureReadLaterFolder(this, now)
      insertWithOnConflict(
        "article_folders",
        null,
        values("article_id" to articleId, "folder_id" to READ_LATER_FOLDER_ID),
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
  }

  fun clearArticleAssociations(articleId: String) {
    database.transaction {
      delete("article_tags", "article_id=?", arrayOf(articleId))
      delete("article_folders", "article_id=?", arrayOf(articleId))
    }
  }

  fun removeReadLater(articleId: String) {
    database.write {
      delete(
        "article_folders",
        "article_id=? AND folder_id=?",
        arrayOf(articleId, READ_LATER_FOLDER_ID),
      )
    }
  }

  fun addImportedTags(articleId: String, tagNames: List<String>) {
    if (tagNames.isEmpty()) return
    database.transaction {
      val importedAt = nowIso()
      tagNames.forEach { tagName ->
        insertWithOnConflict(
          "article_tags",
          null,
          values(
            "article_id" to articleId,
            "tag_id" to ensureImportedTag(this, tagName, importedAt),
          ),
          SQLiteDatabase.CONFLICT_IGNORE,
        )
      }
    }
  }
}

private fun ensureImportedTag(database: SQLiteDatabase, name: String, createdAt: String): String {
  val display = displayName(name)
  val normalized = normalizeName(display)
  database.rawQuery("SELECT id FROM tags WHERE normalized_name=?", arrayOf(normalized)).use { cursor ->
    if (cursor.moveToFirst()) return cursor.getString(0)
  }
  return UUID.randomUUID().toString().also { id ->
    database.insertOrThrow(
      "tags",
      null,
      values("id" to id, "name" to display, "normalized_name" to normalized, "created_at" to createdAt),
    )
  }
}

private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}

private fun displayName(name: String): String = name.trim().replace(Regex("\\s+"), " ")
private fun normalizeName(name: String): String = displayName(name).lowercase()
private fun nowIso(): String = Instant.now().toString()
private const val ASSOCIATION_QUERY_CHUNK_SIZE = 400
