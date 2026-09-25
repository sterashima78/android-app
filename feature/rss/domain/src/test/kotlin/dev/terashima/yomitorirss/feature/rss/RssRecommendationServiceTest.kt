package dev.terashima.yomitorirss.feature.rss

import dev.terashima.yomitorirss.feature.article.Article
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssRecommendationServiceTest {
  @Test
  fun `除外条件がなければスコアリングを実行しない`() = runBlocking {
    val repository = FakeRecommendationRepository()
    val engine = FakeRecommendationEngine()
    val service = RssRecommendationService(repository, engine, nowMillis = { 100L })

    val result = service.refresh(listOf(article("a1", "記事1")))

    assertEquals(0, engine.scoreCalls)
    assertTrue(result.assessments.isEmpty())
  }

  @Test
  fun `評価可能な記事だけ数値スコアとして保存する`() = runBlocking {
    val repository = FakeRecommendationRepository(
      policy = RssRecommendationPolicy(manualCondition = "広告は低くする", revision = 3L),
    )
    val engine = FakeRecommendationEngine(
      decisions = listOf(
        RssRecommendationDecision.Scored(10),
        RssRecommendationDecision.InsufficientInformation,
      ),
    )
    val service = RssRecommendationService(repository, engine, nowMillis = { 200L })

    val result = service.refresh(
      listOf(article("a1", "通常記事"), article("a2", "判断材料の少ない記事")),
    )

    assertEquals(
      RssRecommendationAssessment.Scored(10, revision = 3L, assessedAt = 200L),
      result.assessments["a1"],
    )
    assertEquals(
      RssRecommendationAssessment.Unscored(
        RssRecommendationUnscoredReason.INSUFFICIENT_INFORMATION,
        revision = 3L,
        assessedAt = 200L,
      ),
      result.assessments["a2"],
    )
  }

  @Test
  fun `推論失敗は10にせず未評価理由として保存する`() = runBlocking {
    val repository = FakeRecommendationRepository(
      policy = RssRecommendationPolicy(manualCondition = "広告は低くする", revision = 1L),
    )
    val engine = FakeRecommendationEngine(failScoring = true)
    val service = RssRecommendationService(repository, engine, nowMillis = { 300L })

    val result = service.refresh(listOf(article("a1", "記事")))

    assertEquals(
      RssRecommendationAssessment.Unscored(
        RssRecommendationUnscoredReason.INFERENCE_FAILED,
        revision = 1L,
        assessedAt = 300L,
      ),
      result.assessments["a1"],
    )
  }

  @Test
  fun `記事単位評価は現在revisionの確定済み評価を再推論しない`() = runBlocking {
    val existing = RssRecommendationAssessment.Scored(
      score = 7,
      revision = 2L,
      assessedAt = 100L,
    )
    val repository = FakeRecommendationRepository(
      policy = RssRecommendationPolicy(manualCondition = "条件", revision = 2L),
      assessments = mutableMapOf("a1" to existing),
    )
    val engine = FakeRecommendationEngine(decisions = listOf(RssRecommendationDecision.Scored(9)))
    val service = RssRecommendationService(repository, engine, nowMillis = { 400L })

    val result = service.scoreArticle(article("a1", "記事"), revision = 2L)

    assertEquals(existing, result)
    assertEquals(0, engine.scoreCalls)
  }

  @Test
  fun `記事単位評価は推論失敗だけを同じrevisionでも再評価する`() = runBlocking {
    val repository = FakeRecommendationRepository(
      policy = RssRecommendationPolicy(manualCondition = "条件", revision = 2L),
      assessments = mutableMapOf(
        "a1" to RssRecommendationAssessment.Unscored(
          reason = RssRecommendationUnscoredReason.INFERENCE_FAILED,
          revision = 2L,
          assessedAt = 100L,
        ),
      ),
    )
    val engine = FakeRecommendationEngine(decisions = listOf(RssRecommendationDecision.Scored(9)))
    val service = RssRecommendationService(repository, engine, nowMillis = { 400L })

    val result = service.scoreArticle(article("a1", "記事"), revision = 2L)

    assertEquals(
      RssRecommendationAssessment.Scored(9, revision = 2L, assessedAt = 400L),
      result,
    )
    assertEquals(1, engine.scoreCalls)
  }

  @Test
  fun `同じrevisionの推論失敗は次回refreshで再評価する`() = runBlocking {
    val repository = FakeRecommendationRepository(
      policy = RssRecommendationPolicy(manualCondition = "条件", revision = 2L),
      assessments = mutableMapOf(
        "a1" to RssRecommendationAssessment.Unscored(
          reason = RssRecommendationUnscoredReason.INFERENCE_FAILED,
          revision = 2L,
          assessedAt = 50L,
        ),
      ),
    )
    val engine = FakeRecommendationEngine(decisions = listOf(RssRecommendationDecision.Scored(9)))
    val service = RssRecommendationService(repository, engine, nowMillis = { 400L })

    val result = service.refresh(listOf(article("a1", "記事")))

    assertEquals(1, engine.scoreCalls)
    assertEquals(
      RssRecommendationAssessment.Scored(9, revision = 2L, assessedAt = 400L),
      result.assessments["a1"],
    )
  }

  @Test
  fun `条件revisionが変わると古い評価を再利用しない`() = runBlocking {
    val repository = FakeRecommendationRepository(
      policy = RssRecommendationPolicy(manualCondition = "条件", revision = 2L),
      assessments = mutableMapOf(
        "a1" to RssRecommendationAssessment.Scored(2, revision = 1L, assessedAt = 50L),
      ),
    )
    val engine = FakeRecommendationEngine(decisions = listOf(RssRecommendationDecision.Scored(8)))
    val service = RssRecommendationService(repository, engine, nowMillis = { 400L })

    val result = service.refresh(listOf(article("a1", "記事")))

    assertEquals(1, engine.scoreCalls)
    assertEquals(
      RssRecommendationAssessment.Scored(8, revision = 2L, assessedAt = 400L),
      result.assessments["a1"],
    )
  }

  private fun article(id: String, title: String) = Article(
    id = id,
    feedId = "feed",
    externalId = id,
    identityKey = id,
    url = "https://example.com/$id",
    title = title,
    publishedAt = "2026-09-25T00:00:00Z",
    fetchedAt = "2026-09-25T00:00:00Z",
    readAt = null,
    sourceTitle = "Example",
    sourceFeedUrl = "https://example.com/feed",
  )
}

private class FakeRecommendationEngine(
  private val decisions: List<RssRecommendationDecision> = emptyList(),
  private val failScoring: Boolean = false,
) : RssRecommendationEngine {
  var scoreCalls = 0

  override suspend fun score(
    condition: String,
    titles: List<String>,
  ): List<RssRecommendationDecision> {
    scoreCalls += 1
    if (failScoring) error("inference failed")
    return decisions
  }

  override suspend fun improveLearnedCondition(
    manualCondition: String,
    learnedCondition: String,
    feedback: List<RssRecommendationFeedback>,
  ): String = learnedCondition
}

private class FakeRecommendationRepository(
  var policy: RssRecommendationPolicy = RssRecommendationPolicy(),
  val assessments: MutableMap<String, RssRecommendationAssessment> = mutableMapOf(),
) : RssRecommendationRepository {
  private val feedback = mutableListOf<RssRecommendationFeedback>()
  private val tasks = mutableListOf<RssRecommendationTask>()
  private val changeFlow = MutableStateFlow(0L)
  override val changes: StateFlow<Long> = changeFlow

  override fun loadPolicy(): RssRecommendationPolicy = policy

  override fun saveManualCondition(condition: String): RssRecommendationPolicy {
    if (policy.manualCondition != condition) {
      policy = policy.copy(manualCondition = condition, revision = policy.revision + 1)
    }
    return policy
  }

  override fun resetLearnedCondition(): RssRecommendationPolicy {
    if (policy.learnedCondition.isNotBlank()) {
      policy = policy.copy(learnedCondition = "", revision = policy.revision + 1)
    }
    return policy
  }

  override fun loadAssessments(
    articleIds: Collection<String>,
  ): Map<String, RssRecommendationAssessment> =
    assessments.filterKeys { it in articleIds }

  override fun saveAssessments(assessments: Map<String, RssRecommendationAssessment>) {
    this.assessments.putAll(assessments)
  }

  override fun addFeedback(
    articleId: String,
    title: String,
    previousAssessment: RssRecommendationAssessment?,
  ): RssRecommendationFeedback {
    val item = RssRecommendationFeedback(
      id = "f${feedback.size}",
      articleId = articleId,
      title = title,
      previousAssessment = previousAssessment,
      createdAt = 500L,
    )
    feedback.removeAll { it.articleId == articleId }
    feedback += item
    return item
  }

  override fun listPendingFeedback(): List<RssRecommendationFeedback> = feedback.toList()

  override fun removeFeedback(feedbackId: String) {
    feedback.removeAll { it.id == feedbackId }
  }

  override fun applyLearnedConditionAndConsumeFeedback(
    feedbackIds: Set<String>,
    learnedCondition: String,
  ): RssRecommendationPolicy {
    if (policy.learnedCondition != learnedCondition) {
      policy = policy.copy(learnedCondition = learnedCondition, revision = policy.revision + 1)
    }
    feedback.removeAll { it.id in feedbackIds }
    return policy
  }

  override fun enqueueTasks(articles: List<Article>, revision: Long) {
    articles.forEach { article ->
      if (tasks.none { it.articleId == article.id }) {
        tasks += RssRecommendationTask(
          articleId = article.id,
          title = article.title,
          revision = revision,
          state = RssRecommendationTaskState.QUEUED,
          queuedAt = 0L,
          startedAt = null,
        )
      }
    }
  }

  override fun listTasks(): List<RssRecommendationTask> = tasks.toList()

  override fun claimNextTask(): RssRecommendationTask? {
    val index = tasks.indexOfFirst { it.state == RssRecommendationTaskState.QUEUED }
    if (index < 0) return null
    return tasks[index].copy(
      state = RssRecommendationTaskState.RUNNING,
      startedAt = 1L,
    ).also { tasks[index] = it }
  }

  override fun completeTask(articleId: String, revision: Long) {
    tasks.removeAll { it.articleId == articleId && it.revision == revision }
  }

  override fun requeueTask(articleId: String, revision: Long) {
    val index = tasks.indexOfFirst { it.articleId == articleId && it.revision == revision }
    if (index >= 0) tasks[index] = tasks[index].copy(
      state = RssRecommendationTaskState.QUEUED,
      startedAt = null,
    )
  }

  override fun requeueInterruptedTasks() {
    tasks.indices.forEach { index ->
      if (tasks[index].state == RssRecommendationTaskState.RUNNING) {
        tasks[index] = tasks[index].copy(
          state = RssRecommendationTaskState.QUEUED,
          startedAt = null,
        )
      }
    }
  }

  override fun clearTasks() {
    tasks.clear()
  }
}
