package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEnrichmentContext
import dev.terashima.yomitorirss.feature.bookmark.BookmarkEnrichmentRepository
import java.time.Instant
import java.util.UUID

class DefaultBookmarkEnrichmentRepository(
  private val database: DatabaseConnection,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier(),
) : BookmarkEnrichmentRepository {
  override suspend fun context(articleId: String): BookmarkEnrichmentContext? {
    if (!database.readable.isBookmarked(articleId)) return null
    val existingFolders = if (database.readable.isUncategorizedBookmark(articleId)) {
      database.readable.listExistingFolderNames()
    } else {
      emptyList()
    }
    return BookmarkEnrichmentContext(
      existingTagNames = database.readable.listExistingTagNames(),
      existingFolderNames = existingFolders,
    )
  }

  override suspend fun applyGeneratedMetadata(
    articleId: String,
    tagNames: List<String>,
    folderName: String?,
  ): Boolean {
    if (tagNames.isEmpty() && folderName == null) return false
    val changed = database.transaction {
      if (!isBookmarked(articleId)) return@transaction false
      var changed = addGeneratedTags(articleId, tagNames)
      if (folderName != null && isUncategorizedBookmark(articleId)) {
        if (assignExistingFolder(articleId, folderName)) changed = true
      }
      changed
    }
    if (changed) dataChanges.notifyChanged()
    return changed
  }
}

private fun SQLiteDatabase.listExistingTagNames(): List<String> = rawQuery(
  """
    SELECT t.name
    FROM tags t
    LEFT JOIN article_tags x ON x.tag_id=t.id
    GROUP BY t.id,t.name,t.normalized_name
    ORDER BY COUNT(x.article_id) DESC,t.normalized_name
    LIMIT ?
  """.trimIndent(),
  arrayOf(MAX_EXISTING_TAGS.toString()),
).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

private fun SQLiteDatabase.listExistingFolderNames(): List<String> = rawQuery(
  "SELECT name FROM bookmark_folders WHERE system_kind IS NULL ORDER BY normalized_name LIMIT ?",
  arrayOf(MAX_EXISTING_FOLDERS.toString()),
).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

private fun SQLiteDatabase.isBookmarked(articleId: String): Boolean = rawQuery(
  "SELECT 1 FROM bookmarks WHERE article_id=? LIMIT 1",
  arrayOf(articleId),
).use { cursor -> cursor.moveToFirst() }

private fun SQLiteDatabase.isUncategorizedBookmark(articleId: String): Boolean = rawQuery(
  """
    SELECT 1
    FROM bookmarks b
    WHERE b.article_id=?
      AND NOT EXISTS(SELECT 1 FROM article_folders f WHERE f.article_id=b.article_id)
    LIMIT 1
  """.trimIndent(),
  arrayOf(articleId),
).use { cursor -> cursor.moveToFirst() }

private fun SQLiteDatabase.addGeneratedTags(articleId: String, tagNames: List<String>): Boolean {
  var changed = false
  tagNames.forEach { rawName ->
    val display = displayName(rawName)
    if (display.isBlank()) return@forEach
    val normalized = normalizeName(display)
    val tagId = rawQuery(
      "SELECT id FROM tags WHERE normalized_name=? LIMIT 1",
      arrayOf(normalized),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
      ?: UUID.randomUUID().toString().also { id ->
        insertWithOnConflict(
          "tags",
          null,
          ContentValues().apply {
            put("id", id)
            put("name", display)
            put("normalized_name", normalized)
            put("created_at", Instant.now().toString())
          },
          SQLiteDatabase.CONFLICT_IGNORE,
        )
      }.let { candidateId ->
        rawQuery("SELECT id FROM tags WHERE normalized_name=? LIMIT 1", arrayOf(normalized)).use { cursor ->
          if (cursor.moveToFirst()) cursor.getString(0) else candidateId
        }
      }

    if (insertWithOnConflict(
        "article_tags",
        null,
        ContentValues().apply {
          put("article_id", articleId)
          put("tag_id", tagId)
        },
        SQLiteDatabase.CONFLICT_IGNORE,
      ) != -1L
    ) changed = true
  }
  return changed
}

private fun SQLiteDatabase.assignExistingFolder(articleId: String, folderName: String): Boolean {
  val folderId = rawQuery(
    "SELECT id FROM bookmark_folders WHERE system_kind IS NULL AND normalized_name=? LIMIT 1",
    arrayOf(normalizeName(folderName)),
  ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null } ?: return false
  return insertWithOnConflict(
    "article_folders",
    null,
    ContentValues().apply {
      put("article_id", articleId)
      put("folder_id", folderId)
    },
    SQLiteDatabase.CONFLICT_IGNORE,
  ) != -1L
}

private fun displayName(name: String): String = name.trim().replace(Regex("\\s+"), " ")
private fun normalizeName(name: String): String = displayName(name).lowercase()
private const val MAX_EXISTING_TAGS = 100
private const val MAX_EXISTING_FOLDERS = 100
