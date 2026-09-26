package dev.terashima.yomitorirss.feature.rss

import dev.terashima.yomitorirss.feature.article.Article
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RssRecommendationExecutionProvider {
  LOCAL,
  CLOUD,
}

data class RssRecommendationPolicy(
  val manualCondition: String = "",
  val learnedCondition: String = "",
  val executionProvider: RssRecommendationExecutionProvider = RssRecommendationExecutionProvider.LOCAL,
  val revision: Long = 0L,
) {
  val enabled: Boolean
    get() = manualCondition.isNotBlank() || learnedCondition.isNotBlank()

  fun effectiveCondition(): String = buildList {
    manualCondition.trim().takeIf(String::isNotBlank)?.let { add("手動条件:\n$it") }
    learnedCondition.trim().takeIf(String::isNotBlank)?.let { add("学習条件:\n$it") }
  }.joinToString("\n\n")
}

enum class RssRecommendationUnscoredReason {
  INSUFFICIENT_INFORMATION,
  INFERENCE_FAILED,
}

sealed interface RssRecommendationAssessment {
  val revision: Long
  val assessedAt: Long

  data class Scored(
    val score: Int,
    override val revision: Long,
    override val assessedAt: Long,
  ) : RssRecommendationAssessment {
    init {
      require(score in 1..10) { "score must be between 1 and 10" }
    }
  }

  data class Unscored(
    val reason: RssRecommendationUnscoredReason,
    override val revision: Long,
    override val assessedAt: Long,
  ) : RssRecommendationAssessment
}

sealed interface RssRecommendationDecision {
  data class Scored(val score: Int) : RssRecommendationDecision {
    init {
      require(score in 1..10) { "score must be between 1 and 10" }
    }
  }

  data object InsufficientInformation : RssRecommendationDecision
}

data class RssRecommendationFeedback(
  val id: String,
  val articleId: String,
  val title: String,
  val previousAssessment: RssRecommendationAssessment?,
  val createdAt: Long,
)

data class RssRecommendationSnapshot(
  val policy: RssRecommendationPolicy,
  val assessments: Map<String, RssRecommendationAssessment>,
  val pendingFeedbackCount: Int,
  val latestPendingFeedbackAt: Long?,
)

enum class RssRecommendationTaskState {
  QUEUED,
  RUNNING,
}

data class RssRecommendationTask(
  val articleId: String,
  val title: String,
  val revision: Long,
  val state: RssRecommendationTaskState,
  val queuedAt: Long,
  val startedAt: Long?,
)

interface RssRecommendationTaskReader {
  fun listTasks(): List<RssRecommendationTask>
}

interface RssRecommendationTaskScheduler {
  suspend fun enqueueForFeed(feedId: String)
  suspend fun enqueueUnread()
  fun kick()
  suspend fun pauseForGlobalGate()
  fun setResumeOnChargingScheduled(enabled: Boolean)
}

interface RssRecommendationRepository : RssRecommendationTaskReader {
  val changes: StateFlow<Long>
  fun loadPolicy(): RssRecommendationPolicy
  fun saveManualCondition(condition: String): RssRecommendationPolicy
  fun setExecutionProvider(provider: RssRecommendationExecutionProvider): RssRecommendationPolicy
  fun resetLearnedCondition(): RssRecommendationPolicy
  fun loadAssessments(articleIds: Collection<String>): Map<String, RssRecommendationAssessment>
  fun saveAssessments(assessments: Map<String, RssRecommendationAssessment>)
  fun addFeedback(
    articleId: String,
    title: String,
    previousAssessment: RssRecommendationAssessment?,
  ): RssRecommendationFeedback
  fun listPendingFeedback(): List<RssRecommendationFeedback>
  fun removeFeedback(feedbackId: String)
  fun applyLearnedConditionAndConsumeFeedback(
    feedbackIds: Set<String>,
    learnedCondition: String,
  ): RssRecommendationPolicy

  fun enqueueTasks(articles: List<Article>, revision: Long)
  fun claimNextTask(): RssRecommendationTask?
  fun completeTask(articleId: String, revision: Long)
  fun requeueTask(articleId: String, revision: Long)
  fun requeueInterruptedTasks()
  fun clearTasks()
}

interface RssRecommendationEngine {
  suspend fun score(
    provider: RssRecommendationExecutionProvider,
    condition: String,
    titles: List<String>,
  ): List<RssRecommendationDecision>

  suspend fun improveLearnedCondition(
    provider: RssRecommendationExecutionProvider,
    manualCondition: String,
    learnedCondition: String,
    feedback: List<RssRecommendationFeedback>,
  ): String
}

class RssRecommendationService(
  private val repository: RssRecommendationRepository,
  private val engine: RssRecommendationEngine,
  private val nowMillis: () -> Long = System::currentTimeMillis,
) {
  private val inferenceMutex = Mutex()

  val changes: StateFlow<Long>
    get() = repository.changes

  fun snapshot(articleIds: Collection<String>): RssRecommendationSnapshot {
    val policy = repository.loadPolicy()
    val currentAssessments = if (policy.enabled) {
      repository.loadAssessments(articleIds)
        .filterValues { it.revision == policy.revision }
    } else {
      emptyMap()
    }
    val pending = repository.listPendingFeedback()
    return RssRecommendationSnapshot(
      policy = policy,
      assessments = currentAssessments,
      pendingFeedbackCount = pending.size,
      latestPendingFeedbackAt = pending.maxOfOrNull(RssRecommendationFeedback::createdAt),
    )
  }

  suspend fun scoreArticle(
    article: Article,
    revision: Long,
  ): RssRecommendationAssessment? = inferenceMutex.withLock {
    val policy = repository.loadPolicy()
    if (!policy.enabled || policy.revision != revision) return@withLock null
    val existing = repository.loadAssessments(listOf(article.id))[article.id]
    if (
      existing?.revision == revision &&
      !(existing is RssRecommendationAssessment.Unscored &&
        existing.reason == RssRecommendationUnscoredReason.INFERENCE_FAILED)
    ) {
      return@withLock existing
    }

    val assessedAt = nowMillis()
    val assessment = try {
      when (val decision = engine.score(
        provider = policy.executionProvider,
        condition = policy.effectiveCondition(),
        titles = listOf(article.title),
      ).single()) {
        is RssRecommendationDecision.Scored -> RssRecommendationAssessment.Scored(
          score = decision.score,
          revision = revision,
          assessedAt = assessedAt,
        )
        RssRecommendationDecision.InsufficientInformation -> RssRecommendationAssessment.Unscored(
          reason = RssRecommendationUnscoredReason.INSUFFICIENT_INFORMATION,
          revision = revision,
          assessedAt = assessedAt,
        )
      }
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      RssRecommendationAssessment.Unscored(
        reason = RssRecommendationUnscoredReason.INFERENCE_FAILED,
        revision = revision,
        assessedAt = assessedAt,
      )
    }
    repository.saveAssessments(mapOf(article.id to assessment))
    assessment
  }

  fun saveManualCondition(condition: String): RssRecommendationPolicy =
    repository.saveManualCondition(condition.trim())

  fun setExecutionProvider(provider: RssRecommendationExecutionProvider): RssRecommendationPolicy =
    repository.setExecutionProvider(provider)

  fun resetLearnedCondition(): RssRecommendationPolicy =
    repository.resetLearnedCondition()

  fun recordExclusionFeedback(article: Article): RssRecommendationFeedback {
    val policy = repository.loadPolicy()
    val assessment = repository.loadAssessments(listOf(article.id))[article.id]
      ?.takeIf { it.revision == policy.revision }
    return repository.addFeedback(article.id, article.title, assessment)
  }

  fun cancelExclusionFeedback(feedbackId: String) {
    repository.removeFeedback(feedbackId)
  }

  suspend fun improvePendingFeedback(): RssRecommendationPolicy? = inferenceMutex.withLock {
    val pending = repository.listPendingFeedback().take(MAX_RECOMMENDATION_LEARNING_FEEDBACK)
    if (pending.isEmpty()) return null
    val policy = repository.loadPolicy()
    val learned = try {
      engine.improveLearnedCondition(
        provider = policy.executionProvider,
        manualCondition = policy.manualCondition,
        learnedCondition = policy.learnedCondition,
        feedback = pending,
      ).trim()
    } catch (error: CancellationException) {
      throw error
    } catch (_: Throwable) {
      return null
    }
    return repository.applyLearnedConditionAndConsumeFeedback(
      feedbackIds = pending.mapTo(mutableSetOf(), RssRecommendationFeedback::id),
      learnedCondition = learned,
    )
  }
}

private const val MAX_RECOMMENDATION_LEARNING_FEEDBACK = 50
