package dev.terashima.yomitorirss.feature.rss.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.await
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundExecutionPreferences
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskPriority
import dev.terashima.yomitorirss.feature.article.Article
import dev.terashima.yomitorirss.feature.article.ArticleRepository
import dev.terashima.yomitorirss.feature.rss.RssRecommendationAssessment
import dev.terashima.yomitorirss.feature.rss.RssRecommendationRepository
import dev.terashima.yomitorirss.feature.rss.RssRecommendationService
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTask
import dev.terashima.yomitorirss.feature.rss.RssRecommendationTaskScheduler
import dev.terashima.yomitorirss.feature.rss.RssRecommendationUnscoredReason
import kotlinx.coroutines.CancellationException

class WorkManagerRssRecommendationTaskScheduler(
  context: Context,
  private val articleRepository: ArticleRepository,
  private val repository: RssRecommendationRepository,
  private val articleSelector: (Article) -> Boolean = { true },
) : RssRecommendationTaskScheduler {
  private val appContext = context.applicationContext
  private val workManager = WorkManager.getInstance(appContext)

  override suspend fun enqueueForFeed(feedUrl: String) {
    enqueue(
      articleRepository.listUnreadArticles()
        .filter(articleSelector)
        .filter { it.sourceFeedUrl == feedUrl },
    )
  }

  override suspend fun enqueueUnread() {
    enqueue(articleRepository.listUnreadArticles().filter(articleSelector))
  }

  private suspend fun enqueue(articles: List<Article>) {
    val policy = repository.loadPolicy()
    if (!policy.enabled) {
      repository.clearTasks()
      workManager.cancelUniqueWork(WORK_NAME).await()
      return
    }

    val assessments = repository.loadAssessments(articles.map(Article::id))
    val candidates = articles.filter { article ->
      val assessment = assessments[article.id]
      assessment == null ||
        assessment.revision != policy.revision ||
        (assessment is RssRecommendationAssessment.Unscored &&
          assessment.reason == RssRecommendationUnscoredReason.INFERENCE_FAILED)
    }
    repository.enqueueTasks(candidates, policy.revision)
    kick()
  }

  override fun kick() {
    if (repository.listTasks().isEmpty()) return
    val execution = LocalAiBackgroundExecutionPreferences(appContext)
    if (execution.paused) {
      setResumeOnChargingScheduled(execution.resumeWhenCharging)
      return
    }

    val request = OneTimeWorkRequestBuilder<RssRecommendationWorker>().build()
    workManager.enqueueUniqueWork(
      WORK_NAME,
      ExistingWorkPolicy.APPEND_OR_REPLACE,
      request,
    )
  }

  override suspend fun pauseForGlobalGate() {
    workManager.cancelUniqueWork(WORK_NAME).await()
    repository.requeueInterruptedTasks()
  }

  override fun setResumeOnChargingScheduled(enabled: Boolean) {
    if (!enabled) {
      workManager.cancelUniqueWork(RESUME_ON_CHARGING_WORK_NAME)
      return
    }
    val execution = LocalAiBackgroundExecutionPreferences(appContext)
    if (!execution.paused || !execution.resumeWhenCharging || repository.listTasks().isEmpty()) return

    val request = OneTimeWorkRequestBuilder<RssRecommendationResumeOnChargingWorker>()
      .setConstraints(
        Constraints.Builder()
          .setRequiresCharging(true)
          .build(),
      )
      .build()
    workManager.enqueueUniqueWork(
      RESUME_ON_CHARGING_WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request,
    )
  }

  internal companion object {
    const val WORK_NAME = "rss-recommendation-scoring"
    const val RESUME_ON_CHARGING_WORK_NAME = "rss-recommendation-resume-on-charging"
  }
}

internal class RssRecommendationWorker(
  appContext: Context,
  params: WorkerParameters,
  private val articleRepository: ArticleRepository,
  private val repository: RssRecommendationRepository,
  private val service: RssRecommendationService,
  private val scheduler: RssRecommendationTaskScheduler,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    if (LocalAiBackgroundExecutionPreferences(applicationContext).paused) return Result.success()
    repository.requeueInterruptedTasks()
    var claimed: RssRecommendationTask? = null

    return try {
      LocalAiBackgroundTaskGate.withPermit(LocalAiBackgroundTaskPriority.NORMAL) {
        if (LocalAiBackgroundExecutionPreferences(applicationContext).paused) return@withPermit
        claimed = repository.claimNextTask() ?: return@withPermit
        val task = requireNotNull(claimed)
        val article = articleRepository.findArticle(task.articleId)
        if (article == null || article.readAt != null) {
          repository.completeTask(task.articleId, task.revision)
          return@withPermit
        }

        service.scoreArticle(article, task.revision)
        repository.completeTask(task.articleId, task.revision)
      }
      scheduler.kick()
      Result.success()
    } catch (cancelled: CancellationException) {
      claimed?.let { repository.requeueTask(it.articleId, it.revision) }
      throw cancelled
    } catch (_: Throwable) {
      claimed?.let { repository.requeueTask(it.articleId, it.revision) }
      Result.retry()
    }
  }
}

internal class RssRecommendationResumeOnChargingWorker(
  appContext: Context,
  params: WorkerParameters,
  private val scheduler: RssRecommendationTaskScheduler,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val execution = LocalAiBackgroundExecutionPreferences(applicationContext)
    if (!execution.resumeWhenCharging) return Result.success()
    execution.paused = false
    scheduler.kick()
    return Result.success()
  }
}

class RssRecommendationWorkerFactory(
  private val articleRepositoryProvider: () -> ArticleRepository,
  private val repositoryProvider: () -> RssRecommendationRepository,
  private val serviceProvider: () -> RssRecommendationService,
  private val schedulerProvider: () -> RssRecommendationTaskScheduler,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? = when (workerClassName) {
    RssRecommendationWorker::class.java.name -> RssRecommendationWorker(
      appContext = appContext,
      params = workerParameters,
      articleRepository = articleRepositoryProvider(),
      repository = repositoryProvider(),
      service = serviceProvider(),
      scheduler = schedulerProvider(),
    )
    RssRecommendationResumeOnChargingWorker::class.java.name -> RssRecommendationResumeOnChargingWorker(
      appContext = appContext,
      params = workerParameters,
      scheduler = schedulerProvider(),
    )
    else -> null
  }
}
