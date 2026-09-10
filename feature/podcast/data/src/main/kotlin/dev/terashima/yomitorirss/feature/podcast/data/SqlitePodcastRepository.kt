package dev.terashima.yomitorirss.feature.podcast.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.podcast.PodcastChapterGenerationStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastEpisode
import dev.terashima.yomitorirss.feature.podcast.PodcastEpisodeArticle
import dev.terashima.yomitorirss.feature.podcast.PodcastEpisodeStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTask
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTaskReader
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTaskState
import dev.terashima.yomitorirss.feature.podcast.PodcastProgram
import dev.terashima.yomitorirss.feature.podcast.PodcastRegenerationStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastRepository
import dev.terashima.yomitorirss.feature.podcast.PodcastSchedule
import dev.terashima.yomitorirss.feature.podcast.PodcastSource
import java.util.UUID

class SqlitePodcastRepository(
  private val database: DatabaseConnection,
) : PodcastRepository, PodcastGenerationTaskReader {
  override suspend fun listSources(): List<PodcastSource> = database.readable.rawQuery(
    "SELECT * FROM podcast_sources ORDER BY name COLLATE NOCASE",
    null,
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.source()) } }

  override suspend fun findSource(sourceId: String): PodcastSource? = database.readable.rawQuery(
    "SELECT * FROM podcast_sources WHERE id=? LIMIT 1",
    arrayOf(sourceId),
  ).use { cursor -> if (cursor.moveToFirst()) cursor.source() else null }

  override suspend fun saveSource(source: PodcastSource) {
    database.write {
      val updated = update(
        "podcast_sources",
        source.values(),
        "id=?",
        arrayOf(source.id),
      )
      if (updated == 0) {
        insertOrThrow("podcast_sources", null, source.values())
      }
    }
  }

  override suspend fun deleteSource(sourceId: String) {
    database.transaction {
      val referenced = rawQuery(
        "SELECT source_ids FROM podcast_programs",
        null,
      ).use { cursor ->
        var found = false
        while (!found && cursor.moveToNext()) {
          found = sourceId in cursor.getString(0).lineSequence().filter(String::isNotBlank).toSet()
        }
        found
      }
      require(!referenced) { "番組で利用中のソースは削除できません" }
      delete("podcast_sources", "id=?", arrayOf(sourceId))
    }
  }

  override suspend fun listPrograms(): List<PodcastProgram> = database.readable.rawQuery(
    "SELECT * FROM podcast_programs ORDER BY name COLLATE NOCASE",
    null,
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.program()) } }

  override suspend fun findProgram(programId: String): PodcastProgram? = database.readable.rawQuery(
    "SELECT * FROM podcast_programs WHERE id=? LIMIT 1",
    arrayOf(programId),
  ).use { cursor -> cursor.programOrNull() }

  override suspend fun saveProgram(program: PodcastProgram) {
    database.transaction {
      require(allSourcesExist(this, program.sourceIds)) { "利用できないソースが含まれています" }
      val updated = update(
        "podcast_programs",
        program.values(),
        "id=?",
        arrayOf(program.id),
      )
      if (updated == 0) {
        insertOrThrow("podcast_programs", null, program.values())
      }
    }
  }

  override suspend fun deleteProgram(programId: String) {
    database.write { delete("podcast_programs", "id=?", arrayOf(programId)) }
  }

  override suspend fun listEpisodes(programId: String): List<PodcastEpisode> = database.readable.rawQuery(
    "SELECT * FROM podcast_episodes WHERE program_id=? ORDER BY created_at DESC",
    arrayOf(programId),
  ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.episode(database)) } }

  override suspend fun findEpisode(episodeId: String): PodcastEpisode? = database.readable.rawQuery(
    "SELECT * FROM podcast_episodes WHERE id=? LIMIT 1",
    arrayOf(episodeId),
  ).use { cursor -> if (cursor.moveToFirst()) cursor.episode(database) else null }

  override suspend fun archiveEpisode(episodeId: String): PodcastEpisode {
    database.write {
      val updated = update(
        "podcast_episodes",
        ContentValues().apply {
          put("status", PodcastEpisodeStatus.ARCHIVED.name)
          putNull("regeneration_status")
          putNull("error_message")
        },
        "id=? AND status=? AND (regeneration_status IS NULL OR regeneration_status=?)",
        arrayOf(
          episodeId,
          PodcastEpisodeStatus.READY.name,
          PodcastRegenerationStatus.FAILED.name,
        ),
      )
      require(updated == 1) { "再生成中でない再生可能なエピソードだけアーカイブできます" }
    }
    return requireNotNull(findEpisode(episodeId))
  }

  override suspend fun restoreEpisode(episodeId: String): PodcastEpisode {
    database.write {
      val updated = update(
        "podcast_episodes",
        ContentValues().apply { put("status", PodcastEpisodeStatus.READY.name) },
        "id=? AND status=?",
        arrayOf(episodeId, PodcastEpisodeStatus.ARCHIVED.name),
      )
      require(updated == 1) { "アーカイブ済みエピソードだけ復元できます" }
    }
    return requireNotNull(findEpisode(episodeId))
  }

  override suspend fun deleteEpisode(episodeId: String) {
    database.transaction {
      val lifecycle = rawQuery(
        "SELECT status,regeneration_status FROM podcast_episodes WHERE id=? LIMIT 1",
        arrayOf(episodeId),
      ).use { cursor ->
        if (!cursor.moveToFirst()) {
          null
        } else {
          PodcastEpisodeStatus.valueOf(cursor.getString(0)) to
            if (cursor.isNull(1)) null else PodcastRegenerationStatus.valueOf(cursor.getString(1))
        }
      } ?: error("episode not found: $episodeId")
      val (status, regeneration) = lifecycle
      require(regeneration != PodcastRegenerationStatus.RUNNING) { "再生成中のエピソードは削除できません" }
      require(
        status == PodcastEpisodeStatus.READY ||
          status == PodcastEpisodeStatus.ARCHIVED ||
          status == PodcastEpisodeStatus.FAILED,
      ) { "生成待ちまたは生成中のエピソードは削除できません" }

      val updated = update(
        "podcast_episodes",
        ContentValues().apply {
          put("title", "")
          put("status", PodcastEpisodeStatus.DELETED.name)
          putNull("script")
          putNull("error_message")
          putNull("regeneration_status")
        },
        "id=?",
        arrayOf(episodeId),
      )
      check(updated == 1) { "episode could not be deleted: $episodeId" }
      delete("podcast_episode_articles", "episode_id=?", arrayOf(episodeId))
    }
  }

  override suspend fun listGenerationTasks(): List<PodcastGenerationTask> = database.readable.rawQuery(
    """
      SELECT e.id,e.title,e.created_at,e.status,e.error_message,e.regeneration_status,
             p.name AS program_name,p.provider,
             COUNT(a.position) AS total_chapters,
             COALESCE(SUM(CASE
               WHEN a.chapter_status='READY' AND a.chapter_script IS NOT NULL AND TRIM(a.chapter_script)<>'' THEN 1
               ELSE 0
             END),0) AS completed_chapters,
             MAX(CASE WHEN a.chapter_status='FAILED' THEN a.chapter_error END) AS chapter_error
      FROM podcast_episodes e
      JOIN podcast_programs p ON p.id=e.program_id
      LEFT JOIN podcast_episode_articles a ON a.episode_id=e.id
      WHERE e.status IN (?,?,?) OR e.regeneration_status IS NOT NULL
      GROUP BY e.id,e.title,e.created_at,e.status,e.error_message,e.regeneration_status,p.name,p.provider
      ORDER BY e.created_at DESC,e.id DESC
    """.trimIndent(),
    arrayOf(
      PodcastEpisodeStatus.QUEUED.name,
      PodcastEpisodeStatus.GENERATING.name,
      PodcastEpisodeStatus.FAILED.name,
    ),
  ).use { cursor ->
    buildList {
      while (cursor.moveToNext()) {
        val status = PodcastEpisodeStatus.valueOf(cursor.string("status"))
        val regeneration = cursor.nullableString("regeneration_status")?.let(PodcastRegenerationStatus::valueOf)
        val state = when {
          regeneration == PodcastRegenerationStatus.RUNNING -> PodcastGenerationTaskState.RUNNING
          regeneration == PodcastRegenerationStatus.FAILED -> PodcastGenerationTaskState.FAILED
          status == PodcastEpisodeStatus.QUEUED -> PodcastGenerationTaskState.QUEUED
          status == PodcastEpisodeStatus.GENERATING -> PodcastGenerationTaskState.RUNNING
          status == PodcastEpisodeStatus.FAILED -> PodcastGenerationTaskState.FAILED
          else -> continue
        }
        add(
          PodcastGenerationTask(
            episodeId = cursor.string("id"),
            title = cursor.string("title"),
            programName = cursor.string("program_name"),
            provider = PodcastGenerationProvider.valueOf(cursor.string("provider")),
            state = state,
            completedChapters = cursor.int("completed_chapters"),
            totalChapters = cursor.int("total_chapters"),
            error = cursor.nullableString("chapter_error") ?: cursor.nullableString("error_message"),
            createdAtEpochMillis = cursor.long("created_at"),
          ),
        )
      }
    }
  }

  override suspend fun findInterruptedGenerationEpisode(programId: String): PodcastEpisode? =
    database.readable.rawQuery(
      "SELECT * FROM podcast_episodes " +
        "WHERE program_id=? AND (status=? OR regeneration_status=?) " +
        "ORDER BY created_at,id LIMIT 1",
      arrayOf(
        programId,
        PodcastEpisodeStatus.GENERATING.name,
        PodcastRegenerationStatus.RUNNING.name,
      ),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.episode(database) else null }

  override suspend fun claimPendingEpisode(programId: String): PodcastEpisode? {
    val episodeId = database.transaction {
      val pending = rawQuery(
        "SELECT id,status FROM podcast_episodes " +
          "WHERE program_id=? AND (status IN (?,?) OR regeneration_status=?) " +
          "ORDER BY CASE WHEN status=? OR regeneration_status=? THEN 0 ELSE 1 END, created_at, id LIMIT 1",
        arrayOf(
          programId,
          PodcastEpisodeStatus.GENERATING.name,
          PodcastEpisodeStatus.QUEUED.name,
          PodcastRegenerationStatus.RUNNING.name,
          PodcastEpisodeStatus.GENERATING.name,
          PodcastRegenerationStatus.RUNNING.name,
        ),
      ).use { cursor ->
        if (!cursor.moveToFirst()) null else cursor.getString(0) to PodcastEpisodeStatus.valueOf(cursor.getString(1))
      } ?: return@transaction null

      if (pending.second == PodcastEpisodeStatus.QUEUED) {
        val updated = update(
          "podcast_episodes",
          ContentValues().apply { put("status", PodcastEpisodeStatus.GENERATING.name) },
          "id=? AND status=?",
          arrayOf(pending.first, PodcastEpisodeStatus.QUEUED.name),
        )
        check(updated == 1) { "queued episode could not be claimed: ${pending.first}" }
      }
      pending.first
    }
    return episodeId?.let { findEpisode(it) }
  }

  override suspend fun reserveEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
  ): PodcastEpisode? = database.transaction {
    val pendingCandidates = candidates
      .distinctBy(PodcastFeedEntry::articleId)
      .filterNot { candidate -> isConsumed(this, program.id, candidate.articleId) }
    if (pendingCandidates.isEmpty()) return@transaction null

    var firstEpisode: PodcastEpisode? = null
    pendingCandidates.chunked(program.maxArticlesPerEpisode).forEachIndexed { chunkIndex, selected ->
      val episodeId = UUID.randomUUID().toString()
      val status = if (chunkIndex == 0) PodcastEpisodeStatus.GENERATING else PodcastEpisodeStatus.QUEUED
      val episodeCreatedAt = createdAtEpochMillis + chunkIndex
      insertOrThrow(
        "podcast_episodes",
        null,
        ContentValues().apply {
          put("id", episodeId)
          put("program_id", program.id)
          put("title", program.name)
          put("created_at", episodeCreatedAt)
          put("status", status.name)
        },
      )
      selected.forEachIndexed { index, article ->
        insertOrThrow(
          "podcast_episode_articles",
          null,
          ContentValues().apply {
            put("episode_id", episodeId)
            put("position", index)
            put("article_id", article.articleId)
            put("feed_id", article.feedId)
            put("title", article.title)
            if (article.sourceTitle == null) putNull("source_title") else put("source_title", article.sourceTitle)
            if (article.publishedAtEpochMillis == null) putNull("published_at") else put("published_at", article.publishedAtEpochMillis)
            put("article_url", article.articleUrl)
            put("feed_content", article.feedContent)
            put("chapter_status", PodcastChapterGenerationStatus.PENDING.name)
          },
        )
        insertOrThrow(
          "podcast_consumed_articles",
          null,
          ContentValues().apply {
            put("program_id", program.id)
            put("article_id", article.articleId)
            put("episode_id", episodeId)
            put("consumed_at", createdAtEpochMillis)
          },
        )
      }
      val episode = PodcastEpisode(
        id = episodeId,
        programId = program.id,
        title = program.name,
        createdAtEpochMillis = episodeCreatedAt,
        status = status,
        articles = selected.map(PodcastFeedEntry::toEpisodeArticle),
      )
      if (firstEpisode == null) firstEpisode = episode
    }
    firstEpisode
  }

  override suspend fun prepareEpisodeRetry(episodeId: String): PodcastEpisode {
    database.write {
      val updated = update(
        "podcast_episodes",
        ContentValues().apply {
          put("status", PodcastEpisodeStatus.GENERATING.name)
          putNull("error_message")
          putNull("regeneration_status")
        },
        "id=? AND status=?",
        arrayOf(episodeId, PodcastEpisodeStatus.FAILED.name),
      )
      require(updated == 1) { "failed episode not found: $episodeId" }
    }
    return requireNotNull(findEpisode(episodeId))
  }

  override suspend fun prepareEpisodeRegeneration(episodeId: String): PodcastEpisode {
    database.transaction {
      val regeneration = rawQuery(
        "SELECT status,regeneration_status FROM podcast_episodes WHERE id=? LIMIT 1",
        arrayOf(episodeId),
      ).use { cursor ->
        require(cursor.moveToFirst()) { "episode not found: $episodeId" }
        require(PodcastEpisodeStatus.valueOf(cursor.getString(0)) == PodcastEpisodeStatus.READY) {
          "only ready episodes can be regenerated"
        }
        if (cursor.isNull(1)) null else PodcastRegenerationStatus.valueOf(cursor.getString(1))
      }
      require(regeneration != PodcastRegenerationStatus.RUNNING) { "episode regeneration already running: $episodeId" }
      if (regeneration == null) {
        update(
          "podcast_episode_articles",
          ContentValues().apply {
            put("chapter_status", PodcastChapterGenerationStatus.PENDING.name)
            putNull("chapter_script")
            putNull("chapter_error")
          },
          "episode_id=?",
          arrayOf(episodeId),
        )
      }
      val updated = update(
        "podcast_episodes",
        ContentValues().apply {
          put("regeneration_status", PodcastRegenerationStatus.RUNNING.name)
          putNull("error_message")
        },
        "id=?",
        arrayOf(episodeId),
      )
      require(updated == 1) { "episode not found: $episodeId" }
    }
    return requireNotNull(findEpisode(episodeId))
  }

  override suspend fun markChapterGenerating(episodeId: String, position: Int): PodcastEpisode =
    updateChapter(
      episodeId = episodeId,
      position = position,
      values = ContentValues().apply {
        put("chapter_status", PodcastChapterGenerationStatus.GENERATING.name)
        putNull("chapter_error")
      },
    )

  override suspend fun completeChapter(episodeId: String, position: Int, script: String): PodcastEpisode =
    updateChapter(
      episodeId = episodeId,
      position = position,
      values = ContentValues().apply {
        put("chapter_status", PodcastChapterGenerationStatus.READY.name)
        put("chapter_script", script)
        putNull("chapter_error")
      },
    )

  override suspend fun failChapter(episodeId: String, position: Int, message: String): PodcastEpisode =
    updateChapter(
      episodeId = episodeId,
      position = position,
      values = ContentValues().apply {
        put("chapter_status", PodcastChapterGenerationStatus.FAILED.name)
        put("chapter_error", message.take(1000))
      },
    )

  override suspend fun failRegeneration(episodeId: String, message: String): PodcastEpisode {
    database.write {
      val updated = update(
        "podcast_episodes",
        ContentValues().apply {
          put("regeneration_status", PodcastRegenerationStatus.FAILED.name)
          put("error_message", message.take(1000))
        },
        "id=? AND status=?",
        arrayOf(episodeId, PodcastEpisodeStatus.READY.name),
      )
      require(updated == 1) { "ready episode not found: $episodeId" }
    }
    return requireNotNull(findEpisode(episodeId))
  }

  override suspend fun completeEpisode(episodeId: String, title: String, script: String): PodcastEpisode {
    database.write {
      val updated = update(
        "podcast_episodes",
        ContentValues().apply {
          put("title", title)
          put("status", PodcastEpisodeStatus.READY.name)
          put("script", script)
          putNull("error_message")
          putNull("regeneration_status")
        },
        "id=?",
        arrayOf(episodeId),
      )
      require(updated == 1) { "episode not found: $episodeId" }
    }
    return requireNotNull(findEpisode(episodeId))
  }

  override suspend fun failEpisode(episodeId: String, message: String): PodcastEpisode {
    database.write {
      val updated = update(
        "podcast_episodes",
        ContentValues().apply {
          put("status", PodcastEpisodeStatus.FAILED.name)
          put("error_message", message.take(1000))
          putNull("regeneration_status")
        },
        "id=?",
        arrayOf(episodeId),
      )
      require(updated == 1) { "episode not found: $episodeId" }
    }
    return requireNotNull(findEpisode(episodeId))
  }

  private suspend fun updateChapter(
    episodeId: String,
    position: Int,
    values: ContentValues,
  ): PodcastEpisode {
    database.write {
      val updated = update(
        "podcast_episode_articles",
        values,
        "episode_id=? AND position=?",
        arrayOf(episodeId, position.toString()),
      )
      require(updated == 1) { "episode chapter not found: $episodeId/$position" }
    }
    return requireNotNull(findEpisode(episodeId))
  }
}

private fun PodcastSource.values(): ContentValues = ContentValues().apply {
  put("id", id)
  put("name", name)
  put("feed_url", feedUrl)
}

private fun PodcastProgram.values(): ContentValues = ContentValues().apply {
  put("id", id)
  put("name", name)
  put("source_ids", sourceIds.sorted().joinToString("\n"))
  put("provider", provider.name)
  put("schedule_enabled", if (schedule.enabled) 1 else 0)
  put("schedule_hour", schedule.hour)
  put("schedule_minute", schedule.minute)
  put("max_articles", maxArticlesPerEpisode)
}

private fun Cursor.source(): PodcastSource = PodcastSource(
  id = string("id"),
  name = string("name"),
  feedUrl = string("feed_url"),
)

private fun Cursor.programOrNull(): PodcastProgram? = if (moveToFirst()) program() else null

private fun Cursor.program(): PodcastProgram = PodcastProgram(
  id = string("id"),
  name = string("name"),
  sourceIds = string("source_ids").lineSequence().filter(String::isNotBlank).toSet(),
  provider = PodcastGenerationProvider.valueOf(string("provider")),
  schedule = PodcastSchedule(
    enabled = int("schedule_enabled") != 0,
    hour = int("schedule_hour"),
    minute = int("schedule_minute"),
  ),
  maxArticlesPerEpisode = int("max_articles"),
)

private fun Cursor.episode(database: DatabaseConnection): PodcastEpisode {
  val episodeId = string("id")
  return PodcastEpisode(
    id = episodeId,
    programId = string("program_id"),
    title = string("title"),
    createdAtEpochMillis = long("created_at"),
    status = PodcastEpisodeStatus.valueOf(string("status")),
    articles = database.readable.rawQuery(
      "SELECT * FROM podcast_episode_articles WHERE episode_id=? ORDER BY position",
      arrayOf(episodeId),
    ).use { articleCursor ->
      buildList {
        while (articleCursor.moveToNext()) {
          add(
            PodcastEpisodeArticle(
              articleId = articleCursor.string("article_id"),
              feedId = articleCursor.string("feed_id"),
              title = articleCursor.string("title"),
              sourceTitle = articleCursor.nullableString("source_title"),
              publishedAtEpochMillis = articleCursor.nullableLong("published_at"),
              articleUrl = articleCursor.nullableString("article_url"),
              feedContent = articleCursor.string("feed_content"),
              chapterStatus = PodcastChapterGenerationStatus.valueOf(articleCursor.string("chapter_status")),
              chapterScript = articleCursor.nullableString("chapter_script"),
              chapterError = articleCursor.nullableString("chapter_error"),
            ),
          )
        }
      }
    },
    script = nullableString("script"),
    errorMessage = nullableString("error_message"),
    regenerationStatus = nullableString("regeneration_status")?.let(PodcastRegenerationStatus::valueOf),
  )
}

private fun PodcastFeedEntry.toEpisodeArticle() = PodcastEpisodeArticle(
  articleId = articleId,
  feedId = feedId,
  title = title,
  sourceTitle = sourceTitle,
  publishedAtEpochMillis = publishedAtEpochMillis,
  articleUrl = articleUrl,
  feedContent = feedContent,
)

private fun allSourcesExist(db: SQLiteDatabase, sourceIds: Set<String>): Boolean {
  val ids = sourceIds.toList()
  val placeholders = ids.joinToString(",") { "?" }
  val found = db.rawQuery(
    "SELECT id FROM podcast_sources WHERE id IN($placeholders)",
    ids.toTypedArray(),
  ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
  return found.size == ids.size
}

private fun isConsumed(db: SQLiteDatabase, programId: String, articleId: String): Boolean = db.rawQuery(
  "SELECT 1 FROM podcast_consumed_articles WHERE program_id=? AND article_id=? LIMIT 1",
  arrayOf(programId, articleId),
).use(Cursor::moveToFirst)

private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
private fun Cursor.int(column: String): Int = getInt(getColumnIndexOrThrow(column))
private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
private fun Cursor.nullableString(column: String): String? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getString(it) }
private fun Cursor.nullableLong(column: String): Long? = getColumnIndexOrThrow(column).let { if (isNull(it)) null else getLong(it) }
