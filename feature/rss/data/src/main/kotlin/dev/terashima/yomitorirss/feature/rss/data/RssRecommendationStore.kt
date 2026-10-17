package dev.terashima.yomitorirss.feature.rss.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.rss.RssRecommendationAssessment
import dev.terashima.yomitorirss.feature.rss.RssRecommendationExecutionProvider
import dev.terashima.yomitorirss.feature.rss.RssRecommendationFeedback
import dev.terashima.yomitorirss.feature.rss.RssRecommendationFeedbackKind
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
    return database.readable.rawQuery(
      "SELECT manual_condition, learned_condition, execution_provider, revision, learned_priority_condition FROM rss_recommendation_policy WHERE id=1",
      emptyArray<String>(),
    ).use { cursor ->
      if (!cursor.moveToFirst()) {
        RssRecommendationPolicy()
      } else {
        RssRecommendationPolicy(
          manualCondition = cursor.getString(0),
          learnedCondition = cursor.getString(1),
          executionProvider = runCatching {
            RssRecommendationExecutionProvider.valueOf(cursor.getString(2))
          }.getOrDefault(RssRecommendationExecutionProvider.LOCAL),
          revision = cursor.getLong(3),
          learnedPriorityCondition = cursor.getString(4),
        )
      }
    }
  }

  override fun saveManualCondition(condition: String): RssRecommendationPolicy {
    val normalized = condition.trim()
    require(normalized.length <= MAX_RECOMMENDATION_CONDITION_LENGTH) { "除外条件が長すぎます" }
    val current = loadPolicy()
    if (current.manualCondition == normalized) return current
    return current.copy(manualCondition = normalized, revision = current.revision + 1).also { policy ->
      savePolicy(policy)
      dataChanges.notifyChanged()
    }
  }

  override fun setExecutionProvider(
    provider: RssRecommendationExecutionProvider,
  ): RssRecommendationPolicy {
    val current = loadPolicy()
    if (current.executionProvider == provider) return current
    return current.copy(executionProvider = provider, revision = current.revision + 1).also { policy ->
      savePolicy(policy)
      dataChanges.notifyChanged()
    }
  }

  override fun resetLearnedPriorityCondition(): RssRecommendationPolicy {
    val current = loadPolicy()
    val hasProcessedFeedback = database.readable.rawQuery(
      "SELECT 1 FROM rss_recommendation_feedback WHERE kind=? AND processed_at IS NOT NULL LIMIT 1",
      arrayOf(RssRecommendationFeedbackKind.PRIORITY.name),
    ).use { it.moveToFirst() }
    if (current.learnedPriorityCondition.isBlank() && !hasProcessedFeedback) return current
    return current.copy(learnedPriorityCondition = "", revision = current.revision + 1).also { policy ->
      database.transaction {
        savePolicyInTransaction(this, policy)
        delete(
          "rss_recommendation_feedback",
          "kind=? AND processed_at IS NOT NULL",
          arrayOf(RssRecommendationFeedbackKind.PRIORITY.name),
        )
      }
      dataChanges.notifyChanged()
    }
  }

  override fun resetLearnedCondition(): RssRecommendationPolicy {
    val current = loadPolicy()
    val hasProcessedFeedback = database.readable.rawQuery(
      "SELECT 1 FROM rss_recommendation_feedback WHERE kind=? AND processed_at IS NOT NULL LIMIT 1",
      arrayOf(RssRecommendationFeedbackKind.EXCLUSION.name),
    ).use { it.moveToFirst() }
    if (current.learnedCondition.isBlank() && !hasProcessedFeedback) return current
    return current.copy(learnedCondition = "", revision = current.revision + 1).also { policy ->
      database.transaction {
        savePolicyInTransaction(this, policy)
        delete(
          "rss_recommendation_feedback",
          "kind=? AND processed_at IS NOT NULL",
          arrayOf(RssRecommendationFeedbackKind.EXCLUSION.name),
        )
      }
      dataChanges.notifyChanged()
    }
  }

  override fun loadAssessments(articleIds: Collection<String>): Map<String, RssRecommendationAssessment> {
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
    kind: RssRecommendationFeedbackKind,
  ): RssRecommendationFeedback {
    val id = UUID.randomUUID().toString()
    val createdAt = System.currentTimeMillis()
    val values = ContentValues().apply {
      put("id", id)
      put("article_id", articleId)
      put("title", title)
      put("created_at", createdAt)
      put("kind", kind.name)
      putNull("processed_at")
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
    return RssRecommendationFeedback(id, articleId, title, previousAssessment, createdAt, kind)
  }

  override fun removeFeedback(feedbackId: String) {
    database.write { delete("rss_recommendation_feedback", "id=?", arrayOf(feedbackId)) }
    dataChanges.notifyChanged()
  }

  override fun listLearningExamples(): List<RssRecommendationFeedback> {
    return RssRecommendationFeedbackKind.entries.flatMap { kind ->
      database.readable.rawQuery(
        """
          SELECT id, article_id, title, previous_status, previous_score,
                 previous_unscored_reason, previous_revision, previous_assessed_at, created_at, kind
          FROM rss_recommendation_feedback
          WHERE processed_at IS NOT NULL AND kind=?
          ORDER BY created_at DESC, id DESC
          LIMIT 25
        """.trimIndent(),
        arrayOf(kind.name),
      ).use { cursor ->
        buildList { while (cursor.moveToNext()) add(cursor.feedback()) }
      }
    }.sortedWith(compareBy(RssRecommendationFeedback::createdAt, RssRecommendationFeedback::id))
  }

  override fun listPendingFeedback(): List<RssRecommendationFeedback> {
    return database.readable.rawQuery(
      """
        SELECT id, article_id, title, previous_status, previous_score,
               previous_unscored_reason, previous_revision, previous_assessed_at, created_at, kind
        FROM rss_recommendation_feedback
        WHERE processed_at IS NULL
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
    learnedPriorityCondition: String,
    expectedRevision: Long,
  ): RssRecommendationPolicy? {
    val normalized = learnedCondition.trim()
    require(normalized.length <= MAX_RECOMMENDATION_CONDITION_LENGTH) { "学習条件が長すぎます" }
    val normalizedPriority = learnedPriorityCondition.trim()
    require(normalizedPriority.length <= MAX_RECOMMENDATION_CONDITION_LENGTH) { "優先条件が長すぎます" }
    if (feedbackIds.isEmpty()) return null

    // Recheck the policy and feedback under the same SQLite write transaction. Inference may
    // finish after a manual edit, reset, provider switch, or cancellation of a reference.
    val updated = database.transaction {
      val current = loadPolicy()
      if (current.revision != expectedRevision) return@transaction null
      val pendingCount = feedbackIds.chunked(SQLITE_BIND_CHUNK).sumOf { chunk ->
        rawQuery(
          "SELECT COUNT(*) FROM rss_recommendation_feedback WHERE processed_at IS NULL AND id IN (" +
            chunk.joinToString(",") { "?" } + ")",
          chunk.toTypedArray(),
        ).use { cursor ->
          check(cursor.moveToFirst())
          cursor.getInt(0)
        }
      }
      if (pendingCount != feedbackIds.size) return@transaction null

      val changed = current.learnedCondition != normalized ||
        current.learnedPriorityCondition != normalizedPriority
      val next = if (changed) current.copy(
        learnedCondition = normalized,
        learnedPriorityCondition = normalizedPriority,
        revision = current.revision + 1,
      ) else current
      if (changed) savePolicyInTransaction(this, next)
      feedbackIds.chunked(SQLITE_BIND_CHUNK).forEach { chunk ->
        update(
          "rss_recommendation_feedback",
          ContentValues().apply { put("processed_at", System.currentTimeMillis()) },
          "id IN (" + chunk.joinToString(",") { "?" } + ") AND processed_at IS NULL",
          chunk.toTypedArray(),
        )
      }
      RssRecommendationFeedbackKind.entries.forEach { kind ->
        execSQL(
          """
            DELETE FROM rss_recommendation_feedback
            WHERE processed_at IS NOT NULL AND kind=?
              AND id NOT IN (
                SELECT id FROM rss_recommendation_feedback
                WHERE processed_at IS NOT NULL AND kind=?
                ORDER BY created_at DESC, id DESC
                LIMIT 250
              )
          """.trimIndent(),
          arrayOf(kind.name, kind.name),
        )
      }
      next
    }
    if (updated != null) dataChanges.notifyChanged()
    return updated
  }

  override fun enqueueTasks(articles: List<Article>, revision: Long) {
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

  override fun executionProvider(): RssRecommendationExecutionProvider =
    loadPolicy().executionProvider

  override fun listTasks(): List<RssRecommendationTask> {
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
      } ?: return@localTransaction null
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
    database.localWrite {
      delete(
        "rss_recommendation_tasks",
      "article_id=? AND revision=?",
        arrayOf(articleId, revision.toString()),
      )
    }
  }

  override fun requeueTask(articleId: String, revision: Long) {
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
        learned_priority_condition TEXT NOT NULL DEFAULT '',
        execution_provider TEXT NOT NULL DEFAULT 'LOCAL',
        revision INTEGER NOT NULL DEFAULT 0
      )
    """.trimIndent(),
  )
  if (!db.hasColumn("rss_recommendation_policy", "learned_priority_condition")) {
    db.execSQL("ALTER TABLE rss_recommendation_policy ADD COLUMN learned_priority_condition TEXT NOT NULL DEFAULT ''")
    // The 1..10 score meaning now includes explicit priority preference; invalidate old assessments.
    db.execSQL("UPDATE rss_recommendation_policy SET revision=revision+1")
  }
  db.execSQL(
    "INSERT OR IGNORE INTO rss_recommendation_policy(" +
      "id,manual_condition,learned_condition,execution_provider,revision" +
      ") VALUES(1,'','','LOCAL',0)",
  )
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
        created_at INTEGER NOT NULL,
        kind TEXT NOT NULL DEFAULT 'EXCLUSION',
        processed_at INTEGER
      )
    """.trimIndent(),
  )
  if (!db.hasColumn("rss_recommendation_feedback", "kind")) {
    db.execSQL("ALTER TABLE rss_recommendation_feedback ADD COLUMN kind TEXT NOT NULL DEFAULT 'EXCLUSION'")
  }
  if (!db.hasColumn("rss_recommendation_feedback", "processed_at")) {
    db.execSQL("ALTER TABLE rss_recommendation_feedback ADD COLUMN processed_at INTEGER")
  }
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
      put("learned_priority_condition", policy.learnedPriorityCondition)
      put("execution_provider", policy.executionProvider.name)
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
    kind = RssRecommendationFeedbackKind.valueOf(getString(9)),
  )
}

private const val STATUS_SCORED = "SCORED"
private const val STATUS_UNSCORED = "UNSCORED"
private const val TASK_QUEUED = "QUEUED"
private const val TASK_RUNNING = "RUNNING"
private const val SQLITE_BIND_CHUNK = 500
private const val MAX_RECOMMENDATION_CONDITION_LENGTH = 12_000


private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean =
  rawQuery("PRAGMA table_info($table)", null).use { cursor ->
    val nameIndex = cursor.getColumnIndexOrThrow("name")
    generateSequence { if (cursor.moveToNext()) cursor.getString(nameIndex) else null }
      .any { it == column }
  }
