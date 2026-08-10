package dev.terashima.yomitorirss.feature.backup.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal fun YomitoriDatabase.exportBackup(): JSONObject = JSONObject().apply {
  put("format", "yomitori-rss-backup")
  put("version", 2)
  put("exportedAt", Instant.now().toString())
  put("feeds", queryJsonArray("SELECT id,title,feed_url,site_url,created_at FROM feeds ORDER BY title") { cursor ->
    JSONObject()
      .put("id", cursor.text("id"))
      .put("title", cursor.text("title"))
      .put("feedUrl", cursor.text("feed_url"))
      .put("siteUrl", cursor.nullableText("site_url"))
      .put("createdAt", cursor.text("created_at"))
  })
  put("tags", queryJsonArray("SELECT id,name,normalized_name,created_at FROM tags ORDER BY normalized_name") { cursor ->
    JSONObject()
      .put("id", cursor.text("id"))
      .put("name", cursor.text("name"))
      .put("normalizedName", cursor.text("normalized_name"))
      .put("createdAt", cursor.text("created_at"))
  })
  put("folders", queryJsonArray("SELECT id,name,normalized_name,system_kind,created_at FROM bookmark_folders ORDER BY normalized_name") { cursor ->
    JSONObject()
      .put("id", cursor.text("id"))
      .put("name", cursor.text("name"))
      .put("normalizedName", cursor.text("normalized_name"))
      .put("systemKind", cursor.nullableText("system_kind"))
      .put("createdAt", cursor.text("created_at"))
  })
  put("savedArticles", queryJsonArray("SELECT * FROM articles WHERE saved_at IS NOT NULL ORDER BY published_at DESC") { cursor ->
    val articleId = cursor.text("id")
    JSONObject()
      .put("id", articleId)
      .put("feedId", cursor.nullableText("feed_id"))
      .put("externalId", cursor.nullableText("external_id"))
      .put("identityKey", cursor.text("identity_key"))
      .put("url", cursor.text("url"))
      .put("title", cursor.text("title"))
      .put("publishedAt", cursor.text("published_at"))
      .put("fetchedAt", cursor.text("fetched_at"))
      .put("readAt", cursor.nullableText("read_at"))
      .put("savedAt", cursor.nullableText("saved_at"))
      .put("sourceTitle", cursor.text("source_title"))
      .put("sourceFeedUrl", cursor.text("source_feed_url"))
      .put("tagIds", articleTagIds(articleId))
      .put("folderId", articleFolderId(articleId))
  })
}

internal fun YomitoriDatabase.restoreBackup(root: JSONObject) = transaction {
  val version = root.optInt("version")
  require(root.optString("format") == "yomitori-rss-backup" && version in 1..2) {
    "対応していないバックアップです"
  }

  execSQL("DELETE FROM summary_tasks")
  execSQL("DELETE FROM article_summaries")
  execSQL("DELETE FROM article_folders")
  execSQL("DELETE FROM article_tags")
  execSQL("DELETE FROM articles")
  execSQL("DELETE FROM bookmark_folders")
  execSQL("DELETE FROM tags")
  execSQL("DELETE FROM feeds")

  val feeds = root.getJSONArray("feeds")
  val feedIds = mutableSetOf<String>()
  for (index in 0 until feeds.length()) {
    val item = feeds.getJSONObject(index)
    val id = item.getString("id")
    feedIds += id
    insertOrThrow(
      "feeds",
      null,
      values(
        "id" to id,
        "title" to item.getString("title"),
        "feed_url" to item.getString("feedUrl"),
        "site_url" to item.nullable("siteUrl"),
        "created_at" to item.getString("createdAt"),
      ),
    )
  }

  val tags = root.getJSONArray("tags")
  for (index in 0 until tags.length()) {
    val item = tags.getJSONObject(index)
    insertOrThrow(
      "tags",
      null,
      values(
        "id" to item.getString("id"),
        "name" to item.getString("name"),
        "normalized_name" to item.getString("normalizedName"),
        "created_at" to item.getString("createdAt"),
      ),
    )
  }

  val folderIds = mutableSetOf<String>()
  if (version >= 2) {
    val folders = root.optJSONArray("folders") ?: JSONArray()
    for (index in 0 until folders.length()) {
      val item = folders.getJSONObject(index)
      val id = item.getString("id")
      folderIds += id
      insertOrThrow(
        "bookmark_folders",
        null,
        values(
          "id" to id,
          "name" to item.getString("name"),
          "normalized_name" to item.getString("normalizedName"),
          "system_kind" to item.nullable("systemKind"),
          "created_at" to item.getString("createdAt"),
        ),
      )
    }
  }

  val saved = root.getJSONArray("savedArticles")
  for (index in 0 until saved.length()) {
    val item = saved.getJSONObject(index)
    val id = item.getString("id")
    insertOrThrow(
      "articles",
      null,
      values(
        "id" to id,
        "feed_id" to item.nullable("feedId")?.takeIf(feedIds::contains),
        "external_id" to item.nullable("externalId"),
        "identity_key" to item.getString("identityKey"),
        "url" to item.getString("url"),
        "title" to item.getString("title"),
        "published_at" to item.getString("publishedAt"),
        "fetched_at" to item.getString("fetchedAt"),
        "read_at" to item.nullable("readAt"),
        "saved_at" to item.nullable("savedAt"),
        "source_title" to item.getString("sourceTitle"),
        "source_feed_url" to item.getString("sourceFeedUrl"),
      ),
    )
    val tagIds = item.optJSONArray("tagIds") ?: JSONArray()
    for (tagIndex in 0 until tagIds.length()) {
      insertWithOnConflict(
        "article_tags",
        null,
        values("article_id" to id, "tag_id" to tagIds.getString(tagIndex)),
        SQLiteDatabase.CONFLICT_IGNORE,
      )
    }
    item.nullable("folderId")?.takeIf(folderIds::contains)?.let { folderId ->
      insertWithOnConflict(
        "article_folders",
        null,
        values("article_id" to id, "folder_id" to folderId),
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
  }
}

private fun YomitoriDatabase.queryJsonArray(
  sql: String,
  mapper: (Cursor) -> JSONObject,
): JSONArray = JSONArray().apply {
  readableDatabase.rawQuery(sql, null).use { cursor ->
    while (cursor.moveToNext()) put(mapper(cursor))
  }
}

private fun YomitoriDatabase.articleTagIds(articleId: String): JSONArray = JSONArray().apply {
  readableDatabase.rawQuery(
    "SELECT tag_id FROM article_tags WHERE article_id=? ORDER BY tag_id",
    arrayOf(articleId),
  ).use { cursor -> while (cursor.moveToNext()) put(cursor.getString(0)) }
}

private fun YomitoriDatabase.articleFolderId(articleId: String): String? = readableDatabase.rawQuery(
  "SELECT folder_id FROM article_folders WHERE article_id=? LIMIT 1",
  arrayOf(articleId),
).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

private inline fun <T> YomitoriDatabase.transaction(block: SQLiteDatabase.() -> T): T {
  val database = writableDatabase
  database.beginTransaction()
  return try {
    val value = database.block()
    database.setTransactionSuccessful()
    value
  } finally {
    database.endTransaction()
  }
}

private fun Cursor.text(name: String): String = getString(getColumnIndexOrThrow(name))
private fun Cursor.nullableText(name: String): String? =
  getColumnIndexOrThrow(name).let { index -> if (isNull(index)) null else getString(index) }
private fun JSONObject.nullable(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}
