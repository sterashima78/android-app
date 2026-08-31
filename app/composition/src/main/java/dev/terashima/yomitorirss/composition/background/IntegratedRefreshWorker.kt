package dev.terashima.yomitorirss.composition.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
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
import dev.terashima.yomitorirss.feature.reddit.RedditSourceBoundary
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

internal class IntegratedRefreshWorker(
  appContext: Context,
  params: WorkerParameters,
  private val container: AppContainer,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val before = unreadKeysOrNull()

    refreshSources()

    val after = unreadKeysOrNull()
    val newItems = newUnreadKeys(before, after)
    if (newItems.isNotEmpty() && after != null) {
      IntegratedRefreshNotifier(applicationContext).notifyNewItems(
        newCount = newItems.size,
        totalUnread = after.size,
      )
    }
    return Result.success()
  }

  private suspend fun refreshSources() {
    runIsolatedRefresh {
      val feeds = container.feedRepository.listFeeds()
        .filter { RedditSourceBoundary.isNonRedditFeed(it.feedUrl) }
      container.refreshFeedsUseCase(feeds) { _, _ -> }
    }
    runIsolatedRefresh { container.redditRepository.refreshAll { _, _ -> } }
    runIsolatedRefresh { container.youtubeRepository.refresh() }
    runIsolatedRefresh { container.mailRepository.sync(accountId = null) }
  }

  private suspend fun unreadKeysOrNull(): Set<String>? = try {
    unreadKeys()
  } catch (error: CancellationException) {
    throw error
  } catch (_: Throwable) {
    null
  }

  private suspend fun unreadKeys(): Set<String> = buildSet {
    container.articleRepository.listUnreadArticles()
      .forEach { article -> add("article:${article.id}") }
    container.youtubeRepository.listUnreadVideos()
      .forEach { video -> add("youtube:${video.id}") }
    container.mailRepository.getThreads(null, Mailbox.UNREAD, "")
      .forEach { thread -> add("mail:${thread.accountId}:${thread.id}") }
  }
}

private suspend fun runIsolatedRefresh(block: suspend () -> Unit) {
  try {
    block()
  } catch (error: CancellationException) {
    throw error
  } catch (_: Throwable) {
    // A source-specific failure must not prevent the remaining sources from refreshing.
  }
}

internal fun newUnreadKeys(before: Set<String>?, after: Set<String>?): Set<String> =
  if (before == null || after == null) emptySet() else after - before

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
      intent.action = IntegratedRefreshNotificationContract.ACTION_OPEN_INTEGRATED
      PendingIntent.getActivity(
        context,
        NOTIFICATION_ID,
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
      .apply { launchPendingIntent?.let { setContentIntent(it) } }
      .build()

    manager.notify(NOTIFICATION_ID, notification)
  }

  private companion object {
    const val CHANNEL_ID = "integrated_view_updates"
    const val NOTIFICATION_ID = 2101
  }
}
