package dev.terashima.yomitorirss.feature.bookmark.data

import android.database.Cursor
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.resolveContentType
import dev.terashima.yomitorirss.feature.article.toContentTypeOrNull
import dev.terashima.yomitorirss.feature.bookmark.BookmarkFolder
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.bookmark.READ_LATER_FOLDER_ID
import dev.terashima.yomitorirss.feature.bookmark.Tag
import dev.terashima.yomitorirss.feature.bookmark.UNCATEGORIZED_FOLDER_ID

/** Read model for the bookmark screen. Mutation responsibilities live in dedicated stores. */
internal class BookmarkReadStore(
  private val database: DatabaseConnection,
) {
  fun listSavedArticles(tagId: String?, folderId: String?): List<BookmarkedArticle> {
    val clauses = mutableListOf("articles.saved_at IS NOT NULL")
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
      "$BOOKMARK_ARTICLE_SELECT WHERE ${clauses.joinToString(" AND ")} ORDER BY articles.published_at DESC LIMIT 500",
      args.toTypedArray(),
    )
  }

  fun listAllSavedArticles(): List<BookmarkedArticle> = buildList {
    var offset = 0
    while (true) {
      val page = bookmarkedArticles(
        "$BOOKMARK_ARTICLE_SELECT WHERE articles.saved_at IS NOT NULL ORDER BY articles.published_at DESC LIMIT ? OFFSET ?",
        arrayOf(ALL_SAVED_PAGE_SIZE.toString(), offset.toString()),
      )
      addAll(page)
      if (page.size < ALL_SAVED_PAGE_SIZE) break
      offset += page.size
    }
  }

  fun listReadLaterArticles(): List<BookmarkedArticle> = bookmarkedArticles(
    "$BOOKMARK_ARTICLE_SELECT WHERE articles.saved_at IS NOT NULL " +
      "AND EXISTS(SELECT 1 FROM article_folders f WHERE f.article_id=articles.id AND f.folder_id=?) " +
      "ORDER BY articles.published_at DESC",
    arrayOf(READ_LATER_FOLDER_ID),
  )

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
}

private fun Cursor.article(): Article {
  val articleOverride = nullableString("content_type").toContentTypeOrNull()
  val feedOverride = nullableString("feed_content_type").toContentTypeOrNull()
  val folderOverride = nullableString("folder_content_type").toContentTypeOrNull()
  return Article(
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
    contentTypeOverride = articleOverride,
    effectiveContentType = resolveContentType(articleOverride, feedOverride, folderOverride),
  )
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

private const val BOOKMARK_ARTICLE_SELECT = """
  SELECT articles.*, feeds.content_type AS feed_content_type, feed_folders.content_type AS folder_content_type
  FROM articles
  LEFT JOIN feeds ON feeds.id = articles.feed_id
  LEFT JOIN feed_folders ON feed_folders.id = feeds.folder_id
"""
private const val ALL_SAVED_PAGE_SIZE = 400
