package dev.terashima.yomitorirss.feature.video.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.library.SmbConnectionProfileRepository
import dev.terashima.yomitorirss.feature.library.SmbMediaFile
import dev.terashima.yomitorirss.feature.library.SmbMediaFileAccess
import dev.terashima.yomitorirss.feature.library.SmbMediaLocation
import dev.terashima.yomitorirss.feature.video.VideoFolder
import dev.terashima.yomitorirss.feature.video.VideoItem
import dev.terashima.yomitorirss.feature.video.VideoPlaybackState
import dev.terashima.yomitorirss.feature.video.VideoRepository
import dev.terashima.yomitorirss.feature.video.VideoSavedState
import dev.terashima.yomitorirss.feature.video.VideoSmbSource
import dev.terashima.yomitorirss.feature.video.VideoSource
import dev.terashima.yomitorirss.feature.video.WebVideoExtractorRule
import java.net.URI
import java.util.Locale
import java.util.UUID

class DefaultVideoRepository(
  private val database: DatabaseConnection,
  httpClient: HttpClient,
  private val smbMediaFileAccess: SmbMediaFileAccess,
  private val smbConnectionProfiles: SmbConnectionProfileRepository,
  private val webExtractorClient: AndroidWebVideoExtractorClient,
) : VideoRepository {
  private val metadataClient = WebVideoMetadataClient(httpClient)

  override suspend fun items(): List<VideoItem> {
    ensureSchema()
    return queryItems()
  }

  override suspend fun addWeb(url: String): VideoItem {
    ensureSchema()
    val normalizedUrl = normalizeWebVideoUrl(url)
    val staticMetadata = metadataClient.fetch(normalizedUrl)
    val rule = findMatchingWebVideoExtractorRule(extractorRules(), normalizedUrl)
    val customMetadata = if (rule != null) {
      runCatching { webExtractorClient.extract(normalizedUrl, rule) }.getOrNull()
    } else {
      null
    }
    val title = customMetadata?.title?.takeIf(String::isNotBlank)
      ?: staticMetadata.title?.takeIf(String::isNotBlank)
      ?: URI(normalizedUrl).host
      ?: normalizedUrl
    val now = System.currentTimeMillis()
    val item = VideoItem(
      id = stableVideoId(VideoSource.WEB, normalizedUrl),
      source = VideoSource.WEB,
      sourceId = normalizedUrl,
      title = title,
      pageUrl = normalizedUrl,
      thumbnailUrl = customMetadata?.thumbnailUrl?.takeIf(String::isNotBlank)
        ?: staticMetadata.thumbnailUrl,
      updatedAtEpochMillis = now,
    )
    database.write { upsertVideoItem(item) }
    return item
  }

  override suspend fun remove(id: String) {
    ensureSchema()
    database.transaction {
      delete("video_saved_items", "video_id = ?", arrayOf(id))
      delete("video_playback_state", "video_id = ?", arrayOf(id))
      delete("video_items", "id = ?", arrayOf(id))
    }
  }

  override suspend fun refreshSmb(): Int {
    ensureSchema()
    val validProfileIds = smbConnectionProfiles.connectionProfiles().mapTo(HashSet()) { it.id }
    val sources = smbSources().filter { it.serverId in validProfileIds }
    require(sources.isNotEmpty()) { "動画設定で有効なSMB同期場所を追加してください" }
    val files = buildList {
      sources.forEach { source ->
        addAll(
          smbMediaFileAccess.listMediaFiles(
            location = SmbMediaLocation(source.serverId, source.share, source.rootPath),
            extensions = SMB_VIDEO_EXTENSIONS,
          ),
        )
        require(size <= MAX_SMB_VIDEO_ITEMS) { "SMB動画が上限の $MAX_SMB_VIDEO_ITEMS 件を超えています" }
      }
    }
    val now = System.currentTimeMillis()
    val incoming = files.map { it.toVideoItem(now) }.distinctBy(VideoItem::id)
    val incomingIds = incoming.mapTo(HashSet()) { it.id }
    val legacyPlaybackTargets = files
      .groupBy { file -> stableVideoId(VideoSource.SMB, legacySmbVideoSourceId(file.serverId, file.path)) }
      .mapNotNull { (legacyId, matchingFiles) ->
        val targetIds = matchingFiles.map { it.toVideoItem(now).id }.distinct()
        targetIds.singleOrNull()?.let { targetId -> legacyId to targetId }
      }
      .toMap()
    database.transaction {
      incoming.forEach(::upsertVideoItem)
      legacyPlaybackTargets.forEach { (legacyId, targetId) ->
        migratePlaybackStateIfAbsent(legacyId, targetId)
      }
      existingSmbIds().filterNot(incomingIds::contains).forEach { staleId ->
        delete("video_saved_items", "video_id = ?", arrayOf(staleId))
        delete("video_playback_state", "video_id = ?", arrayOf(staleId))
        delete("video_items", "id = ?", arrayOf(staleId))
      }
    }
    return incoming.size
  }

  override fun smbSources(): List<VideoSmbSource> {
    ensureSchema()
    return database.readable.rawQuery(
      """
        SELECT id, server_id, share_name, root_path, updated_at
        FROM video_smb_sources
        ORDER BY updated_at DESC, id
      """.trimIndent(),
      null,
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            VideoSmbSource(
              id = cursor.getString(0),
              serverId = cursor.getString(1),
              share = cursor.getString(2),
              rootPath = cursor.getString(3),
              updatedAtEpochMillis = cursor.getLong(4),
            ),
          )
        }
      }
    }
  }

  override fun saveSmbSource(source: VideoSmbSource): VideoSmbSource {
    ensureSchema()
    val saved = source.copy(
      id = source.id.ifBlank { UUID.randomUUID().toString() },
      serverId = source.serverId.trim(),
      share = source.share.trim().trim('/', '\\'),
      rootPath = normalizeVideoSmbRoot(source.rootPath),
      updatedAtEpochMillis = System.currentTimeMillis(),
    )
    require(saved.serverId.isNotBlank()) { "SMB接続設定を選択してください" }
    require(saved.share.isNotBlank()) { "SMB共有名を入力してください" }
    database.write {
      insertWithOnConflict(
        "video_smb_sources",
        null,
        ContentValues().apply {
          put("id", saved.id)
          put("server_id", saved.serverId)
          put("share_name", saved.share)
          put("root_path", saved.rootPath)
          put("updated_at", saved.updatedAtEpochMillis)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
    return saved
  }

  override fun deleteSmbSource(id: String) {
    ensureSchema()
    database.write { delete("video_smb_sources", "id = ?", arrayOf(id)) }
  }

  override fun folders(): List<VideoFolder> {
    ensureSchema()
    return database.readable.rawQuery(
      """
        SELECT id, name, created_at, updated_at
        FROM video_folders
        ORDER BY normalized_name, id
      """.trimIndent(),
      null,
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            VideoFolder(
              id = cursor.getString(0),
              name = cursor.getString(1),
              createdAtEpochMillis = cursor.getLong(2),
              updatedAtEpochMillis = cursor.getLong(3),
            ),
          )
        }
      }
    }
  }

  override fun saveFolder(folder: VideoFolder): VideoFolder {
    ensureSchema()
    val name = folder.name.trim()
    require(name.isNotBlank()) { "フォルダ名を入力してください" }
    val normalizedName = name.lowercase(Locale.ROOT)
    val id = folder.id.ifBlank { UUID.randomUUID().toString() }
    require(!folderNameExists(normalizedName, id)) { "同じ名前のフォルダがあります" }
    val now = System.currentTimeMillis()
    val existingCreatedAt = queryFolderCreatedAt(id)
    val saved = VideoFolder(
      id = id,
      name = name,
      createdAtEpochMillis = existingCreatedAt ?: now,
      updatedAtEpochMillis = now,
    )
    database.write {
      val values = ContentValues().apply {
        put("name", saved.name)
        put("normalized_name", normalizedName)
        put("created_at", saved.createdAtEpochMillis)
        put("updated_at", saved.updatedAtEpochMillis)
      }
      val updated = update("video_folders", values, "id = ?", arrayOf(id))
      if (updated == 0) {
        values.put("id", id)
        insertOrThrow("video_folders", null, values)
      }
    }
    return saved
  }

  override fun deleteFolder(id: String) {
    ensureSchema()
    database.transaction {
      val values = ContentValues().apply { putNull("folder_id") }
      update("video_saved_items", values, "folder_id = ?", arrayOf(id))
      delete("video_folders", "id = ?", arrayOf(id))
    }
  }

  override fun saveVideo(videoId: String, folderId: String?) {
    ensureSchema()
    require(videoExists(videoId)) { "動画が見つかりません" }
    if (folderId != null) require(folderExists(folderId)) { "保存先フォルダが見つかりません" }
    val savedAt = querySavedAt(videoId) ?: System.currentTimeMillis()
    database.write {
      insertWithOnConflict(
        "video_saved_items",
        null,
        ContentValues().apply {
          put("video_id", videoId)
          putNullable("folder_id", folderId)
          put("saved_at", savedAt)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
  }

  override fun removeSavedVideo(videoId: String) {
    ensureSchema()
    database.write { delete("video_saved_items", "video_id = ?", arrayOf(videoId)) }
  }

  override suspend fun updatePlayback(
    videoId: String,
    positionMs: Long,
    durationMs: Long,
    completed: Boolean?,
  ) {
    ensureSchema()
    val safeDuration = durationMs.coerceAtLeast(0L)
    val maxPosition = safeDuration.takeIf { it > 0L } ?: Long.MAX_VALUE
    val safePosition = positionMs.coerceIn(0L, maxPosition)
    val derivedCompleted = completed ?: (
      safeDuration > 0L && safePosition.toDouble() / safeDuration.toDouble() >= COMPLETED_RATIO
    )
    database.write {
      insertWithOnConflict(
        "video_playback_state",
        null,
        ContentValues().apply {
          put("video_id", videoId)
          put("position_ms", safePosition)
          put("duration_ms", safeDuration)
          put("last_played_at", System.currentTimeMillis())
          put("completed", if (derivedCompleted) 1 else 0)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
  }

  override suspend fun setCompleted(videoId: String, completed: Boolean) {
    ensureSchema()
    val current = queryPlaybackState(videoId)
    updatePlayback(
      videoId = videoId,
      positionMs = current?.positionMs ?: 0L,
      durationMs = current?.durationMs ?: 0L,
      completed = completed,
    )
  }

  override fun extractorRules(): List<WebVideoExtractorRule> {
    ensureSchema()
    return database.readable.rawQuery(
      """
        SELECT id, url_pattern, title_function, thumbnail_function,
               playback_function, timeout_seconds, updated_at
        FROM video_web_extractor_rules
        ORDER BY updated_at DESC, id
      """.trimIndent(),
      null,
    ).use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(
            WebVideoExtractorRule(
              id = cursor.getString(0),
              urlPattern = cursor.getString(1),
              titleExtractorCode = cursor.stringOrNull(2),
              thumbnailExtractorCode = cursor.stringOrNull(3),
              playbackExtractorCode = cursor.stringOrNull(4),
              timeoutSeconds = cursor.getInt(5),
              updatedAtEpochMillis = cursor.getLong(6),
            ),
          )
        }
      }
    }
  }

  override fun saveExtractorRule(rule: WebVideoExtractorRule): WebVideoExtractorRule {
    ensureSchema()
    val saved = rule.copy(
      id = rule.id.ifBlank { UUID.randomUUID().toString() },
      urlPattern = rule.urlPattern.trim(),
      titleExtractorCode = rule.titleExtractorCode.cleaned(),
      thumbnailExtractorCode = rule.thumbnailExtractorCode.cleaned(),
      playbackExtractorCode = rule.playbackExtractorCode.cleaned(),
      updatedAtEpochMillis = System.currentTimeMillis(),
    )
    validateWebVideoExtractorRule(saved)
    database.write {
      insertWithOnConflict(
        "video_web_extractor_rules",
        null,
        ContentValues().apply {
          put("id", saved.id)
          put("url_pattern", saved.urlPattern)
          putNullable("title_function", saved.titleExtractorCode)
          putNullable("thumbnail_function", saved.thumbnailExtractorCode)
          putNullable("playback_function", saved.playbackExtractorCode)
          put("timeout_seconds", saved.timeoutSeconds)
          put("updated_at", saved.updatedAtEpochMillis)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }
    return saved
  }

  override fun deleteExtractorRule(id: String) {
    ensureSchema()
    database.write {
      delete("video_web_extractor_rules", "id = ?", arrayOf(id))
    }
  }

  private fun queryItems(): List<VideoItem> = database.readable.rawQuery(
    """
      SELECT i.id, i.source, i.source_id, i.title, i.page_url, i.thumbnail_url,
             i.duration_ms, i.size_bytes, i.mime_type, i.updated_at,
             p.position_ms, p.duration_ms, p.last_played_at, p.completed,
             s.folder_id, s.saved_at
      FROM video_items i
      LEFT JOIN video_playback_state p ON p.video_id = i.id
      LEFT JOIN video_saved_items s ON s.video_id = i.id
      ORDER BY COALESCE(p.last_played_at, 0) DESC, i.updated_at DESC, i.title COLLATE NOCASE
    """.trimIndent(),
    null,
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        add(
          VideoItem(
            id = cursor.getString(0),
            source = VideoSource.valueOf(cursor.getString(1)),
            sourceId = cursor.getString(2),
            title = cursor.getString(3),
            pageUrl = cursor.stringOrNull(4),
            thumbnailUrl = cursor.stringOrNull(5),
            durationMs = cursor.longOrNull(6),
            sizeBytes = cursor.longOrNull(7),
            mimeType = cursor.stringOrNull(8),
            updatedAtEpochMillis = cursor.getLong(9),
            playbackState = cursor.playbackStateOrNull(),
            savedState = cursor.savedStateOrNull(),
          ),
        )
      }
    }
  }

  private fun queryPlaybackState(videoId: String): VideoPlaybackState? = database.readable.rawQuery(
    "SELECT position_ms, duration_ms, last_played_at, completed FROM video_playback_state WHERE video_id = ?",
    arrayOf(videoId),
  ).use { cursor ->
    if (!cursor.moveToFirst()) null else VideoPlaybackState(
      positionMs = cursor.getLong(0),
      durationMs = cursor.getLong(1),
      lastPlayedAtEpochMillis = cursor.getLong(2),
      completed = cursor.getInt(3) != 0,
    )
  }

  private fun queryFolderCreatedAt(id: String): Long? = database.readable.rawQuery(
    "SELECT created_at FROM video_folders WHERE id = ?",
    arrayOf(id),
  ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

  private fun folderNameExists(normalizedName: String, exceptId: String): Boolean = database.readable.rawQuery(
    "SELECT 1 FROM video_folders WHERE normalized_name = ? AND id <> ? LIMIT 1",
    arrayOf(normalizedName, exceptId),
  ).use { it.moveToFirst() }

  private fun folderExists(id: String): Boolean = database.readable.rawQuery(
    "SELECT 1 FROM video_folders WHERE id = ? LIMIT 1",
    arrayOf(id),
  ).use { it.moveToFirst() }

  private fun videoExists(id: String): Boolean = database.readable.rawQuery(
    "SELECT 1 FROM video_items WHERE id = ? LIMIT 1",
    arrayOf(id),
  ).use { it.moveToFirst() }

  private fun querySavedAt(videoId: String): Long? = database.readable.rawQuery(
    "SELECT saved_at FROM video_saved_items WHERE video_id = ?",
    arrayOf(videoId),
  ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

  private fun ensureSchema() {
    ensureVideoSchema(database.writable)
  }

  private companion object {
    const val COMPLETED_RATIO = 0.95
    const val MAX_SMB_VIDEO_ITEMS = 50_000
  }
}

private fun SQLiteDatabase.existingSmbIds(): Set<String> = rawQuery(
  "SELECT id FROM video_items WHERE source = ?",
  arrayOf(VideoSource.SMB.name),
).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }

private fun SQLiteDatabase.upsertVideoItem(item: VideoItem) {
  val values = ContentValues().apply {
    put("id", item.id)
    put("source", item.source.name)
    put("source_id", item.sourceId)
    put("title", item.title)
    putNullable("page_url", item.pageUrl)
    putNullable("thumbnail_url", item.thumbnailUrl)
    putNullable("duration_ms", item.durationMs)
    putNullable("size_bytes", item.sizeBytes)
    putNullable("mime_type", item.mimeType)
    put("updated_at", item.updatedAtEpochMillis)
  }
  val updated = update("video_items", values, "id = ?", arrayOf(item.id))
  if (updated == 0) insertOrThrow("video_items", null, values)
}

private fun SQLiteDatabase.migratePlaybackStateIfAbsent(
  legacyVideoId: String,
  targetVideoId: String,
) {
  if (legacyVideoId == targetVideoId) return
  execSQL(
    """
      INSERT OR IGNORE INTO video_playback_state(
        video_id, position_ms, duration_ms, last_played_at, completed
      )
      SELECT ?, position_ms, duration_ms, last_played_at, completed
      FROM video_playback_state
      WHERE video_id = ?
    """.trimIndent(),
    arrayOf(targetVideoId, legacyVideoId),
  )
}

private fun SmbMediaFile.toVideoItem(now: Long): VideoItem {
  val sourceId = smbVideoSourceId(serverId, share, path)
  return VideoItem(
    id = stableVideoId(VideoSource.SMB, sourceId),
    source = VideoSource.SMB,
    sourceId = sourceId,
    title = name.substringBeforeLast('.').ifBlank { name },
    sizeBytes = size,
    mimeType = videoMimeType(name),
    updatedAtEpochMillis = maxOf(now, modifiedAtEpochMillis),
  )
}

private fun normalizeVideoSmbRoot(path: String): String {
  val segments = path
    .replace('/', '\\')
    .split('\\')
    .filter { it.isNotBlank() && it != "." }
  require(".." !in segments) { "SMB動画のパスに .. は使用できません" }
  return segments.joinToString("\\")
}

private fun Cursor.playbackStateOrNull(): VideoPlaybackState? = if (isNull(10)) {
  null
} else {
  VideoPlaybackState(
    positionMs = getLong(10),
    durationMs = getLong(11),
    lastPlayedAtEpochMillis = getLong(12),
    completed = getInt(13) != 0,
  )
}

private fun Cursor.savedStateOrNull(): VideoSavedState? = if (isNull(15)) {
  null
} else {
  VideoSavedState(
    folderId = stringOrNull(14),
    savedAtEpochMillis = getLong(15),
  )
}

private fun Cursor.stringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
private fun Cursor.longOrNull(index: Int): Long? = if (isNull(index)) null else getLong(index)
private fun String?.cleaned(): String? = this?.trim()?.takeIf(String::isNotBlank)

private fun ContentValues.putNullable(key: String, value: String?) {
  if (value == null) putNull(key) else put(key, value)
}

private fun ContentValues.putNullable(key: String, value: Long?) {
  if (value == null) putNull(key) else put(key, value)
}
