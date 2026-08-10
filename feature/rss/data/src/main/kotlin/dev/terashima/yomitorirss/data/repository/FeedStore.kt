package dev.terashima.yomitorirss.feature.rss.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.rss.Feed
import dev.terashima.yomitorirss.feature.rss.data.network.ParsedFeed
import java.time.Instant
import java.util.UUID

internal class FeedStore(
  private val database: DatabaseConnection,
) {
  fun listFeeds(): List<Feed> = database.readable
    .rawQuery("SELECT * FROM feeds ORDER BY title COLLATE NOCASE", null)
    .use { cursor ->
      buildList {
        while (cursor.moveToNext()) add(cursor.feed())
      }
    }

  fun addFeed(
    parsed: ParsedFeed,
    etag: String?,
    modified: String?,
    markExistingArticlesRead: Boolean = false,
  ): Feed {
    val now = nowIso()
    val feed = Feed(
      id = UUID.randomUUID().toString(),
      title = parsed.title,
      feedUrl = parsed.feedUrl,
      siteUrl = parsed.siteUrl,
      etag = etag,
      lastModified = modified,
      lastFetchedAt = now,
      lastError = null,
      createdAt = now,
    )
    database.transaction {
      insertOrThrow("feeds", null, feed.values())
      upsertArticles(
        db = this,
        feed = feed,
        parsed = parsed,
        fetchedAt = now,
        insertedReadAt = now.takeIf { markExistingArticlesRead },
      )
    }
    return feed
  }

  fun updateFeedSuccess(feed: Feed, parsed: ParsedFeed, etag: String?, modified: String?) {
    database.transaction {
      val now = nowIso()
      update(
        "feeds",
        contentValues(
          "title" to parsed.title,
          "feed_url" to parsed.feedUrl,
          "site_url" to parsed.siteUrl,
          "etag" to etag,
          "last_modified" to modified,
          "last_fetched_at" to now,
          "last_error" to null,
        ),
        "id=?",
        arrayOf(feed.id),
      )
      upsertArticles(
        this,
        feed.copy(title = parsed.title, feedUrl = parsed.feedUrl, siteUrl = parsed.siteUrl),
        parsed,
        now,
      )
    }
  }

  fun updateFeedNotModified(id: String) {
    database.writable.update(
      "feeds",
      contentValues("last_fetched_at" to nowIso(), "last_error" to null),
      "id=?",
      arrayOf(id),
    )
  }

  fun updateFeedError(id: String, error: String) {
    database.writable.update(
      "feeds",
      contentValues("last_error" to error.take(500)),
      "id=?",
      arrayOf(id),
    )
  }

  fun deleteFeed(id: String) {
    database.transaction {
      delete("articles", "feed_id=? AND saved_at IS NULL", arrayOf(id))
      update("articles", contentValues("feed_id" to null), "feed_id=? AND saved_at IS NOT NULL", arrayOf(id))
      delete("feeds", "id=?", arrayOf(id))
    }
  }

  private fun upsertArticles(
    db: SQLiteDatabase,
    feed: Feed,
    parsed: ParsedFeed,
    fetchedAt: String,
    insertedReadAt: String? = null,
  ) {
    parsed.articles.take(500).forEach { article ->
      db.execSQL(
        "UPDATE articles SET feed_id=?,source_title=?,source_feed_url=? WHERE feed_id IS NULL AND saved_at IS NOT NULL AND(identity_key=? OR url=?)",
        arrayOf(feed.id, feed.title, feed.feedUrl, article.identityKey, article.url),
      )
      db.insertWithOnConflict(
        "articles",
        null,
        contentValues(
          "id" to UUID.randomUUID().toString(),
          "feed_id" to feed.id,
          "external_id" to article.externalId,
          "identity_key" to article.identityKey,
          "url" to article.url,
          "title" to article.title,
          "published_at" to article.publishedAt,
          "fetched_at" to fetchedAt,
          "read_at" to insertedReadAt,
          "source_title" to feed.title,
          "source_feed_url" to feed.feedUrl,
        ),
        SQLiteDatabase.CONFLICT_IGNORE,
      )
    }
  }
}

private fun Feed.values(): ContentValues = contentValues(
  "id" to id,
  "title" to title,
  "feed_url" to feedUrl,
  "site_url" to siteUrl,
  "etag" to etag,
  "last_modified" to lastModified,
  "last_fetched_at" to lastFetchedAt,
  "last_error" to lastError,
  "created_at" to createdAt,
)

private fun contentValues(vararg values: Pair<String, String?>): ContentValues = ContentValues().apply {
  values.forEach { (key, value) ->
    if (value == null) putNull(key) else put(key, value)
  }
}

private fun Cursor.feed(): Feed = Feed(
  id = string("id"),
  title = string("title"),
  feedUrl = string("feed_url"),
  siteUrl = nullableString("site_url"),
  etag = nullableString("etag"),
  lastModified = nullableString("last_modified"),
  lastFetchedAt = nullableString("last_fetched_at"),
  lastError = nullableString("last_error"),
  createdAt = string("created_at"),
)

private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))

private fun Cursor.nullableString(name: String): String? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getString(index) }

private fun nowIso(): String = Instant.now().toString()
