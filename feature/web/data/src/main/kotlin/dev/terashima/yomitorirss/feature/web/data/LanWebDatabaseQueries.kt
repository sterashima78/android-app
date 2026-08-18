package dev.terashima.yomitorirss.feature.web.data

import android.database.Cursor
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.bookmark.BookmarkFolder
import dev.terashima.yomitorirss.feature.bookmark.BookmarkedArticle
import dev.terashima.yomitorirss.feature.bookmark.READ_LATER_FOLDER_ID
import dev.terashima.yomitorirss.feature.bookmark.Tag
import dev.terashima.yomitorirss.feature.rss.Feed

internal fun YomitoriDatabase.listUnreadArticles(): List<Article> = articles(
  "SELECT * FROM articles WHERE read_at IS NULL ORDER BY published_at DESC LIMIT 500",
)

internal fun YomitoriDatabase.listSavedArticles(): List<BookmarkedArticle> = bookmarkedArticles(
  "SELECT * FROM articles WHERE saved_at IS NOT NULL ORDER BY published_at DESC LIMIT 500",
)

internal fun YomitoriDatabase.listReadLaterArticles(): List<BookmarkedArticle> = bookmarkedArticles(
  "SELECT a.* FROM articles a JOIN article_folders f ON f.article_id=a.id WHERE a.saved_at IS NOT NULL AND f.folder_id=? ORDER BY a.published_at DESC",
  arrayOf(READ_LATER_FOLDER_ID),
)

internal fun YomitoriDatabase.listFeeds(): List<Feed> = readableDatabase.rawQuery(
  "SELECT * FROM feeds ORDER BY COALESCE(custom_title,title) COLLATE NOCASE",
  null,
).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.feed()) } }

private fun YomitoriDatabase.articles(sql: String, args: Array<String> = emptyArray()): List<Article> =
  readableDatabase.rawQuery(sql, args).use { cursor ->
    buildList { while (cursor.moveToNext()) add(cursor.article()) }
  }

private fun YomitoriDatabase.bookmarkedArticles(
  sql: String,
  args: Array<String> = emptyArray(),
): List<BookmarkedArticle> {
  val base = readableDatabase.rawQuery(sql, args).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(cursor.article() to cursor.text("saved_at"))
      }
    }
  }
  if (base.isEmpty()) return emptyList()

  val tags = mutableMapOf<String, MutableList<Tag>>()
  val folders = mutableMapOf<String, BookmarkFolder>()
  val placeholders = base.joinToString(",") { "?" }
  val ids = base.map { it.first.id }.toTypedArray()

  readableDatabase.rawQuery(
    "SELECT x.article_id,t.* FROM article_tags x JOIN tags t ON t.id=x.tag_id WHERE x.article_id IN($placeholders) ORDER BY t.normalized_name",
    ids,
  ).use { cursor ->
    while (cursor.moveToNext()) {
      tags.getOrPut(cursor.text("article_id")) { mutableListOf() }.add(cursor.tag())
    }
  }

  readableDatabase.rawQuery(
    "SELECT x.article_id,f.* FROM article_folders x JOIN bookmark_folders f ON f.id=x.folder_id WHERE x.article_id IN($placeholders)",
    ids,
  ).use { cursor ->
    while (cursor.moveToNext()) folders[cursor.text("article_id")] = cursor.folder()
  }

  return base.map { (article, savedAt) ->
    BookmarkedArticle(
      article = article,
      savedAt = savedAt,
      tags = tags[article.id].orEmpty(),
      folder = folders[article.id],
    )
  }
}

private fun Cursor.article(): Article = Article(
  id = text("id"),
  feedId = nullableText("feed_id"),
  externalId = nullableText("external_id"),
  identityKey = text("identity_key"),
  url = text("url"),
  title = text("title"),
  publishedAt = text("published_at"),
  fetchedAt = text("fetched_at"),
  readAt = nullableText("read_at"),
  sourceTitle = text("source_title"),
  sourceFeedUrl = text("source_feed_url"),
)

private fun Cursor.feed(): Feed = Feed(
  id = text("id"),
  title = nullableText("custom_title") ?: text("title"),
  feedUrl = text("feed_url"),
  siteUrl = nullableText("site_url"),
  etag = nullableText("etag"),
  lastModified = nullableText("last_modified"),
  lastFetchedAt = nullableText("last_fetched_at"),
  lastError = nullableText("last_error"),
  createdAt = text("created_at"),
)

private fun Cursor.tag(): Tag = Tag(
  id = text("id"),
  name = text("name"),
  normalizedName = text("normalized_name"),
  createdAt = text("created_at"),
)

private fun Cursor.folder(): BookmarkFolder = BookmarkFolder(
  id = text("id"),
  name = text("name"),
  normalizedName = text("normalized_name"),
  systemKind = nullableText("system_kind"),
  createdAt = text("created_at"),
)

private fun Cursor.text(name: String): String = getString(getColumnIndexOrThrow(name))
private fun Cursor.nullableText(name: String): String? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getString(index) }
