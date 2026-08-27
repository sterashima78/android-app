package dev.terashima.yomitorirss.feature.article.data

import android.content.ContentValues
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkArticleGateway
import java.time.Instant
import java.util.UUID

class DefaultBookmarkArticleGateway(
  private val database: DatabaseConnection,
) : BookmarkArticleGateway {
  override suspend fun markRead(articleId: String) {
    val updated = database.write {
      update(
        "articles",
        values("read_at" to Instant.now().toString()),
        "id=?",
        arrayOf(articleId),
      )
    }
    require(updated > 0) { "記事が見つかりません" }
  }

  override suspend fun findOrCreateSharedArticle(
    url: String,
    title: String,
    sourceTitle: String,
  ): String = database.transaction {
    val now = Instant.now().toString()
    val existingId = rawQuery(
      "SELECT id FROM articles WHERE url=? ORDER BY fetched_at DESC LIMIT 1",
      arrayOf(url),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    if (existingId != null) {
      update("articles", values("read_at" to now), "id=?", arrayOf(existingId))
      return@transaction existingId
    }

    UUID.randomUUID().toString().also { id ->
      insertOrThrow(
        "articles",
        null,
        values(
          "id" to id,
          "feed_id" to null,
          "external_id" to null,
          "identity_key" to "shared:$url",
          "url" to url,
          "title" to title,
          "published_at" to now,
          "fetched_at" to now,
          "read_at" to now,
          "source_title" to sourceTitle,
          "source_feed_url" to "",
        ),
      )
    }
  }

  override suspend fun findOrCreateImportedArticle(
    url: String,
    title: String,
    sourceTitle: String,
    createdAt: String,
    identityPrefix: String,
  ): String = database.transaction {
    val existing = rawQuery(
      "SELECT id,read_at FROM articles WHERE url=? ORDER BY fetched_at DESC LIMIT 1",
      arrayOf(url),
    ).use { cursor ->
      if (!cursor.moveToFirst()) null else cursor.getString(0) to if (cursor.isNull(1)) null else cursor.getString(1)
    }
    if (existing != null) {
      if (existing.second == null) update("articles", values("read_at" to createdAt), "id=?", arrayOf(existing.first))
      return@transaction existing.first
    }

    UUID.randomUUID().toString().also { id ->
      insertOrThrow(
        "articles",
        null,
        values(
          "id" to id,
          "feed_id" to null,
          "external_id" to null,
          "identity_key" to "$identityPrefix:$url",
          "url" to url,
          "title" to title,
          "published_at" to createdAt,
          "fetched_at" to Instant.now().toString(),
          "read_at" to createdAt,
          "source_title" to sourceTitle,
          "source_feed_url" to "",
        ),
      )
    }
  }
}

private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}
