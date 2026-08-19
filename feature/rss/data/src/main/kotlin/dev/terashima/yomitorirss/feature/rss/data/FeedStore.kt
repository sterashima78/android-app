package dev.terashima.yomitorirss.feature.rss.data

import android.content.ContentValues
import android.database.Cursor
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ContentSourceGateway
import dev.terashima.yomitorirss.feature.article.ContentSourceSnapshot
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.SourceContentItem
import dev.terashima.yomitorirss.feature.article.toContentTypeOrNull
import dev.terashima.yomitorirss.feature.rss.Feed
import dev.terashima.yomitorirss.feature.rss.FeedFolder
import dev.terashima.yomitorirss.feature.rss.data.network.ParsedFeed
import java.time.Instant
import java.util.UUID

internal class FeedStore(
  private val database: DatabaseConnection,
  private val contentSourceGateway: ContentSourceGateway,
) {
  fun listFeeds(): List<Feed> = database.readable.rawQuery(
    "SELECT * FROM feeds ORDER BY COALESCE(custom_title,title) COLLATE NOCASE",
    null,
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.feed()) } }

  fun listFolders(): List<FeedFolder> = database.readable.rawQuery(
    "SELECT * FROM feed_folders ORDER BY normalized_name",
    null,
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.feedFolder()) } }

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
    database.writable.insertOrThrow("feeds", null, feed.values())
    contentSourceGateway.upsertSourceContent(
      source = feed.contentSourceSnapshot(),
      items = parsed.contentItems(),
      fetchedAt = now,
      insertedReadAt = now.takeIf { markExistingArticlesRead },
    )
    return feed
  }

  fun renameFeed(id: String, name: String) {
    val display = displayName(name)
    require(display.isNotBlank()) { "フィード名を入力してください" }
    val updated = database.writable.update("feeds", contentValues("custom_title" to display), "id=?", arrayOf(id))
    require(updated > 0) { "フィードが見つかりません" }
    contentSourceGateway.renameSourceContent(id, display)
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
    ).use { cursor -> if (cursor.moveToFirst()) return cursor.feedFolder() }
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
      val inheritedType = rawQuery("SELECT content_type FROM feed_folders WHERE id=? LIMIT 1", arrayOf(id)).use { cursor ->
        require(cursor.moveToFirst()) { "フォルダが見つかりません" }
        if (cursor.isNull(0)) null else cursor.getString(0)
      }
      if (inheritedType != null) {
        update("feeds", contentValues("content_type" to inheritedType), "folder_id=? AND content_type IS NULL", arrayOf(id))
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
    val updated = database.writable.update("feeds", contentValues("folder_id" to folderId), "id=?", arrayOf(feedId))
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
    val now = nowIso()
    database.writable.update(
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
    val displayTitle = database.readable.rawQuery(
      "SELECT COALESCE(custom_title,title) FROM feeds WHERE id=? LIMIT 1",
      arrayOf(feed.id),
    ).use { cursor ->
      require(cursor.moveToFirst()) { "フィードが見つかりません" }
      cursor.getString(0)
    }
    val updatedFeed = feed.copy(title = displayTitle, feedUrl = parsed.feedUrl, siteUrl = parsed.siteUrl)
    contentSourceGateway.upsertSourceContent(updatedFeed.contentSourceSnapshot(), parsed.contentItems(), now)
  }

  fun updateFeedNotModified(id: String) {
    database.writable.update("feeds", contentValues("last_fetched_at" to nowIso(), "last_error" to null), "id=?", arrayOf(id))
  }

  fun updateFeedError(id: String, error: String) {
    database.writable.update("feeds", contentValues("last_error" to error.take(500)), "id=?", arrayOf(id))
  }

  fun deleteFeed(id: String) {
    val inheritedType = database.readable.rawQuery(
      """
        SELECT COALESCE(f.content_type,ff.content_type)
        FROM feeds f
        LEFT JOIN feed_folders ff ON ff.id=f.folder_id
        WHERE f.id=? LIMIT 1
      """.trimIndent(),
      arrayOf(id),
    ).use { cursor ->
      require(cursor.moveToFirst()) { "フィードが見つかりません" }
      if (cursor.isNull(0)) null else cursor.getString(0).toContentTypeOrNull()
    }
    contentSourceGateway.detachSourceContent(id, inheritedType)
    database.writable.delete("feeds", "id=?", arrayOf(id))
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
}

private fun Feed.contentSourceSnapshot(): ContentSourceSnapshot = ContentSourceSnapshot(id, title, feedUrl)
private fun ParsedFeed.contentItems(): List<SourceContentItem> = articles.map { article ->
  SourceContentItem(article.externalId, article.identityKey, article.url, article.title, article.publishedAt)
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
  values.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}

private fun Cursor.feed(): Feed = Feed(
  id = string("id"),
  title = nullableString("custom_title") ?: string("title"),
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
