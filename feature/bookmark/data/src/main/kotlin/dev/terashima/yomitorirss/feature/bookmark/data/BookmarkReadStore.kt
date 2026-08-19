package dev.terashima.yomitorirss.feature.bookmark.data

import android.database.Cursor
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkFolder
import dev.terashima.yomitorirss.feature.bookmark.READ_LATER_FOLDER_ID
import dev.terashima.yomitorirss.feature.bookmark.Tag
import dev.terashima.yomitorirss.feature.bookmark.UNCATEGORIZED_FOLDER_ID

internal data class BookmarkRecord(
  val articleId: String,
  val savedAt: String,
  val tags: List<Tag>,
  val folder: BookmarkFolder?,
)

/** Curation-owned read model. Content details are composed by DefaultBookmarkRepository. */
internal class BookmarkReadStore(
  private val database: DatabaseConnection,
) {
  fun listSavedRecords(tagId: String?, folderId: String?): List<BookmarkRecord> {
    val clauses = mutableListOf<String>()
    val args = mutableListOf<String>()
    if (tagId != null) {
      clauses += "EXISTS(SELECT 1 FROM article_tags x WHERE x.article_id=b.article_id AND x.tag_id=?)"
      args += tagId
    }
    when (folderId) {
      null -> Unit
      UNCATEGORIZED_FOLDER_ID -> clauses += "NOT EXISTS(SELECT 1 FROM article_folders f WHERE f.article_id=b.article_id)"
      else -> {
        clauses += "EXISTS(SELECT 1 FROM article_folders f WHERE f.article_id=b.article_id AND f.folder_id=?)"
        args += folderId
      }
    }
    val where = clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND ")?.let { " WHERE $it" }.orEmpty()
    return bookmarkRecords(
      "SELECT b.article_id,b.saved_at FROM bookmarks b$where ORDER BY b.saved_at DESC",
      args.toTypedArray(),
    )
  }

  fun listAllSavedRecords(): List<BookmarkRecord> = buildList {
    var offset = 0
    while (true) {
      val page = bookmarkRecords(
        "SELECT article_id,saved_at FROM bookmarks ORDER BY saved_at DESC LIMIT ? OFFSET ?",
        arrayOf(ALL_SAVED_PAGE_SIZE.toString(), offset.toString()),
      )
      addAll(page)
      if (page.size < ALL_SAVED_PAGE_SIZE) break
      offset += page.size
    }
  }

  fun listReadLaterRecords(): List<BookmarkRecord> = bookmarkRecords(
    """
      SELECT b.article_id,b.saved_at
      FROM bookmarks b
      JOIN article_folders af ON af.article_id=b.article_id
      WHERE af.folder_id=?
      ORDER BY b.saved_at DESC
    """.trimIndent(),
    arrayOf(READ_LATER_FOLDER_ID),
  )

  private fun bookmarkRecords(sql: String, args: Array<String>): List<BookmarkRecord> {
    val rows = database.readable.rawQuery(sql, args).use { cursor ->
      buildList {
        while (cursor.moveToNext()) add(cursor.string("article_id") to cursor.string("saved_at"))
      }
    }
    if (rows.isEmpty()) return emptyList()

    val tags = mutableMapOf<String, MutableList<Tag>>()
    val folders = mutableMapOf<String, BookmarkFolder>()
    rows.map { it.first }.chunked(QUERY_CHUNK_SIZE).forEach { ids ->
      val placeholders = ids.joinToString(",") { "?" }
      database.readable.rawQuery(
        "SELECT x.article_id,t.* FROM article_tags x JOIN tags t ON t.id=x.tag_id WHERE x.article_id IN($placeholders) ORDER BY t.normalized_name",
        ids.toTypedArray(),
      ).use { cursor ->
        while (cursor.moveToNext()) tags.getOrPut(cursor.string("article_id")) { mutableListOf() }.add(cursor.tag())
      }
      database.readable.rawQuery(
        "SELECT x.article_id,f.* FROM article_folders x JOIN bookmark_folders f ON f.id=x.folder_id WHERE x.article_id IN($placeholders)",
        ids.toTypedArray(),
      ).use { cursor ->
        while (cursor.moveToNext()) folders[cursor.string("article_id")] = cursor.folder()
      }
    }

    return rows.map { (articleId, savedAt) ->
      BookmarkRecord(articleId, savedAt, tags[articleId].orEmpty(), folders[articleId])
    }
  }
}

private fun Cursor.tag(): Tag = Tag(
  id = string("id"),
  name = string("name"),
  normalizedName = string("normalized_name"),
  createdAt = string("created_at"),
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

private const val ALL_SAVED_PAGE_SIZE = 400
private const val QUERY_CHUNK_SIZE = 400
