package dev.terashima.yomitorirss.feature.rss.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.feature.rss.RssRecommendationAssessment
import dev.terashima.yomitorirss.feature.rss.RssRecommendationFeedback
import dev.terashima.yomitorirss.feature.rss.RssRecommendationPolicy
import dev.terashima.yomitorirss.feature.rss.RssRecommendationRepository
import dev.terashima.yomitorirss.feature.rss.RssRecommendationUnscoredReason
import java.util.UUID

class DefaultRssRecommendationRepository(
  private val database: DatabaseConnection,
) : RssRecommendationRepository {
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
    return current.copy(manualCondition = normalized, revision = current.revision + 1).also(::savePolicy)
  }

  override fun resetLearnedCondition(): RssRecommendationPolicy {
    ensureRssRecommendationSchema(database.writable)
    val current = loadPolicy()
    if (current.learnedCondition.isBlank()) return current
    return current.copy(learnedCondition = "", revision = current.revision + 1).also(::savePolicy)
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
    return RssRecommendationFeedback(id, articleId, title, previousAssessment, createdAt)
  }

  override fun removeFeedback(feedbackId: String) {
    ensureRssRecommendationSchema(database.writable)
    database.write { delete("rss_recommendation_feedback", "id=?", arrayOf(feedbackId)) }
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
    return updated
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
private const val SQLITE_BIND_CHUNK = 500
private const val MAX_RECOMMENDATION_CONDITION_LENGTH = 12_000
