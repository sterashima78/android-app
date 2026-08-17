package dev.terashima.yomitorirss.feature.rss.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.toContentTypeOrNull
import dev.terashima.yomitorirss.feature.rss.Feed
import dev.terashima.yomitorirss.feature.rss.FeedFolder
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

  fun listFolders(): List<FeedFolder> = database.readable
    .rawQuery("SELECT * FROM feed_folders ORDER BY normalized_name", null)
    .use { cursor ->
      buildList {
        while (cursor.moveToNext()) add(cursor.feedFolder())
      }
    }

  fun addFeed(
    parsed: ParsedFeed,
    etag: String?,
    modified: String?,
    markExistingArticlesRead: Boolean = false,
    folderId: String? = null,
    contentTypeOverride: ContentType? = null,
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
      folderId = folderId,
      contentTypeOverride = contentTypeOverride,
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

  fun createFolder(name: String): FeedFolder {
    val display = displayName(name)
    require(display.isNotBlank()) { "フォルダ名を入力してください" }
    require(!folderNameExists(normalizeName(display))) { "同じ名前のフォルダがあります" }
    val folder = FeedFolder(
      id = UUID.randomUUID().toString(),
      name = display,
      normalizedName = normalizeName(display),
      createdAt = nowIso(),
    )
    database.writable.insertOrThrow("feed_folders", null, folder.values())
    return folder
  }

  fun ensureFolder(name: String): FeedFolder {
    val display = displayName(name)
    require(display.isNotBlank()) { "フォルダ名を入力してください" }
    database.readable.rawQuery(
      "SELECT * FROM feed_folders WHERE normalized_name=? LIMIT 1",
      arrayOf(normalizeName(display)),
    ).use { cursor ->
      if (cursor.moveToFirst()) return cursor.feedFolder()
    }
    return createFolder(display)
  }

  fun renameFolder(id: String, name: String) {
    val display = displayName(name)
    require(display.isNotBlank()) { "フォルダ名を入力してください" }
    val normalized = normalizeName(display)
    require(!folderNameExists(normalized, excludingId = id)) { "同じ名前のフォルダがあります" }
    val updated = database.writable.update(
      "feed_folders",
      contentValues("name" to display, "normalized_name" to normalized),
      "id=?",
      arrayOf(id),
    )
    require(updated > 0) { "フォルダが見つかりません" }
  }

  fun deleteFolder(id: String) {
    database.transaction {
      val inheritedType = rawQuery(
        "SELECT content_type FROM feed_folders WHERE id=? LIMIT 1",
        arrayOf(id),
      ).use { cursor ->
        require(cursor.moveToFirst()) { "フォルダが見つかりません" }
        if (cursor.isNull(0)) null else cursor.getString(0)
      }
      if (inheritedType != null) {
        update(
          "feeds",
          contentValues("content_type" to inheritedType),
          "folder_id=? AND content_type IS NULL",
          arrayOf(id),
        )
      }
      delete("feed_folders", "id=?", arrayOf(id))
    }
  }

  fun moveFeedToFolder(feedId: String, folderId: String?) {
    if (folderId != null) {
      database.readable.rawQuery("SELECT 1 FROM feed_folders WHERE id=? LIMIT 1", arrayOf(folderId)).use { cursor ->
        require(cursor.moveToFirst()) { "フォルダが見つかりません" }
      }
    }
    val updated = database.writable.update(
      "feeds",
      contentValues("folder_id" to folderId),
      "id=?",
      arrayOf(feedId),
    )
    require(updated > 0) { "フィードが見つかりません" }
  }

  fun setFeedContentType(feedId: String, contentType: ContentType?) {
    val updated = database.writable.update(
      "feeds",
      contentValues("content_type" to contentType?.name),
      "id=?",
      arrayOf(feedId),
    )
    require(updated > 0) { "フィードが見つかりません" }
  }

  fun setFolderContentType(folderId: String, contentType: ContentType?) {
    val updated = database.writable.update(
      "feed_folders",
      contentValues("content_type" to contentType?.name),
      "id=?",
      arrayOf(folderId),
    )
    require(updated > 0) { "フォルダが見つかりません" }
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
      val inheritedType = rawQuery(
        """
          SELECT COALESCE(f.content_type, ff.content_type)
          FROM feeds f
          LEFT JOIN feed_folders ff ON ff.id = f.folder_id
          WHERE f.id=?
          LIMIT 1
        """.trimIndent(),
        arrayOf(id),
      ).use { cursor ->
        require(cursor.moveToFirst()) { "フィードが見つかりません" }
        if (cursor.isNull(0)) null else cursor.getString(0)
      }
      if (inheritedType != null) {
        update(
          "articles",
          contentValues("content_type" to inheritedType),
          "feed_id=? AND saved_at IS NOT NULL AND content_type IS NULL",
          arrayOf(id),
        )
      }
      delete("articles", "feed_id=? AND saved_at IS NULL", arrayOf(id))
      update("articles", contentValues("feed_id" to null), "feed_id=? AND saved_at IS NOT NULL", arrayOf(id))
      delete("feeds", "id=?", arrayOf(id))
    }
  }

  private fun folderNameExists(normalizedName: String, excludingId: String? = null): Boolean {
    val sql = if (excludingId == null) {
      "SELECT 1 FROM feed_folders WHERE normalized_name=? LIMIT 1"
    } else {
      "SELECT 1 FROM feed_folders WHERE normalized_name=? AND id<>? LIMIT 1"
    }
    val args = if (excludingId == null) arrayOf(normalizedName) else arrayOf(normalizedName, excludingId)
    return database.readable.rawQuery(sql, args).use(Cursor::moveToFirst)
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
  "folder_id" to folderId,
  "content_type" to contentTypeOverride?.name,
)

private fun FeedFolder.values(): ContentValues = contentValues(
  "id" to id,
  "name" to name,
  "normalized_name" to normalizedName,
  "created_at" to createdAt,
  "content_type" to contentTypeOverride?.name,
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
  folderId = nullableString("folder_id"),
  contentTypeOverride = nullableString("content_type").toContentTypeOrNull(),
)

private fun Cursor.feedFolder(): FeedFolder = FeedFolder(
  id = string("id"),
  name = string("name"),
  normalizedName = string("normalized_name"),
  createdAt = string("created_at"),
  contentTypeOverride = nullableString("content_type").toContentTypeOrNull(),
)

private fun Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))

private fun Cursor.nullableString(name: String): String? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getString(index) }

private fun displayName(name: String): String = name.trim().replace(Regex("\\s+"), " ")
private fun normalizeName(name: String): String = displayName(name).lowercase()
private fun nowIso(): String = Instant.now().toString()
