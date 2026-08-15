package dev.terashima.yomitorirss.feature.bookmark.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkFolder
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportResult
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.bookmark.READ_LATER_FOLDER_ID
import dev.terashima.yomitorirss.feature.bookmark.READ_LATER_FOLDER_KIND
import dev.terashima.yomitorirss.feature.bookmark.Tag
import dev.terashima.yomitorirss.feature.bookmark.UNCATEGORIZED_FOLDER_ID
import java.time.Instant
import java.util.UUID

internal data class ImportedBookmarkEntry(
  val title: String,
  val url: String,
  val createdAt: String,
  val sourceTitle: String,
  val tagNames: List<String>,
)

internal class BookmarkStore(
  private val database: DatabaseConnection,
) {
  fun listSavedArticles(tagId: String?, folderId: String?): List<BookmarkedArticle> {
    val clauses = mutableListOf("saved_at IS NOT NULL")
    val args = mutableListOf<String>()
    if (tagId != null) {
      clauses += "EXISTS(SELECT 1 FROM article_tags x WHERE x.article_id=articles.id AND x.tag_id=?)"
      args += tagId
    }
    when (folderId) {
      null -> Unit
      UNCATEGORIZED_FOLDER_ID -> clauses += "NOT EXISTS(SELECT 1 FROM article_folders f WHERE f.article_id=articles.id)"
      else -> {
        clauses += "EXISTS(SELECT 1 FROM article_folders f WHERE f.article_id=articles.id AND f.folder_id=?)"
        args += folderId
      }
    }
    return bookmarkedArticles(
      "SELECT * FROM articles WHERE ${clauses.joinToString(" AND ")} ORDER BY published_at DESC LIMIT 500",
      args.toTypedArray(),
    )
  }

  fun listAllSavedArticles(): List<BookmarkedArticle> = buildList {
    var offset = 0
    while (true) {
      val page = bookmarkedArticles(
        "SELECT * FROM articles WHERE saved_at IS NOT NULL ORDER BY published_at DESC LIMIT ? OFFSET ?",
        arrayOf(ALL_SAVED_PAGE_SIZE.toString(), offset.toString()),
      )
      addAll(page)
      if (page.size < ALL_SAVED_PAGE_SIZE) break
      offset += page.size
    }
  }

  fun listReadLaterArticles(): List<BookmarkedArticle> = bookmarkedArticles(
    "SELECT a.* FROM articles a JOIN article_folders f ON f.article_id=a.id WHERE a.saved_at IS NOT NULL AND f.folder_id=? ORDER BY a.published_at DESC",
    arrayOf(READ_LATER_FOLDER_ID),
  )

  fun isBookmarked(articleId: String): Boolean = database.readable.rawQuery(
    "SELECT 1 FROM articles WHERE id=? AND saved_at IS NOT NULL LIMIT 1",
    arrayOf(articleId),
  ).use(Cursor::moveToFirst)

  fun listTags(): List<Tag> = database.readable
    .rawQuery("SELECT * FROM tags ORDER BY normalized_name", null)
    .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.tag()) } }

  fun listFolders(): List<BookmarkFolder> = database.readable.rawQuery(
    "SELECT * FROM bookmark_folders ORDER BY CASE WHEN system_kind=? THEN 0 ELSE 1 END, normalized_name",
    arrayOf(READ_LATER_FOLDER_KIND),
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.folder()) } }

  fun createTag(name: String): Tag {
    val display = displayName(name)
    require(display.isNotBlank()) { "タグ名を入力してください" }
    val tag = Tag(UUID.randomUUID().toString(), display, normalizeName(display), nowIso())
    check(
      database.writable.insertWithOnConflict("tags", null, tag.values(), SQLiteDatabase.CONFLICT_ABORT) != -1L,
    ) { "同じ名前のタグがあります" }
    return tag
  }

  fun renameTag(id: String, name: String) {
    val display = displayName(name)
    require(display.isNotBlank()) { "タグ名を入力してください" }
    database.writable.update(
      "tags",
      values("name" to display, "normalized_name" to normalizeName(display)),
      "id=?",
      arrayOf(id),
    )
  }

  fun deleteTag(id: String) {
    database.writable.delete("tags", "id=?", arrayOf(id))
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

  fun moveArticleToFolder(articleId: String, folderId: String?) {
    database.transaction {
      rawQuery("SELECT saved_at FROM articles WHERE id=?", arrayOf(articleId)).use { cursor ->
        require(cursor.moveToFirst() && !cursor.isNull(0)) { "ブックマークされていない記事です" }
      }
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

  fun saveAndReadArticle(articleId: String) {
    val now = nowIso()
    database.writable.update("articles", values("read_at" to now, "saved_at" to now), "id=?", arrayOf(articleId))
  }

  fun markReadLater(articleId: String) {
    database.transaction {
      val now = nowIso()
      update("articles", values("read_at" to now, "saved_at" to now), "id=?", arrayOf(articleId))
      ensureReadLaterFolder(this, now)
      insertWithOnConflict(
        "article_folders",
        null,
        values("article_id" to articleId, "folder_id" to READ_LATER_FOLDER_ID),
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
  }

  fun unsaveArticle(articleId: String) {
    database.transaction {
      delete("article_tags", "article_id=?", arrayOf(articleId))
      delete("article_folders", "article_id=?", arrayOf(articleId))
      update("articles", values("saved_at" to null), "id=?", arrayOf(articleId))
    }
  }

  fun removeReadLater(articleId: String) {
    database.writable.delete(
      "article_folders",
      "article_id=? AND folder_id=?",
      arrayOf(articleId, READ_LATER_FOLDER_ID),
    )
  }

  fun saveSharedArticle(url: String, title: String, sourceTitle: String): BookmarkSaveResult =
    database.transaction {
      val now = nowIso()
      var existingId: String? = null
      var alreadyBookmarked = false
      rawQuery(
        "SELECT id,saved_at FROM articles WHERE url=? ORDER BY CASE WHEN saved_at IS NULL THEN 1 ELSE 0 END,fetched_at DESC LIMIT 1",
        arrayOf(url),
      ).use { cursor ->
        if (cursor.moveToFirst()) {
          existingId = cursor.getString(0)
          alreadyBookmarked = !cursor.isNull(1)
        }
      }

      val articleId = existingId
      if (articleId == null) {
        insertOrThrow(
          "articles",
          null,
          values(
            "id" to UUID.randomUUID().toString(),
            "feed_id" to null,
            "external_id" to null,
            "identity_key" to "shared:$url",
            "url" to url,
            "title" to title,
            "published_at" to now,
            "fetched_at" to now,
            "read_at" to now,
            "saved_at" to now,
            "source_title" to sourceTitle,
            "source_feed_url" to "",
          ),
        )
      } else if (!alreadyBookmarked) {
        update("articles", values("read_at" to now, "saved_at" to now), "id=?", arrayOf(articleId))
      }

      if (alreadyBookmarked) BookmarkSaveResult.ALREADY_BOOKMARKED else BookmarkSaveResult.ADDED
    }

  fun importBookmarks(
    entries: List<ImportedBookmarkEntry>,
    skipped: Int,
    identityPrefix: String,
  ): BookmarkImportResult = database.transaction {
    val importedAt = nowIso()
    var added = 0
    var duplicates = 0

    entries.forEach { entry ->
      var articleId: String? = null
      var savedAt: String? = null
      var readAt: String? = null
      rawQuery(
        "SELECT id,saved_at,read_at FROM articles WHERE url=? ORDER BY CASE WHEN saved_at IS NULL THEN 1 ELSE 0 END,fetched_at DESC LIMIT 1",
        arrayOf(entry.url),
      ).use { cursor ->
        if (cursor.moveToFirst()) {
          articleId = cursor.getString(0)
          savedAt = if (cursor.isNull(1)) null else cursor.getString(1)
          readAt = if (cursor.isNull(2)) null else cursor.getString(2)
        }
      }

      val targetArticleId = articleId ?: UUID.randomUUID().toString().also { id ->
        insertOrThrow(
          "articles",
          null,
          values(
            "id" to id,
            "feed_id" to null,
            "external_id" to null,
            "identity_key" to "$identityPrefix:${entry.url}",
            "url" to entry.url,
            "title" to entry.title,
            "published_at" to entry.createdAt,
            "fetched_at" to importedAt,
            "read_at" to entry.createdAt,
            "saved_at" to entry.createdAt,
            "source_title" to entry.sourceTitle,
            "source_feed_url" to "",
          ),
        )
        added += 1
      }

      if (articleId != null) {
        if (savedAt == null) {
          update(
            "articles",
            values("read_at" to entry.createdAt, "saved_at" to entry.createdAt),
            "id=?",
            arrayOf(targetArticleId),
          )
          added += 1
        } else {
          duplicates += 1
          if (readAt == null) {
            update(
              "articles",
              values("read_at" to (savedAt ?: entry.createdAt)),
              "id=?",
              arrayOf(targetArticleId),
            )
          }
        }
      }

      entry.tagNames.forEach { tagName ->
        insertWithOnConflict(
          "article_tags",
          null,
          values(
            "article_id" to targetArticleId,
            "tag_id" to ensureImportedTag(this, tagName, importedAt),
          ),
          SQLiteDatabase.CONFLICT_IGNORE,
        )
      }
    }

    BookmarkImportResult(added = added, duplicates = duplicates, skipped = skipped)
  }

  private fun bookmarkedArticles(sql: String, args: Array<String> = emptyArray()): List<BookmarkedArticle> {
    val rows = database.readable.rawQuery(sql, args).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(cursor.article() to cursor.string("saved_at"))
        }
      }
    }
    if (rows.isEmpty()) return emptyList()

    val articles = rows.map { it.first }
    val tags = mutableMapOf<String, MutableList<Tag>>()
    val folders = mutableMapOf<String, BookmarkFolder>()
    val placeholders = articles.joinToString(",") { "?" }
    val ids = articles.map(Article::id).toTypedArray()

    database.readable.rawQuery(
      "SELECT x.article_id,t.* FROM article_tags x JOIN tags t ON t.id=x.tag_id WHERE x.article_id IN($placeholders) ORDER BY t.normalized_name",
      ids,
    ).use { cursor ->
      while (cursor.moveToNext()) tags.getOrPut(cursor.string("article_id")) { mutableListOf() }.add(cursor.tag())
    }

    database.readable.rawQuery(
      "SELECT x.article_id,f.* FROM article_folders x JOIN bookmark_folders f ON f.id=x.folder_id WHERE x.article_id IN($placeholders)",
      ids,
    ).use { cursor ->
      while (cursor.moveToNext()) folders[cursor.string("article_id")] = cursor.folder()
    }

    return rows.map { (article, savedAt) ->
      BookmarkedArticle(
        article = article,
        savedAt = savedAt,
        tags = tags[article.id].orEmpty(),
        folder = folders[article.id],
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

private fun ensureReadLaterFolder(database: SQLiteDatabase, createdAt: String) {
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

private fun Tag.values(): ContentValues = values(
  "id" to id,
  "name" to name,
  "normalized_name" to normalizedName,
  "created_at" to createdAt,
)

private fun BookmarkFolder.values(): ContentValues = values(
  "id" to id,
  "name" to name,
  "normalized_name" to normalizedName,
  "system_kind" to systemKind,
  "created_at" to createdAt,
)

private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}

private fun Cursor.article(): Article = Article(
  id = string("id"),
  feedId = nullableString("feed_id"),
  externalId = nullableString("external_id"),
  identityKey = string("identity_key"),
  url = string("url"),
  title = string("title"),
  publishedAt = string("published_at"),
  fetchedAt = string("fetched_at"),
  readAt = nullableString("read_at"),
  sourceTitle = string("source_title"),
  sourceFeedUrl = string("source_feed_url"),
)

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

private fun displayName(name: String): String = name.trim().replace(Regex("\\s+"), " ")
private fun normalizeName(name: String): String = displayName(name).lowercase()
private fun nowIso(): String = Instant.now().toString()

private const val READ_LATER_FOLDER_NAME = "あとで読む"
private const val ALL_SAVED_PAGE_SIZE = 400
