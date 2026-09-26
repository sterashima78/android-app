package dev.terashima.yomitorirss.feature.rss

import dev.terashima.yomitorirss.feature.article.Article
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RssRecommendationServiceTest {
  @Test
  fun `除外条件がなければ記事単位評価を実行しない`() = runBlocking {
    val repository = FakeRecommendationRepository()
    val engine = FakeRecommendationEngine()
    val service = RssRecommendationService(repository, engine, nowMillis = { 100L })

    val result = service.scoreArticle(article("a1", "記事1"), revision = 0L)

    assertNull(result)
    assertEquals(0, engine.scoreCalls)
  }

  @Test
  fun `実行先変更はrevisionを進める`() {
    val repository = FakeRecommendationRepository()
    val service = RssRecommendationService(repository, FakeRecommendationEngine())

    val updated = service.setExecutionProvider(RssRecommendationExecutionProvider.CLOUD)

    assertEquals(RssRecommendationExecutionProvider.CLOUD, updated.executionProvider)
    assertEquals(1L, updated.revision)
  }

  @Test
  fun `学習中にproviderが変わった場合は古い学習結果を反映しない`() = runBlocking {
    val repository = FakeRecommendationRepository(
      policy = RssRecommendationPolicy(
        manualCondition = "広告は低くする",
        executionProvider = RssRecommendationExecutionProvider.CLOUD,
        revision = 2L,
      ),
    )
    repository.addFeedback("a1", "除外した記事", null)
    val engine = FakeRecommendationEngine(
      improveResult = "新しい学習条件",
      onImprove = { repository.setExecutionProvider(RssRecommendationExecutionProvider.LOCAL) },
    )
    val service = RssRecommendationService(repository, engine)

    val result = service.improvePendingFeedback()

    assertNull(result)
    assertEquals("", repository.policy.learnedCondition)
    assertEquals(RssRecommendationExecutionProvider.LOCAL, repository.policy.executionProvider)
    assertEquals(1, repository.listPendingFeedback().size)
  }

  @Test
  fun `記事単位評価は数値スコアを保存する`() = runBlocking {
    val repository = FakeRecommendationRepository(
      policy = RssRecommendationPolicy(manualCondition = "広告は低くする", revision = 3L),
    )
    val engine = FakeRecommendationEngine(
      decisions = listOf(RssRecommendationDecision.Scored(10)),
    )
    val service = RssRecommendationService(repository, engine, nowMillis = { 200L })

    val result = service.scoreArticle(article("a1", "通常記事"), revision = 3L)

    assertEquals(
      RssRecommendationAssessment.Scored(10, revision = 3L, assessedAt = 200L),
      result,
    )
    assertEquals(result, repository.assessments["a1"])
  }

  @Test
  fun `記事単位評価は情報不足を数値にしない`() = runBlocking {
    val repository = FakeRecommendationRepository(
      policy = RssRecommendationPolicy(manualCondition = "広告は低くする", revision = 3L),
    )
    val engine = FakeRecommendationEngine(
      decisions = listOf(RssRecommendationDecision.InsufficientInformation),
    )
    val service = RssRecommendationService(repository, engine, nowMillis = { 210L })

    val result = service.scoreArticle(article("a1", "判断材料の少ない記事"), revision = 3L)

    assertEquals(
      RssRecommendationAssessment.Unscored(
        RssRecommendationUnscoredReason.INSUFFICIENT_INFORMATION,
        revision = 3L,
        assessedAt = 210L,
      ),
      result,
    )
  }

  @Test
  fun `記事単位評価の推論失敗は10にせず未評価理由として保存する`() = runBlocking {
    val repository = FakeRecommendationRepository(
      policy = RssRecommendationPolicy(manualCondition = "広告は低くする", revision = 1L),
    )
    val engine = FakeRecommendationEngine(failScoring = true)
    val service = RssRecommendationService(repository, engine, nowMillis = { 300L })

    val result = service.scoreArticle(article("a1", "記事"), revision = 1L)

    assertEquals(
      RssRecommendationAssessment.Unscored(
        RssRecommendationUnscoredReason.INFERENCE_FAILED,
        revision = 1L,
        assessedAt = 300L,
      ),
      result,
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
  private val improveResult: String = "",
  private val onImprove: () -> Unit = {},
) : RssRecommendationEngine {
  var scoreCalls = 0

  override suspend fun score(
    provider: RssRecommendationExecutionProvider,
    condition: String,
    titles: List<String>,
  ): List<RssRecommendationDecision> {
    scoreCalls += 1
    if (failScoring) error("inference failed")
    return decisions
  }

  override suspend fun improveLearnedCondition(
    provider: RssRecommendationExecutionProvider,
    manualCondition: String,
    learnedCondition: String,
    feedback: List<RssRecommendationFeedback>,
  ): String {
    onImprove()
    return improveResult.ifBlank { learnedCondition }
  }
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

  override fun setExecutionProvider(
    provider: RssRecommendationExecutionProvider,
  ): RssRecommendationPolicy {
    if (policy.executionProvider != provider) {
      policy = policy.copy(executionProvider = provider, revision = policy.revision + 1)
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

  override fun executionProvider(): RssRecommendationExecutionProvider = policy.executionProvider

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
