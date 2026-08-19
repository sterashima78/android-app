package dev.terashima.yomitorirss.feature.article.data

import android.content.ContentValues
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.bookmark.BookmarkArticleGateway
import dev.terashima.yomitorirss.feature.bookmark.BookmarkArticleSave
import dev.terashima.yomitorirss.feature.bookmark.BookmarkImportedArticle
import dev.terashima.yomitorirss.feature.bookmark.BookmarkSaveResult
import java.time.Instant
import java.util.UUID

class DefaultBookmarkArticleGateway(
  private val database: DatabaseConnection,
) : BookmarkArticleGateway {
  override suspend fun isBookmarked(articleId: String): Boolean = database.readable.rawQuery(
    "SELECT 1 FROM articles WHERE id=? AND saved_at IS NOT NULL LIMIT 1",
    arrayOf(articleId),
  ).use { cursor -> cursor.moveToFirst() }

  override suspend fun saveAndRead(articleId: String) {
    val now = Instant.now().toString()
    database.writable.update(
      "articles",
      values("read_at" to now, "saved_at" to now),
      "id=?",
      arrayOf(articleId),
    )
  }

  override suspend fun unsave(articleId: String) {
    database.writable.update(
      "articles",
      values("saved_at" to null),
      "id=?",
      arrayOf(articleId),
    )
  }

  override suspend fun saveSharedArticle(
    url: String,
    title: String,
    sourceTitle: String,
  ): BookmarkArticleSave = database.transaction {
    val now = Instant.now().toString()
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

    val articleId = existingId ?: UUID.randomUUID().toString().also { id ->
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
          "saved_at" to now,
          "source_title" to sourceTitle,
          "source_feed_url" to "",
        ),
      )
    }

    if (existingId != null && !alreadyBookmarked) {
      update(
        "articles",
        values("read_at" to now, "saved_at" to now),
        "id=?",
        arrayOf(articleId),
      )
    }

    BookmarkArticleSave(
      articleId = articleId,
      result = if (alreadyBookmarked) BookmarkSaveResult.ALREADY_BOOKMARKED else BookmarkSaveResult.ADDED,
    )
  }

  override suspend fun importSavedArticle(
    url: String,
    title: String,
    sourceTitle: String,
    createdAt: String,
    identityPrefix: String,
  ): BookmarkImportedArticle = database.transaction {
    var articleId: String? = null
    var savedAt: String? = null
    var readAt: String? = null
    rawQuery(
      "SELECT id,saved_at,read_at FROM articles WHERE url=? ORDER BY CASE WHEN saved_at IS NULL THEN 1 ELSE 0 END,fetched_at DESC LIMIT 1",
      arrayOf(url),
    ).use { cursor ->
      if (cursor.moveToFirst()) {
        articleId = cursor.getString(0)
        savedAt = if (cursor.isNull(1)) null else cursor.getString(1)
        readAt = if (cursor.isNull(2)) null else cursor.getString(2)
      }
    }

    if (articleId == null) {
      val id = UUID.randomUUID().toString()
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
          "saved_at" to createdAt,
          "source_title" to sourceTitle,
          "source_feed_url" to "",
        ),
      )
      return@transaction BookmarkImportedArticle(id, added = true, duplicate = false)
    }

    val id = requireNotNull(articleId)
    if (savedAt == null) {
      update(
        "articles",
        values("read_at" to createdAt, "saved_at" to createdAt),
        "id=?",
        arrayOf(id),
      )
      return@transaction BookmarkImportedArticle(id, added = true, duplicate = false)
    }

    if (readAt == null) {
      update(
        "articles",
        values("read_at" to (savedAt ?: createdAt)),
        "id=?",
        arrayOf(id),
      )
    }
    BookmarkImportedArticle(id, added = false, duplicate = true)
  }
}

private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}
