package dev.terashima.yomitorirss.feature.rss.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.rss.RssRecommendationAssessment
import dev.terashima.yomitorirss.feature.rss.RssRecommendationFeedback
import dev.terashima.yomitorirss.feature.rss.RssRecommendationPolicy
import dev.terashima.yomitorirss.feature.rss.RssRecommendationRepository
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTask
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTaskState
import dev.terashima.yomitorirss.feature.rss.RssRecommendationUnscoredReason
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

class DefaultRssRecommendationRepository(
  private val database: DatabaseConnection,
  private val dataChanges: DataChangeNotifier = DataChangeNotifier(),
) : RssRecommendationRepository {
  override val changes: StateFlow<Long> = dataChanges.version
  override fun loadPolicy(): RssRecommendationPolicy {
    ensureRssRecommendationSchema(database.writable)
    return database.readable.rawQuery(
      "SELECT manual_condition, learned_condition, revision FROM rss_recommendation_policy WHERE id=1",
      emptyArray<String>(),
    ).use { cursor ->
      if (!cursor.moveToFirst()) {
        RssRecommendationPolicy()
      } else {
        RssRecommendationPolicy(
          manualCondition = cursor.getString(0),
          learnedCondition = cursor.getString(1),
          revision = cursor.getLong(2),
        )
      }
    }
  }

  override fun saveManualCondition(condition: String): RssRecommendationPolicy {
    ensureRssRecommendationSchema(database.writable)
    val normalized = condition.trim()
    require(normalized.length <= MAX_RECOMMENDATION_CONDITION_LENGTH) { "除外条件が長すぎます" }
    val current = loadPolicy()
    if (current.manualCondition == normalized) return current
    return current.copy(manualCondition = normalized, revision = current.revision + 1).also { policy ->
      savePolicy(policy)
      dataChanges.notifyChanged()
    }
  }

  override fun resetLearnedCondition(): RssRecommendationPolicy {
    ensureRssRecommendationSchema(database.writable)
    val current = loadPolicy()
    if (current.learnedCondition.isBlank()) return current
    return current.copy(learnedCondition = "", revision = current.revision + 1).also { policy ->
      savePolicy(policy)
      dataChanges.notifyChanged()
    }
  }

  override fun loadAssessments(articleIds: Collection<String>): Map<String, RssRecommendationAssessment> {
    ensureRssRecommendationSchema(database.writable)
    if (articleIds.isEmpty()) return emptyMap()
    val result = mutableMapOf<String, RssRecommendationAssessment>()
    articleIds.distinct().chunked(SQLITE_BIND_CHUNK).forEach { chunk ->
      val placeholders = chunk.joinToString(",") { "?" }
      database.readable.rawQuery(
        """
          SELECT article_id, status, score, unscored_reason, revision, assessed_at
          FROM rss_recommendation_assessments
          WHERE article_id IN ($placeholders)
        """.trimIndent(),
        chunk.toTypedArray(),
      ).use { cursor ->
        while (cursor.moveToNext()) result[cursor.getString(0)] = cursor.assessment(offset = 1)
      }
    }
    return result
  }

  override fun saveAssessments(assessments: Map<String, RssRecommendationAssessment>) {
    ensureRssRecommendationSchema(database.writable)
    if (assessments.isEmpty()) return
    database.transaction {
      assessments.forEach { (articleId, assessment) ->
        val values = ContentValues().apply {
          put("article_id", articleId)
          when (assessment) {
            is RssRecommendationAssessment.Scored -> {
              put("status", STATUS_SCORED)
              put("score", assessment.score)
              putNull("unscored_reason")
            }
            is RssRecommendationAssessment.Unscored -> {
              put("status", STATUS_UNSCORED)
              putNull("score")
              put("unscored_reason", assessment.reason.name)
            }
          }
          put("revision", assessment.revision)
          put("assessed_at", assessment.assessedAt)
        }
        insertWithOnConflict("rss_recommendation_assessments", null, values, SQLiteDatabase.CONFLICT_REPLACE)
      }
    }
    dataChanges.notifyChanged()
  }

  override fun addFeedback(
    articleId: String,
    title: String,
    previousAssessment: RssRecommendationAssessment?,
  ): RssRecommendationFeedback {
    ensureRssRecommendationSchema(database.writable)
    val id = UUID.randomUUID().toString()
    val createdAt = System.currentTimeMillis()
    val values = ContentValues().apply {
      put("id", id)
      put("article_id", articleId)
      put("title", title)
      put("created_at", createdAt)
      when (previousAssessment) {
        is RssRecommendationAssessment.Scored -> {
          put("previous_status", STATUS_SCORED)
          put("previous_score", previousAssessment.score)
          putNull("previous_unscored_reason")
          put("previous_revision", previousAssessment.revision)
          put("previous_assessed_at", previousAssessment.assessedAt)
        }
        is RssRecommendationAssessment.Unscored -> {
          put("previous_status", STATUS_UNSCORED)
          putNull("previous_score")
          put("previous_unscored_reason", previousAssessment.reason.name)
          put("previous_revision", previousAssessment.revision)
          put("previous_assessed_at", previousAssessment.assessedAt)
        }
        null -> {
          putNull("previous_status")
          putNull("previous_score")
          putNull("previous_unscored_reason")
          putNull("previous_revision")
          putNull("previous_assessed_at")
        }
      }
    }
    database.write {
      insertWithOnConflict("rss_recommendation_feedback", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
    dataChanges.notifyChanged()
    return RssRecommendationFeedback(id, articleId, title, previousAssessment, createdAt)
  }

  override fun removeFeedback(feedbackId: String) {
    ensureRssRecommendationSchema(database.writable)
    database.write { delete("rss_recommendation_feedback", "id=?", arrayOf(feedbackId)) }
    dataChanges.notifyChanged()
  }

  override fun listPendingFeedback(): List<RssRecommendationFeedback> {
    ensureRssRecommendationSchema(database.writable)
    return database.readable.rawQuery(
      """
        SELECT id, article_id, title, previous_status, previous_score,
               previous_unscored_reason, previous_revision, previous_assessed_at, created_at
        FROM rss_recommendation_feedback
        ORDER BY created_at, id
      """.trimIndent(),
      emptyArray<String>(),
    ).use { cursor ->
      buildList { while (cursor.moveToNext()) add(cursor.feedback()) }
    }
  }

  override fun applyLearnedConditionAndConsumeFeedback(
    feedbackIds: Set<String>,
    learnedCondition: String,
  ): RssRecommendationPolicy {
    ensureRssRecommendationSchema(database.writable)
    val normalized = learnedCondition.trim()
    require(normalized.length <= MAX_RECOMMENDATION_CONDITION_LENGTH) { "学習条件が長すぎます" }
    val current = loadPolicy()
    val changed = current.learnedCondition != normalized
    val updated = if (changed) current.copy(learnedCondition = normalized, revision = current.revision + 1) else current
    database.transaction {
      if (changed) savePolicyInTransaction(this, updated)
      feedbackIds.chunked(SQLITE_BIND_CHUNK).forEach { chunk ->
        if (chunk.isNotEmpty()) {
          delete(
            "rss_recommendation_feedback",
            "id IN (${chunk.joinToString(",") { "?" }})",
            chunk.toTypedArray(),
          )
        }
      }
    }
    dataChanges.notifyChanged()
    return updated
  }

  override fun enqueueTasks(articles: List<Article>, revision: Long) {
    ensureRssRecommendationSchema(database.writable)
    val queuedAt = System.currentTimeMillis()
    database.localTransaction {
      delete("rss_recommendation_tasks", "revision<>?", arrayOf(revision.toString()))
      articles.distinctBy(Article::id).forEach { article ->
        insertWithOnConflict(
          "rss_recommendation_tasks",
          null,
          ContentValues().apply {
            put("article_id", article.id)
            put("title", article.title)
            put("revision", revision)
            put("state", TASK_QUEUED)
            put("queued_at", queuedAt)
            putNull("started_at")
          },
          SQLiteDatabase.CONFLICT_IGNORE,
        )
      }
    }
  }

  override fun listTasks(): List<RssRecommendationTask> {
    ensureRssRecommendationSchema(database.writable)
    return database.readable.rawQuery(
      """
        SELECT article_id, title, revision, state, queued_at, started_at
        FROM rss_recommendation_tasks
        ORDER BY queued_at, article_id
      """.trimIndent(),
      emptyArray<String>(),
    ).use { cursor ->
      buildList { while (cursor.moveToNext()) add(cursor.recommendationTask()) }
    }
  }

  override fun claimNextTask(): RssRecommendationTask? {
    ensureRssRecommendationSchema(database.writable)
    val startedAt = System.currentTimeMillis()
    return database.localTransaction {
      val queued = rawQuery(
        """
          SELECT article_id, title, revision, state, queued_at, started_at
          FROM rss_recommendation_tasks
          WHERE state=?
          ORDER BY queued_at, article_id
          LIMIT 1
        """.trimIndent(),
        arrayOf(TASK_QUEUED),
      ).use { cursor ->
        if (cursor.moveToFirst()) cursor.recommendationTask() else null
      } ?: return@transaction null
      val updated = update(
        "rss_recommendation_tasks",
        ContentValues().apply {
          put("state", TASK_RUNNING)
          put("started_at", startedAt)
        },
        "article_id=? AND revision=? AND state=?",
        arrayOf(queued.articleId, queued.revision.toString(), TASK_QUEUED),
      )
      if (updated == 1) {
        queued.copy(state = RssRecommendationTaskState.RUNNING, startedAt = startedAt)
      } else {
        null
      }
    }
  }

  override fun completeTask(articleId: String, revision: Long) {
    ensureRssRecommendationSchema(database.writable)
    database.localWrite {
      delete(
        "rss_recommendation_tasks",
      "article_id=? AND revision=?",
        arrayOf(articleId, revision.toString()),
      )
    }
  }

  override fun requeueTask(articleId: String, revision: Long) {
    ensureRssRecommendationSchema(database.writable)
    database.localWrite {
      update(
        "rss_recommendation_tasks",
      ContentValues().apply {
        put("state", TASK_QUEUED)
        putNull("started_at")
      },
      "article_id=? AND revision=?",
        arrayOf(articleId, revision.toString()),
      )
    }
  }

  override fun requeueInterruptedTasks() {
    ensureRssRecommendationSchema(database.writable)
    database.localWrite {
      update(
        "rss_recommendation_tasks",
      ContentValues().apply {
        put("state", TASK_QUEUED)
        putNull("started_at")
      },
      "state=?",
        arrayOf(TASK_RUNNING),
      )
    }
  }

  override fun clearTasks() {
    ensureRssRecommendationSchema(database.writable)
    database.localWrite { delete("rss_recommendation_tasks", null, null) }
  }

  private fun savePolicy(policy: RssRecommendationPolicy) {
    database.transaction { savePolicyInTransaction(this, policy) }
  }
}

internal fun ensureRssRecommendationSchema(db: SQLiteDatabase) {
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS rss_recommendation_policy(
        id INTEGER PRIMARY KEY NOT NULL CHECK(id=1),
        manual_condition TEXT NOT NULL DEFAULT '',
        learned_condition TEXT NOT NULL DEFAULT '',
        revision INTEGER NOT NULL DEFAULT 0
      )
    """.trimIndent(),
  )
  db.execSQL("INSERT OR IGNORE INTO rss_recommendation_policy(id,manual_condition,learned_condition,revision) VALUES(1,'','',0)")
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS rss_recommendation_assessments(
        article_id TEXT PRIMARY KEY NOT NULL,
        status TEXT NOT NULL,
        score INTEGER,
        unscored_reason TEXT,
        revision INTEGER NOT NULL,
        assessed_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS rss_recommendation_feedback(
        id TEXT PRIMARY KEY NOT NULL,
        article_id TEXT NOT NULL UNIQUE,
        title TEXT NOT NULL,
        previous_status TEXT,
        previous_score INTEGER,
        previous_unscored_reason TEXT,
        previous_revision INTEGER,
        previous_assessed_at INTEGER,
        created_at INTEGER NOT NULL
      )
    """.trimIndent(),
  )
  db.execSQL("CREATE INDEX IF NOT EXISTS rss_recommendation_feedback_created_at ON rss_recommendation_feedback(created_at,id)")
  db.execSQL(
    """
      CREATE TABLE IF NOT EXISTS rss_recommendation_tasks(
        article_id TEXT PRIMARY KEY NOT NULL,
        title TEXT NOT NULL,
        revision INTEGER NOT NULL,
        state TEXT NOT NULL,
        queued_at INTEGER NOT NULL,
        started_at INTEGER
      )
    """.trimIndent(),
  )
  db.execSQL("CREATE INDEX IF NOT EXISTS rss_recommendation_tasks_state ON rss_recommendation_tasks(state,queued_at)")
}

private fun savePolicyInTransaction(db: SQLiteDatabase, policy: RssRecommendationPolicy) {
  db.update(
    "rss_recommendation_policy",
    ContentValues().apply {
      put("manual_condition", policy.manualCondition)
      put("learned_condition", policy.learnedCondition)
      put("revision", policy.revision)
    },
    "id=1",
    null,
  )
}

private fun Cursor.assessment(offset: Int): RssRecommendationAssessment {
  val status = getString(offset)
  val revision = getLong(offset + 3)
  val assessedAt = getLong(offset + 4)
  return when (status) {
    STATUS_SCORED -> RssRecommendationAssessment.Scored(getInt(offset + 1), revision, assessedAt)
    STATUS_UNSCORED -> RssRecommendationAssessment.Unscored(
      RssRecommendationUnscoredReason.valueOf(getString(offset + 2)),
      revision,
      assessedAt,
    )
    else -> error("Unknown RSS recommendation status: $status")
  }
}

private fun Cursor.recommendationTask(): RssRecommendationTask = RssRecommendationTask(
  articleId = getString(0),
  title = getString(1),
  revision = getLong(2),
  state = when (getString(3)) {
    TASK_QUEUED -> RssRecommendationTaskState.QUEUED
    TASK_RUNNING -> RssRecommendationTaskState.RUNNING
    else -> error("Unknown RSS recommendation task state: ${getString(3)}")
  },
  queuedAt = getLong(4),
  startedAt = if (isNull(5)) null else getLong(5),
)

private fun Cursor.feedback(): RssRecommendationFeedback {
  val previous = if (isNull(3)) {
    null
  } else {
    val revision = getLong(6)
    val assessedAt = getLong(7)
    when (getString(3)) {
      STATUS_SCORED -> RssRecommendationAssessment.Scored(getInt(4), revision, assessedAt)
      STATUS_UNSCORED -> RssRecommendationAssessment.Unscored(
        RssRecommendationUnscoredReason.valueOf(getString(5)),
        revision,
        assessedAt,
      )
      else -> null
    }
  }
  return RssRecommendationFeedback(
    id = getString(0),
    articleId = getString(1),
    title = getString(2),
    previousAssessment = previous,
    createdAt = getLong(8),
  )
}

private const val STATUS_SCORED = "SCORED"
private const val STATUS_UNSCORED = "UNSCORED"
private const val TASK_QUEUED = "QUEUED"
private const val TASK_RUNNING = "RUNNING"
private const val SQLITE_BIND_CHUNK = 500
private const val MAX_RECOMMENDATION_CONDITION_LENGTH = 12_000
