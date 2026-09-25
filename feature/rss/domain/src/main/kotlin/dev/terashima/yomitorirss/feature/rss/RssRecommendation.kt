package dev.terashima.yomitorirss.feature.rss

import dev.terashima.yomitorirss.feature.article.Article
import java.util.concurrent.CancellationException

data class RssRecommendationPolicy(
  val manualCondition: String = "",
  val learnedCondition: String = "",
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

interface RssRecommendationRepository {
  fun loadPolicy(): RssRecommendationPolicy
  fun saveManualCondition(condition: String): RssRecommendationPolicy
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
}

interface RssRecommendationEngine {
  suspend fun score(
    condition: String,
    titles: List<String>,
  ): List<RssRecommendationDecision>

  suspend fun improveLearnedCondition(
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

  suspend fun refresh(articles: List<Article>): RssRecommendationSnapshot {
    val policy = repository.loadPolicy()
    if (!policy.enabled || articles.isEmpty()) return snapshot(articles.map(Article::id))

    val ids = articles.map(Article::id)
    val existing = repository.loadAssessments(ids)
    val candidates = articles.filter { existing[it.id]?.revision != policy.revision }
    if (candidates.isNotEmpty()) {
      val decisions = try {
        engine.score(
          condition = policy.effectiveCondition(),
          titles = candidates.map(Article::title),
        ).also {
          require(it.size == candidates.size) { "recommendation decisions must match candidate count" }
        }
      } catch (error: CancellationException) {
        throw error
      } catch (_: Throwable) {
        val failedAt = nowMillis()
        repository.saveAssessments(
          candidates.associate { article ->
            article.id to RssRecommendationAssessment.Unscored(
              reason = RssRecommendationUnscoredReason.INFERENCE_FAILED,
              revision = policy.revision,
              assessedAt = failedAt,
            )
          },
        )
        return snapshot(ids)
      }

      val assessedAt = nowMillis()
      repository.saveAssessments(
        candidates.mapIndexed { index, article ->
          val assessment = when (val decision = decisions[index]) {
            is RssRecommendationDecision.Scored -> RssRecommendationAssessment.Scored(
              score = decision.score,
              revision = policy.revision,
              assessedAt = assessedAt,
            )
            RssRecommendationDecision.InsufficientInformation -> RssRecommendationAssessment.Unscored(
              reason = RssRecommendationUnscoredReason.INSUFFICIENT_INFORMATION,
              revision = policy.revision,
              assessedAt = assessedAt,
            )
          }
          article.id to assessment
        }.toMap(),
      )
    }
    return snapshot(ids)
  }

  fun saveManualCondition(condition: String): RssRecommendationPolicy =
    repository.saveManualCondition(condition.trim())

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

  suspend fun improvePendingFeedback(): RssRecommendationPolicy? {
    val pending = repository.listPendingFeedback().take(MAX_RECOMMENDATION_LEARNING_FEEDBACK)
    if (pending.isEmpty()) return null
    val policy = repository.loadPolicy()
    val learned = try {
      engine.improveLearnedCondition(
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
