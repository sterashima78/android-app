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
import dev.terashima.yomitorirss.core.airuntime.HIERARCHICAL_SUMMARY_CACHE_VARIANT
import dev.terashima.yomitorirss.core.airuntime.LocalModelManager
import dev.terashima.yomitorirss.core.airuntime.summarizeHierarchically
import dev.terashima.yomitorirss.core.database.YomitoriDatabase
import dev.terashima.yomitorirss.feature.article.data.network.ArticleContentClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class SummaryWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
    val database = YomitoriDatabase.create(applicationContext)
    val modelManager = LocalModelManager(applicationContext)
    try {
      database.requeueInterruptedSummaryTasks()

      while (true) {
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
            val prompt = modelManager.summaryPrompt.value
            val cacheKey = "${modelManager.summaryCacheKey(selectedModel.id, prompt)}:$HIERARCHICAL_SUMMARY_CACHE_VARIANT"
            val articleText = ArticleContentClient().fetchArticleText(article.url)
            currentCoroutineContext().ensureActive()
            modelManager.summarizeHierarchically(articleText, prompt).also { generated ->
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
            val generatedTags = parseGeneratedTags(
              modelManager.summarize(tagSource, AUTO_TAG_PROMPT),
            )
            currentCoroutineContext().ensureActive()
            database.addAiGeneratedTags(task.articleId, generatedTags)
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

private fun Throwable.userMessage(): String =
  generateSequence(this) { it.cause }
    .mapNotNull(Throwable::message)
    .firstOrNull(String::isNotBlank)
    ?: javaClass.simpleName
