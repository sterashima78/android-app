package dev.terashima.yomitorirss.feature.article.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.ContentSourceGateway
import dev.terashima.yomitorirss.feature.article.ContentSourceSnapshot
import dev.terashima.yomitorirss.feature.article.ContentType
import dev.terashima.yomitorirss.feature.article.SourceContentItem
import dev.terashima.yomitorirss.feature.bookmark.BookmarkContentQuery
import java.util.UUID

class DefaultContentSourceGateway(
  private val database: DatabaseConnection,
  private val bookmarkContentQuery: BookmarkContentQuery,
) : ContentSourceGateway {
  override fun upsertSourceContent(
    source: ContentSourceSnapshot,
    items: List<SourceContentItem>,
    fetchedAt: String,
    insertedReadAt: String?,
  ) {
    database.transaction {
      items.take(500).forEach { item ->
        val detachedId = rawQuery(
          "SELECT id FROM articles WHERE feed_id IS NULL AND (identity_key=? OR url=?) ORDER BY fetched_at DESC LIMIT 1",
          arrayOf(item.identityKey, item.url),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (
          detachedId != null &&
          detachedId in bookmarkContentQuery.bookmarkedContentIds(setOf(detachedId))
        ) {
          update(
            "articles",
            values(
              "feed_id" to source.id,
              "source_title" to source.title,
              "source_feed_url" to source.sourceUrl,
            ),
            "id=?",
            arrayOf(detachedId),
          )
        }
        insertWithOnConflict(
          "articles",
          null,
          values(
            "id" to UUID.randomUUID().toString(),
            "feed_id" to source.id,
            "external_id" to item.externalId,
            "identity_key" to item.identityKey,
            "url" to item.url,
            "title" to item.title,
            "published_at" to item.publishedAt,
            "fetched_at" to fetchedAt,
            "read_at" to insertedReadAt,
            "source_title" to source.title,
            "source_feed_url" to source.sourceUrl,
          ),
          SQLiteDatabase.CONFLICT_IGNORE,
        )
      }
    }
  }

  override fun renameSourceContent(sourceId: String, sourceTitle: String) {
    database.writable.update(
      "articles",
      values("source_title" to sourceTitle),
      "feed_id=?",
      arrayOf(sourceId),
    )
  }

  override fun detachSourceContent(sourceId: String, inheritedContentType: ContentType?) {
    val sourceContentIds = database.readable.rawQuery(
      "SELECT id FROM articles WHERE feed_id=?",
      arrayOf(sourceId),
    ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    val bookmarkedIds = bookmarkContentQuery.bookmarkedContentIds(sourceContentIds)
    database.transaction {
      bookmarkedIds.chunked(QUERY_CHUNK_SIZE).forEach { ids ->
        val placeholders = ids.joinToString(",") { "?" }
        if (inheritedContentType != null) {
          update(
            "articles",
            values("content_type" to inheritedContentType.name),
            "id IN($placeholders) AND content_type IS NULL",
            ids.toTypedArray(),
          )
        }
        update("articles", values("feed_id" to null), "id IN($placeholders)", ids.toTypedArray())
      }
      delete("articles", "feed_id=?", arrayOf(sourceId))
    }
  }

  private companion object {
    const val QUERY_CHUNK_SIZE = 400
  }
}

private fun values(vararg entries: Pair<String, String?>): ContentValues = ContentValues().apply {
  entries.forEach { (key, value) -> if (value == null) putNull(key) else put(key, value) }
}
