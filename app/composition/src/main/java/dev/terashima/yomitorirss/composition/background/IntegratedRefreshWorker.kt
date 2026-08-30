package dev.terashima.yomitorirss.composition.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.terashima.yomitorirss.AppContainer
import dev.terashima.yomitorirss.core.background.BackgroundDataFetchPreferences
import dev.terashima.yomitorirss.core.background.backgroundDataFetchConstraints
import dev.terashima.yomitorirss.feature.mail.Mailbox
import dev.terashima.yomitorirss.feature.reddit.isRedditFeedUrl
import java.util.concurrent.TimeUnit

internal class IntegratedRefreshWorker(
  appContext: Context,
  params: WorkerParameters,
  private val container: AppContainer,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val before = unreadKeys()

    refreshSources()

    val after = unreadKeys()
    val newItems = after - before
    if (newItems.isNotEmpty()) {
      IntegratedRefreshNotifier(applicationContext).notifyNewItems(
        newCount = newItems.size,
        totalUnread = after.size,
      )
    }
    return Result.success()
  }

  private suspend fun refreshSources() {
    runCatching {
      val feeds = container.feedRepository.listFeeds()
        .filterNot { isRedditFeedUrl(it.feedUrl) }
      container.refreshFeedsUseCase(feeds) { _, _ -> }
    }
    runCatching { container.redditRepository.refreshAll { _, _ -> } }
    runCatching { container.youtubeRepository.refresh() }
    runCatching { container.mailRepository.sync(accountId = null) }
  }

  private suspend fun unreadKeys(): Set<String> = buildSet {
    runCatching { container.articleRepository.listUnreadArticles() }
      .getOrDefault(emptyList())
      .forEach { article -> add("article:${article.id}") }
    runCatching { container.youtubeRepository.listUnreadVideos() }
      .getOrDefault(emptyList())
      .forEach { video -> add("youtube:${video.id}") }
    runCatching { container.mailRepository.getThreads(null, Mailbox.UNREAD, "") }
      .getOrDefault(emptyList())
      .forEach { thread -> add("mail:${thread.accountId}:${thread.id}") }
  }
}

internal class IntegratedRefreshWorkerFactory(
  private val container: AppContainer,
) : WorkerFactory() {
  override fun createWorker(
    appContext: Context,
    workerClassName: String,
    workerParameters: WorkerParameters,
  ): ListenableWorker? = if (workerClassName == IntegratedRefreshWorker::class.java.name) {
    IntegratedRefreshWorker(appContext, workerParameters, container)
  } else {
    null
  }
}

internal object IntegratedRefreshScheduler {
  private const val UNIQUE_WORK_NAME = "integrated-view-periodic-refresh"

  fun schedule(context: Context) {
    val appContext = context.applicationContext
    val intervalMinutes = BackgroundDataFetchPreferences(appContext).integratedRefreshIntervalMinutes
    val request = PeriodicWorkRequestBuilder<IntegratedRefreshWorker>(
      intervalMinutes,
      TimeUnit.MINUTES,
    )
      .setConstraints(backgroundDataFetchConstraints(appContext))
      .build()

    WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
      UNIQUE_WORK_NAME,
      ExistingPeriodicWorkPolicy.UPDATE,
      request,
    )
  }

  internal fun requestConstraints(context: Context): Constraints = backgroundDataFetchConstraints(context)
}

private class IntegratedRefreshNotifier(
  private val context: Context,
) {
  fun notifyNewItems(newCount: Int, totalUnread: Int) {
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      return
    }
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
      NotificationChannel(
        CHANNEL_ID,
        "統合ビューの新着",
        NotificationManager.IMPORTANCE_DEFAULT,
      ).apply {
        description = "RSS・Reddit・YouTube・メールの新しい未読アイテム"
        setShowBadge(true)
      },
    )

    val launchPendingIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
      PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
    }
    val notification = Notification.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
      .setContentTitle("新しい未読アイテム")
      .setContentText("${newCount}件の新着があります（未読 ${totalUnread}件）")
      .setNumber(totalUnread)
      .setAutoCancel(true)
      .apply { launchPendingIntent?.let(::setContentIntent) }
      .build()

    manager.notify(NOTIFICATION_ID, notification)
  }

  private companion object {
    const val CHANNEL_ID = "integrated_view_updates"
    const val NOTIFICATION_ID = 2101
  }
}
