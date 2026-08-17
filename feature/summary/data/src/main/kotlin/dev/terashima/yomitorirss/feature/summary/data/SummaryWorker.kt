package dev.terashima.yomitorirss.feature.summary.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.core.airuntime.LocalInferenceStage
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.background.LocalAiBackgroundTaskGate
import dev.terashima.yomitorirss.core.database.DataChangeNotifier
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.article.data.network.ArticleContentClient
import dev.terashima.yomitorirss.feature.summary.summaryCacheKey
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SummaryWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    if (SummaryQueue.executionState(applicationContext).paused) return Result.success()

    setForeground(createForegroundInfo("AIタスクの実行を待っています"))
    return withContext(Dispatchers.IO) {
      val database = YomitoriDatabase.create(applicationContext)
      try {
        database.requeueInterruptedSummaryTasks()

        while (!SummaryQueue.executionState(applicationContext).paused) {
          currentCoroutineContext().ensureActive()
          val priority = database.peekNextSummaryTaskPriority() ?: break
          LocalAiBackgroundTaskGate.withPermit(priority) {
            if (SummaryQueue.executionState(applicationContext).paused) return@withPermit
            val task = database.claimNextSummaryTaskByPriority() ?: return@withPermit
            processTask(database, task)
          }
        }
        Result.success()
      } finally {
        database.close()
      }
    }
  }

  private suspend fun processTask(
    database: YomitoriDatabase,
    task: SummaryTaskRecord,
  ) {
    val article = database.findArticle(task.articleId)
    if (article == null) {
      database.failRunningSummaryTask(task.articleId, "記事が見つかりません")
      return
    }

    val modelManager = LocalModelManager.shared(applicationContext)
    val summaryPromptStore = SummaryPromptStore(applicationContext)
    try {
      setForeground(createForegroundInfo(article.title))
      val enrichmentContext = database.bookmarkEnrichmentContext(task.articleId)
      val cached = if (task.forceRefresh) null else database.findSummary(task.articleId)
      val summaryForMetadata = if (cached != null) {
        cached.summary
      } else {
        val selectedModel = modelManager.selectedModel()
          ?: error("要約モデルをダウンロードして選択してください")
        val prompt = summaryPromptStore.prompt.value
        val cacheKey = "${summaryCacheKey(selectedModel.id, prompt, modelManager.inferenceCacheVariant(selectedModel.id))}:$HIERARCHICAL_SUMMARY_CACHE_VARIANT"

        database.updateRunningSummaryTaskProgress(task.articleId, SUMMARY_PROGRESS_FETCHING_ARTICLE)
        val articleText = ArticleContentClient().fetchArticleText(article.url)
        currentCoroutineContext().ensureActive()
        val generated = summarizeWithProgress(
          database = database,
          modelManager = modelManager,
          articleId = task.articleId,
          articleText = articleText,
          prompt = prompt,
        )
        currentCoroutineContext().ensureActive()
        database.saveSummary(task.articleId, generated, cacheKey)
        generated
      }

      if (enrichmentContext != null) {
        currentCoroutineContext().ensureActive()
        modelManager.selectedModel()
          ?: error("AIメタデータ生成用のモデルをダウンロードして選択してください")
        val generatedMetadata = parseBookmarkMetadataEnrichment(
          raw = modelManager.summarizeText(
            text = summaryForMetadata,
            prompt = buildBookmarkMetadataPrompt(),
            promptSuffix = buildBookmarkMetadataCandidateSuffix(
              articleTitle = article.title,
              existingTagNames = enrichmentContext.existingTagNames,
              existingFolderNames = enrichmentContext.existingFolderNames,
            ),
          ),
          existingFolderNames = enrichmentContext.existingFolderNames,
        )
        applyBookmarkMetadata(database, task.articleId, generatedMetadata)
      }

      database.completeRunningSummaryTask(task.articleId)
    } catch (error: CancellationException) {
      throw error
    } catch (error: Throwable) {
      database.failRunningSummaryTask(task.articleId, error.userMessage())
    }
  }

  private suspend fun applyBookmarkMetadata(
    database: YomitoriDatabase,
    articleId: String,
    metadata: BookmarkAiGeneratedMetadata,
  ) {
    currentCoroutineContext().ensureActive()
    var metadataChanged = database.addAiGeneratedTags(articleId, metadata.tags)
    currentCoroutineContext().ensureActive()
    if (
      metadata.folder != null &&
      database.assignExistingFolderForAiEnrichment(articleId, metadata.folder)
    ) {
      metadataChanged = true
    }
    if (metadataChanged) {
      DataChangeNotifier.shared.notifyChanged()
    }
  }

  private suspend fun summarizeWithProgress(
    database: YomitoriDatabase,
    modelManager: LocalModelManager,
    articleId: String,
    articleText: String,
    prompt: String,
  ): String = coroutineScope {
    val hierarchyProgress = AtomicReference<HierarchicalSummaryProgress?>(null)
    val progressCollector = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
      modelManager.inferenceProgress.filterNotNull().collect { progress ->
        when (progress.stage) {
          LocalInferenceStage.PREPARING_MODEL -> database.updateRunningSummaryTaskProgress(
            articleId,
            SUMMARY_PROGRESS_PREPARING_MODEL,
          )
          LocalInferenceStage.GENERATING_RESPONSE -> {
            val stored = hierarchyProgress.get().toStoredProgress()
            database.updateRunningSummaryTaskProgress(
              articleId = articleId,
              stage = stored.stage,
              current = stored.current,
              total = stored.total,
            )
          }
        }
      }
    }

    try {
      modelManager.summarizeHierarchically(
        text = articleText,
        prompt = prompt,
      ) { progress ->
        hierarchyProgress.set(progress)
        val stored = progress.toStoredProgress()
        database.updateRunningSummaryTaskProgress(
          articleId = articleId,
          stage = stored.stage,
          current = stored.current,
          total = stored.total,
        )
      }
    } finally {
      progressCollector.cancelAndJoin()
    }
  }

  private fun createForegroundInfo(articleTitle: String): ForegroundInfo {
    val notificationManager = applicationContext.getSystemService(NotificationManager::class.java)
    notificationManager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        "記事の要約とタグ付け",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "ローカルAIで記事をバックグラウンド要約・タグ付けしている間に表示します"
        setShowBadge(false)
      },
    )

    val notificationBuilder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("記事をAI処理しています")
      .setContentText(articleTitle)
      .setStyle(NotificationCompat.BigTextStyle().bigText(articleTitle))
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    applicationContext.packageManager
      .getLaunchIntentForPackage(applicationContext.packageName)
      ?.let { launchIntent ->
        PendingIntent.getActivity(
          applicationContext,
          0,
          launchIntent,
          PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
      }
      ?.let(notificationBuilder::setContentIntent)

    val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
      0
    }
    return ForegroundInfo(NOTIFICATION_ID, notificationBuilder.build(), serviceType)
  }

  companion object {
    private const val CHANNEL_ID = "article_summary"
    private const val NOTIFICATION_ID = 8766
  }
}

private data class BookmarkEnrichmentContext(
  val existingTagNames: List<String>,
  val existingFolderNames: List<String>,
)

private fun YomitoriDatabase.bookmarkEnrichmentContext(articleId: String): BookmarkEnrichmentContext? {
  if (!isBookmarkedForAiEnrichment(articleId)) return null
  val existingFolders = if (isUncategorizedBookmarkForAiEnrichment(articleId)) {
    listExistingFolderNamesForAiEnrichment()
  } else {
    emptyList()
  }
  return BookmarkEnrichmentContext(
    existingTagNames = listExistingTagNamesForAiEnrichment(),
    existingFolderNames = existingFolders,
  )
}

private data class StoredSummaryProgress(
  val stage: String,
  val current: Int? = null,
  val total: Int? = null,
)

private fun HierarchicalSummaryProgress?.toStoredProgress(): StoredSummaryProgress = when (this?.stage) {
  HierarchicalSummaryProgressStage.CHUNK -> StoredSummaryProgress(
    stage = SUMMARY_PROGRESS_SUMMARIZING_CHUNK,
    current = this?.current,
    total = this?.total,
  )
  HierarchicalSummaryProgressStage.REDUCTION -> StoredSummaryProgress(
    stage = SUMMARY_PROGRESS_REDUCING_SUMMARY,
    current = this?.current,
    total = this?.total,
  )
  HierarchicalSummaryProgressStage.FINAL -> StoredSummaryProgress(SUMMARY_PROGRESS_FINALIZING_SUMMARY)
  HierarchicalSummaryProgressStage.DIRECT,
  null -> StoredSummaryProgress(SUMMARY_PROGRESS_GENERATING_SUMMARY)
}

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
