package dev.terashima.yomitorirss.feature.podcast.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.database.PersistenceChangeNotifier
import dev.terashima.yomitorirss.feature.podcast.PodcastChapterGenerationStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastClusteringStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastClusteringMode
import dev.terashima.yomitorirss.feature.podcast.PodcastEpisode
import dev.terashima.yomitorirss.feature.podcast.PodcastEpisodeArticle
import dev.terashima.yomitorirss.feature.podcast.PodcastEpisodeStatus
import dev.terashima.yomitorirss.feature.podcast.PodcastFeedEntry
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationProvider
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTask
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTaskReader
import dev.terashima.yomitorirss.feature.podcast.PodcastGenerationTaskState
import dev.terashima.yomitorirss.feature.podcast.PodcastProgram
import dev.terashima.yomitorirss.feature.podcast.PodcastRepository
import dev.terashima.yomitorirss.feature.podcast.PodcastSchedule
import dev.terashima.yomitorirss.feature.podcast.PodcastSource
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlitePodcastRepository(
  private val database: DatabaseConnection,
  persistenceChanges: PersistenceChangeNotifier = PersistenceChangeNotifier.shared,
) : PodcastRepository, PodcastGenerationTaskReader {
  override val changes: Flow<Unit> = persistenceChanges.version.map { Unit }
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
      val updated = update("podcast_sources", source.values(), "id=?", arrayOf(source.id))
      if (updated == 0) insertOrThrow("podcast_sources", null, source.values())
    }
  }

  override suspend fun deleteSource(sourceId: String) {
    database.transaction {
      val referenced = rawQuery("SELECT source_ids FROM podcast_programs", null).use { cursor ->
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
      val updated = update("podcast_programs", program.values(), "id=?", arrayOf(program.id))
      if (updated == 0) insertOrThrow("podcast_programs", null, program.values())
    }
  }

  override suspend fun deleteProgram(programId: String) {
    database.write { delete("podcast_programs", "id=?", arrayOf(programId)) }
  }

  override suspend fun recordExcludedEntries(
    programId: String,
    entries: List<PodcastFeedEntry>,
    excludedAtEpochMillis: Long,
  ) {
    if (entries.isEmpty()) return
    database.transaction {
      entries.distinctBy(PodcastFeedEntry::articleId).forEach { entry ->
        execSQL(
          "INSERT OR IGNORE INTO podcast_excluded_articles(program_id,article_id,excluded_at) VALUES(?,?,?)",
          arrayOf<Any>(programId, entry.articleId, excludedAtEpochMillis),
        )
      }
    }
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
          putNull("last_played_chapter")
          putNull("error_message")
        },
        "id=? AND status=?",
        arrayOf(episodeId, PodcastEpisodeStatus.READY.name),
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

  override suspend fun recordPlaybackChapter(episodeId: String, chapterNumber: Int) {
    require(chapterNumber > 0) { "chapter number must be positive" }
    database.write {
      update(
        "podcast_episodes",
        ContentValues().apply { put("last_played_chapter", chapterNumber) },
        "id=? AND status=? AND (last_played_chapter IS NULL OR last_played_chapter!=?)",
        arrayOf(episodeId, PodcastEpisodeStatus.READY.name, chapterNumber.toString()),
      )
    }
  }

  override suspend fun deleteEpisode(episodeId: String) {
    database.transaction {
      val status = rawQuery(
        "SELECT status FROM podcast_episodes WHERE id=? LIMIT 1",
        arrayOf(episodeId),
      ).use { cursor ->
        if (!cursor.moveToFirst()) null else PodcastEpisodeStatus.valueOf(cursor.getString(0))
      } ?: error("episode not found: $episodeId")
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
          putNull("clustering_status")
          putNull("last_played_chapter")
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
      SELECT e.id,e.title,e.created_at,e.status,e.error_message,
             p.name AS program_name,p.provider,
             COUNT(DISTINCT COALESCE(a.chapter_position,a.position)) AS total_chapters,
             COUNT(DISTINCT CASE
               WHEN a.chapter_status='READY' AND a.chapter_script IS NOT NULL AND TRIM(a.chapter_script)<>''
               THEN COALESCE(a.chapter_position,a.position)
             END) AS completed_chapters,
             MAX(CASE WHEN a.chapter_status='FAILED' THEN a.chapter_error END) AS chapter_error
      FROM podcast_episodes e
      JOIN podcast_programs p ON p.id=e.program_id
      LEFT JOIN podcast_episode_articles a ON a.episode_id=e.id
      WHERE e.status IN (?,?,?)
      GROUP BY e.id,e.title,e.created_at,e.status,e.error_message,p.name,p.provider
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
        val state = when {
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
      "SELECT * FROM podcast_episodes WHERE program_id=? AND status=? ORDER BY created_at,id LIMIT 1",
      arrayOf(programId, PodcastEpisodeStatus.GENERATING.name),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.episode(database) else null }

  override suspend fun claimPendingEpisode(programId: String): PodcastEpisode? {
    val episodeId = database.transaction {
      val pending = rawQuery(
        "SELECT id,status FROM podcast_episodes " +
          "WHERE program_id=? AND status IN (?,?) " +
          "ORDER BY CASE WHEN status=? THEN 0 ELSE 1 END, created_at, id LIMIT 1",
        arrayOf(
          programId,
          PodcastEpisodeStatus.GENERATING.name,
          PodcastEpisodeStatus.QUEUED.name,
          PodcastEpisodeStatus.GENERATING.name,
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
  ): PodcastEpisode? = reserveEpisode(program, candidates, createdAtEpochMillis, null)

  override suspend fun reserveEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
    clusteringStatus: PodcastClusteringStatus?,
  ): PodcastEpisode? = database.transaction {
    val pendingCandidates = candidates
      .distinctBy(PodcastFeedEntry::articleId)
      .filterNot { candidate -> isProcessed(this, program.id, candidate.articleId) }
    if (pendingCandidates.isEmpty()) return@transaction null

    val clusteredCandidates = pendingCandidates
      .mapIndexed { index, candidate -> (candidate.chapterPosition ?: index) to candidate }
      .groupBy({ it.first }, { it.second })
      .toSortedMap()
      .values
      .toList()

    var firstEpisode: PodcastEpisode? = null
    clusteredCandidates.chunked(program.maxArticlesPerEpisode).forEachIndexed { chunkIndex, selectedClusters ->
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
          put("clustering_mode", program.clusteringMode.name)
          if (clusteringStatus == null) putNull("clustering_status") else put("clustering_status", clusteringStatus.name)
        },
      )

      val episodeArticles = mutableListOf<PodcastEpisodeArticle>()
      var articlePosition = 0
      selectedClusters.forEachIndexed { chapterPosition, cluster ->
        cluster.forEach { article ->
          insertOrThrow(
            "podcast_episode_articles",
            null,
            ContentValues().apply {
              put("episode_id", episodeId)
              put("position", articlePosition)
              put("chapter_position", chapterPosition)
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
          episodeArticles += article.toEpisodeArticle(chapterPosition)
          articlePosition += 1
        }
      }

      val episode = PodcastEpisode(
        id = episodeId,
        programId = program.id,
        title = program.name,
        createdAtEpochMillis = episodeCreatedAt,
        status = status,
        articles = episodeArticles,
        clusteringStatus = clusteringStatus,
        clusteringMode = program.clusteringMode,
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
        },
        "id=? AND status=?",
        arrayOf(episodeId, PodcastEpisodeStatus.FAILED.name),
      )
      require(updated == 1) { "failed episode not found: $episodeId" }
    }
    return requireNotNull(findEpisode(episodeId))
  }

  override suspend fun prepareEpisodeRebuild(
    episodeId: String,
    candidates: List<PodcastFeedEntry>,
    clusteringStatus: PodcastClusteringStatus,
  ): PodcastEpisode {
    require(candidates.isNotEmpty()) { "rebuild candidates must not be empty" }
    database.transaction {
      val state = rawQuery(
        "SELECT status FROM podcast_episodes WHERE id=? LIMIT 1",
        arrayOf(episodeId),
      ).use { cursor ->
        require(cursor.moveToFirst()) { "episode not found: $episodeId" }
        PodcastEpisodeStatus.valueOf(cursor.getString(0))
      }
      require(state == PodcastEpisodeStatus.READY || state == PodcastEpisodeStatus.FAILED) {
        "only ready or failed episodes can be rebuilt"
      }

      delete("podcast_episode_articles", "episode_id=?", arrayOf(episodeId))
      candidates.forEachIndexed { position, article ->
        insertOrThrow(
          "podcast_episode_articles",
          null,
          ContentValues().apply {
            put("episode_id", episodeId)
            put("position", position)
            if (article.chapterPosition == null) putNull("chapter_position") else put("chapter_position", article.chapterPosition)
            put("article_id", article.articleId)
            put("feed_id", article.feedId)
            put("title", article.title)
            if (article.sourceTitle == null) putNull("source_title") else put("source_title", article.sourceTitle)
            if (article.publishedAtEpochMillis == null) putNull("published_at") else put("published_at", article.publishedAtEpochMillis)
            if (article.articleUrl == null) putNull("article_url") else put("article_url", article.articleUrl)
            put("feed_content", article.feedContent)
            put("chapter_status", PodcastChapterGenerationStatus.PENDING.name)
            putNull("chapter_script")
            putNull("chapter_error")
          },
        )
      }

      val updated = update(
        "podcast_episodes",
        ContentValues().apply {
          put("status", PodcastEpisodeStatus.GENERATING.name)
          putNull("script")
          putNull("error_message")
          putNull("last_played_chapter")
          put("clustering_status", clusteringStatus.name)
        },
        "id=?",
        arrayOf(episodeId),
      )
      require(updated == 1) { "episode not found: $episodeId" }
    }
    return requireNotNull(findEpisode(episodeId))
  }

  override suspend fun reserveComparisonEpisode(
    program: PodcastProgram,
    candidates: List<PodcastFeedEntry>,
    createdAtEpochMillis: Long,
    clusteringStatus: PodcastClusteringStatus,
  ): PodcastEpisode {
    require(candidates.isNotEmpty()) { "comparison requires article snapshot" }
    val episodeId = UUID.randomUUID().toString()
    database.transaction {
      insertOrThrow(
        "podcast_episodes",
        null,
        ContentValues().apply {
          put("id", episodeId)
          put("program_id", program.id)
          put("title", program.name)
          put("created_at", createdAtEpochMillis)
          put("status", PodcastEpisodeStatus.GENERATING.name)
          put("clustering_status", clusteringStatus.name)
          put("clustering_mode", program.clusteringMode.name)
        },
      )
      candidates.forEachIndexed { index, article ->
        insertOrThrow(
          "podcast_episode_articles",
          null,
          ContentValues().apply {
            put("episode_id", episodeId)
            put("position", index)
            put("chapter_position", article.chapterPosition ?: index)
            put("article_id", article.articleId)
            put("feed_id", article.feedId)
            put("title", article.title)
            if (article.sourceTitle == null) putNull("source_title") else put("source_title", article.sourceTitle)
            if (article.publishedAtEpochMillis == null) putNull("published_at") else put("published_at", article.publishedAtEpochMillis)
            if (article.articleUrl == null) putNull("article_url") else put("article_url", article.articleUrl)
            put("feed_content", article.feedContent)
            put("chapter_status", PodcastChapterGenerationStatus.PENDING.name)
          },
        )
      }
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

  override suspend fun completeEpisode(episodeId: String, title: String, script: String): PodcastEpisode {
    database.write {
      val updated = update(
        "podcast_episodes",
        ContentValues().apply {
          put("title", title)
          put("status", PodcastEpisodeStatus.READY.name)
          put("script", script)
          putNull("error_message")
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
        "episode_id=? AND COALESCE(chapter_position,position)=CAST(? AS INTEGER)",
        arrayOf(episodeId, position.toString()),
      )
      require(updated >= 1) { "episode chapter not found: $episodeId/$position" }
    }
    return requireNotNull(findEpisode(episodeId))
  }
}

