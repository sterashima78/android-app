package dev.terashima.yomitorirss.feature.video.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoProvider
import dev.terashima.yomitorirss.feature.video.VideoProviderType
import dev.terashima.yomitorirss.feature.video.VideoProviderVideo
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.VideoSubscription
import java.util.Locale

internal class VideoProviderDatabase(
  private val database: DatabaseConnection,
) {
  fun providers(): List<VideoProvider> {
    ensureSchema()
    return database.readable.rawQuery(
      "SELECT id, provider_type, name, enabled, created_at, updated_at FROM video_providers ORDER BY name COLLATE NOCASE, id",
      null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toProvider()) } }
  }

  fun saveProvider(provider: VideoProvider): VideoProvider {
    ensureSchema()
    val existingId = queryProviderId(provider.type)
    val now = System.currentTimeMillis()
    val id = provider.id.trim().takeIf(String::isNotBlank)
      ?: existingId
      ?: provider.type.name.lowercase(Locale.ROOT)
    require(existingId == null || existingId == id) { "同じ種類の動画プロバイダが既に登録されています" }
    val saved = provider.copy(
      id = id,
      name = provider.name.trim().ifBlank { provider.type.defaultDisplayName() },
      createdAtEpochMillis = queryProviderCreatedAt(id) ?: provider.createdAtEpochMillis.takeIf { it > 0L } ?: now,
      updatedAtEpochMillis = now,
    )
    database.write {
      insertWithOnConflict(
        "video_providers",
        null,
        ContentValues().apply {
          put("id", saved.id)
          put("provider_type", saved.type.name)
          put("name", saved.name)
          put("enabled", if (saved.enabled) 1 else 0)
          put("created_at", saved.createdAtEpochMillis)
          put("updated_at", saved.updatedAtEpochMillis)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
    return saved
  }

  fun deleteProvider(id: String) {
    ensureSchema()
    database.transaction {
      videoIds("provider_id = ?", arrayOf(id)).forEach { videoId ->
        if (!hasRetainedVideoState(videoId)) delete("video_items", "id = ?", arrayOf(videoId))
      }
      delete("video_providers", "id = ?", arrayOf(id))
    }
  }

  fun subscriptions(providerId: String?): List<VideoSubscription> {
    ensureSchema()
    val where = providerId?.let { "WHERE provider_id = ?" }.orEmpty()
    val args = providerId?.let { arrayOf(it) }
    return database.readable.rawQuery(
      "SELECT id, provider_id, source_id, title, source_url, created_at, updated_at FROM video_subscriptions $where ORDER BY title COLLATE NOCASE, id",
      args,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toSubscription()) } }
  }

  fun requireProvider(id: String): VideoProvider = providers().firstOrNull { it.id == id }
    ?: throw IllegalArgumentException("動画プロバイダが見つかりません")

  fun upsertProviderFeed(provider: VideoProvider, feed: VideoProviderFeed): Pair<VideoSubscription, Int> {
    ensureSchema()
    return database.transaction {
      val now = System.currentTimeMillis()
      val subscriptionId = providerSubscriptionId(provider.id, feed.sourceId)
      val createdAt = rawQuery(
        "SELECT created_at FROM video_subscriptions WHERE id = ?",
        arrayOf(subscriptionId),
      ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else now }
      val subscription = VideoSubscription(
        id = subscriptionId,
        providerId = provider.id,
        sourceId = feed.sourceId,
        title = feed.title,
        sourceUrl = feed.sourceUrl,
        createdAtEpochMillis = createdAt,
        updatedAtEpochMillis = now,
      )
      insertWithOnConflict(
        "video_subscriptions",
        null,
        ContentValues().apply {
          put("id", subscription.id)
          put("provider_id", subscription.providerId)
          put("source_id", subscription.sourceId)
          put("title", subscription.title)
          put("source_url", subscription.sourceUrl)
          put("created_at", subscription.createdAtEpochMillis)
          put("updated_at", subscription.updatedAtEpochMillis)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
      var added = 0
      feed.videos.forEach { incoming ->
        val videoId = providerVideoId(provider.id, incoming.id)
        val existed = rawQuery(
          "SELECT 1 FROM video_provider_items WHERE video_id = ? LIMIT 1",
          arrayOf(videoId),
        ).use { it.moveToFirst() }
        val videoValues = ContentValues().apply {
          put("id", videoId)
          put("source", VideoSource.SERVICE.name)
          put("source_id", providerItemSourceId(provider.id, incoming.id))
          put("title", incoming.title)
          put("page_url", incoming.url)
          putStringOrNull("thumbnail_url", incoming.thumbnailUrl)
          putNull("duration_ms")
          putNull("size_bytes")
          putNull("mime_type")
          put("updated_at", incoming.publishedAtEpochMillis)
        }
        val updated = update("video_items", videoValues, "id = ?", arrayOf(videoId))
        if (updated == 0) insertOrThrow("video_items", null, videoValues)
        if (!existed) {
          insertOrThrow(
            "video_provider_items",
            null,
            ContentValues().apply {
              put("video_id", videoId)
              put("provider_id", provider.id)
              put("subscription_id", subscription.id)
              put("provider_item_id", incoming.id)
              put("published_at", incoming.publishedAtEpochMillis)
              put("is_read", 0)
              put("is_watch_later", 0)
            },
          )
          added += 1
        } else {
          update(
            "video_provider_items",
            ContentValues().apply {
              put("provider_id", provider.id)
              put("subscription_id", subscription.id)
              put("provider_item_id", incoming.id)
              put("published_at", incoming.publishedAtEpochMillis)
            },
            "video_id = ?",
            arrayOf(videoId),
          )
        }
      }
      subscription to added
    }
  }

  fun unsubscribe(subscriptionId: String) {
    ensureSchema()
    database.transaction {
      videoIds("subscription_id = ?", arrayOf(subscriptionId)).forEach { videoId ->
        if (hasRetainedVideoState(videoId)) {
          update(
            "video_provider_items",
            ContentValues().apply { putNull("subscription_id") },
            "video_id = ?",
            arrayOf(videoId),
          )
        } else {
          delete("video_items", "id = ?", arrayOf(videoId))
        }
      }
      delete("video_subscriptions", "id = ?", arrayOf(subscriptionId))
    }
  }

  fun unreadVideos(): List<VideoProviderVideo> = queryProviderVideos("p.is_read = 0 AND p.is_watch_later = 0")

  fun watchLaterVideos(): List<VideoProviderVideo> = queryProviderVideos("p.is_watch_later = 1")

  fun historyVideos(limit: Int): List<VideoProviderVideo> {
    require(limit > 0) { "履歴件数は1以上にしてください" }
    return queryProviderVideos("p.is_read = 1 AND p.is_watch_later = 0", limit)
  }

  fun markRead(videoId: String) = updateState(videoId) {
    put("is_read", 1)
    put("is_watch_later", 0)
  }

  fun markUnread(videoId: String) = updateState(videoId) {
    put("is_read", 0)
    put("is_watch_later", 0)
  }

  fun setWatchLater(videoId: String, watchLater: Boolean) = updateState(videoId) {
    put("is_watch_later", if (watchLater) 1 else 0)
    if (watchLater) put("is_read", 0)
  }

  fun markAllRead() {
    ensureSchema()
    database.write {
      update(
        "video_provider_items",
        ContentValues().apply { put("is_read", 1) },
        "is_read = 0 AND is_watch_later = 0",
        null,
      )
    }
  }

  private fun updateState(videoId: String, values: ContentValues.() -> Unit) {
    ensureSchema()
    database.write {
      update("video_provider_items", ContentValues().apply(values), "video_id = ?", arrayOf(videoId))
    }
  }

  private fun queryProviderVideos(whereClause: String, limit: Int? = null): List<VideoProviderVideo> {
    ensureSchema()
    val limitClause = limit?.let { "LIMIT $it" }.orEmpty()
    return database.readable.rawQuery(
      """
        SELECT i.id, i.source, i.source_id, i.title, i.page_url, i.thumbnail_url,
               i.duration_ms, i.size_bytes, i.mime_type, i.updated_at,
               p.provider_id, p.provider_item_id, p.subscription_id, s.title,
               p.published_at, p.is_read, p.is_watch_later
        FROM video_provider_items p
        JOIN video_items i ON i.id = p.video_id
        LEFT JOIN video_subscriptions s ON s.id = p.subscription_id
        WHERE $whereClause
        ORDER BY p.published_at DESC
        $limitClause
      """.trimIndent(),
      null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toProviderVideo()) } }
  }

  private fun queryProviderId(type: VideoProviderType): String? = database.readable.rawQuery(
    "SELECT id FROM video_providers WHERE provider_type = ? LIMIT 1",
    arrayOf(type.name),
  ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

  private fun queryProviderCreatedAt(id: String): Long? = database.readable.rawQuery(
    "SELECT created_at FROM video_providers WHERE id = ? LIMIT 1",
    arrayOf(id),
  ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

  private fun SQLiteDatabase.videoIds(where: String, args: Array<String>): List<String> = rawQuery(
    "SELECT video_id FROM video_provider_items WHERE $where",
    args,
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

  private fun SQLiteDatabase.hasRetainedVideoState(videoId: String): Boolean = rawQuery(
    """
      SELECT 1
      WHERE EXISTS(SELECT 1 FROM video_saved_items WHERE video_id = ?)
         OR EXISTS(SELECT 1 FROM video_playback_state WHERE video_id = ?)
      LIMIT 1
    """.trimIndent(),
    arrayOf(videoId, videoId),
  ).use { it.moveToFirst() }

  private fun ensureSchema() = ensureVideoSchema(database.writable)
}

private fun Cursor.toProvider(): VideoProvider = VideoProvider(
  id = getString(0),
  type = VideoProviderType.valueOf(getString(1)),
  name = getString(2),
  enabled = getInt(3) != 0,
  createdAtEpochMillis = getLong(4),
  updatedAtEpochMillis = getLong(5),
)

private fun Cursor.toSubscription(): VideoSubscription = VideoSubscription(
  id = getString(0),
  providerId = getString(1),
  sourceId = getString(2),
  title = getString(3),
  sourceUrl = getString(4),
  createdAtEpochMillis = getLong(5),
  updatedAtEpochMillis = getLong(6),
)

private fun Cursor.toProviderVideo(): VideoProviderVideo = VideoProviderVideo(
  video = VideoItem(
    id = getString(0),
    source = VideoSource.valueOf(getString(1)),
    sourceId = getString(2),
    title = getString(3),
    pageUrl = stringOrNull(4),
    thumbnailUrl = stringOrNull(5),
    durationMs = longOrNull(6),
    sizeBytes = longOrNull(7),
    mimeType = stringOrNull(8),
    updatedAtEpochMillis = getLong(9),
  ),
  providerId = getString(10),
  providerItemId = getString(11),
  subscriptionId = stringOrNull(12),
  subscriptionTitle = stringOrNull(13),
  publishedAtEpochMillis = getLong(14),
  isRead = getInt(15) != 0,
  isWatchLater = getInt(16) != 0,
)

private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
private fun ContentValues.putStringOrNull(key: String, value: String?) = if (value == null) putNull(key) else put(key, value)
private fun providerSubscriptionId(providerId: String, sourceId: String): String = "$providerId:$sourceId"
private fun providerVideoId(providerId: String, itemId: String): String = "provider:$providerId:$itemId"
private fun providerItemSourceId(providerId: String, itemId: String): String = "$providerId:$itemId"
private fun VideoProviderType.defaultDisplayName(): String = when (this) {
  VideoProviderType.YOUTUBE -> "YouTube"
}
