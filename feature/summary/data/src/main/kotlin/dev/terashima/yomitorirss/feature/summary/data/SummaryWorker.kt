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
    return LocalAiBackgroundTaskGate.withPermit {
      withContext(Dispatchers.IO) {
        val database = YomitoriDatabase.create(applicationContext)
        val modelManager = LocalModelManager(applicationContext)
        val summaryPromptStore = SummaryPromptStore(applicationContext)
        try {
          database.requeueInterruptedSummaryTasks()
          if (SummaryQueue.executionState(applicationContext).paused) {
            return@withContext Result.success()
          }

          while (true) {
            if (SummaryQueue.executionState(applicationContext).paused) break
            val task = database.claimNextSummaryTask() ?: break
            val article = database.findArticle(task.articleId)
            if (article == null) {
              database.failRunningSummaryTask(task.articleId, "記事が見つかりません")
              continue
            }

            try {
              setForeground(createForegroundInfo(article.title))
              val cached = if (task.forceRefresh) null else database.findSummary(task.articleId)
              val summary = if (cached != null) {
                cached.summary
              } else {
                val selectedModel = modelManager.selectedModel()
                  ?: error("要約モデルをダウンロードして選択してください")
                val prompt = summaryPromptStore.prompt.value
                val cacheKey = "${summaryCacheKey(selectedModel.id, prompt, modelManager.inferenceCacheVariant(selectedModel.id))}:$HIERARCHICAL_SUMMARY_CACHE_VARIANT"

                database.updateRunningSummaryTaskProgress(task.articleId, SUMMARY_PROGRESS_FETCHING_ARTICLE)
                val articleText = ArticleContentClient().fetchArticleText(article.url)
                currentCoroutineContext().ensureActive()
                summarizeWithProgress(
                  database = database,
                  modelManager = modelManager,
                  articleId = task.articleId,
                  articleText = articleText,
                  prompt = prompt,
                ).also { generated ->
                  currentCoroutineContext().ensureActive()
                  database.saveSummary(task.articleId, generated, cacheKey)
                }
              }

              if (database.isBookmarkedForAiEnrichment(task.articleId)) {
                currentCoroutineContext().ensureActive()
                modelManager.selectedModel()
                  ?: error("AIタグ生成用のモデルをダウンロードして選択してください")
                val tagSource = buildString {
                  append("タイトル: ")
                  append(article.title)
                  append("\n\n要約:\n")
                  append(summary)
                }
                val tagPrompt = buildAutoTagPrompt(database.listExistingTagNamesForAiEnrichment())
                val generatedTags = parseGeneratedTags(
                  modelManager.summarizeText(tagSource, tagPrompt),
                )
                check(generatedTags.isNotEmpty()) { "AIタグを生成できませんでした" }
                currentCoroutineContext().ensureActive()
                var metadataChanged = database.addAiGeneratedTags(task.articleId, generatedTags)

                if (database.isUncategorizedBookmarkForAiEnrichment(task.articleId)) {
                  val existingFolders = database.listExistingFolderNamesForAiEnrichment()
                  if (existingFolders.isNotEmpty()) {
                    val folderSource = buildString {
                      append(tagSource)
                      append("\n\nタグ: ")
                      append(generatedTags.joinToString("、"))
                    }
                    val folderName = parseGeneratedFolder(
                      raw = modelManager.summarizeText(
                        folderSource,
                        buildAutoFolderPrompt(existingFolders),
                      ),
                      existingFolderNames = existingFolders,
                    )
                    currentCoroutineContext().ensureActive()
                    if (
                      folderName != null &&
                      database.assignExistingFolderForAiEnrichment(task.articleId, folderName)
                    ) {
                      metadataChanged = true
                    }
                  }
                }

                if (metadataChanged) {
                  DataChangeNotifier.shared.notifyChanged()
                }
              }

              database.completeRunningSummaryTask(task.articleId)
            } catch (error: CancellationException) {
              throw error
            } catch (error: Throwable) {
              database.failRunningSummaryTask(task.articleId, error.userMessage())
            }
          }

          Result.success()
        } finally {
          modelManager.close()
          database.close()
        }
      }
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
      modelManager.summarizeHierarchically(articleText, prompt) { progress ->
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
