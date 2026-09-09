package dev.terashima.yomitorirss.feature.video.data

import android.content.Context
import dev.terashima.yomitorirss.core.database.DatabaseConnection
import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.feature.video.VideoProvider
import dev.terashima.yomitorirss.feature.video.VideoProviderRefreshResult
import dev.terashima.yomitorirss.feature.video.VideoProviderRepository
import dev.terashima.yomitorirss.feature.video.VideoProviderType
import dev.terashima.yomitorirss.feature.video.VideoProviderVideo
import dev.terashima.yomitorirss.feature.video.VideoSubscription
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.xml.sax.SAXException

class DefaultVideoProviderRepository internal constructor(
  database: DatabaseConnection,
  httpClient: HttpClient,
  private val customProviderRuntime: CustomVideoProviderRuntime?,
) : VideoProviderRepository {
  constructor(
    database: DatabaseConnection,
    httpClient: HttpClient,
  ) : this(database, httpClient, null)

  constructor(
    database: DatabaseConnection,
    httpClient: HttpClient,
    context: Context,
  ) : this(database, httpClient, AndroidCustomVideoProviderRuntime(context, httpClient))

  private val database = VideoProviderDatabase(database)
  private val youtubeClient = YouTubeVideoProviderClient(httpClient)

  override fun providers(): List<VideoProvider> = database.providers()

  override fun saveProvider(provider: VideoProvider): VideoProvider = database.saveProvider(provider)

  override fun deleteProvider(id: String) = database.deleteProvider(id)

  override fun subscriptions(providerId: String?): List<VideoSubscription> = database.subscriptions(providerId)

  override suspend fun subscribe(providerId: String, sourceUrl: String): VideoSubscription {
    val provider = database.requireProvider(providerId)
    require(provider.enabled) { "動画プロバイダが無効です" }
    val feed = when (provider.type) {
      VideoProviderType.YOUTUBE -> youtubeClient.subscribe(sourceUrl)
      VideoProviderType.CUSTOM -> customRuntime().subscribe(provider.requireFunctionCode(), sourceUrl)
    }
    return database.upsertProviderFeed(provider, feed).first
  }

  override suspend fun unsubscribe(subscriptionId: String) = database.unsubscribe(subscriptionId)

  override suspend fun refreshProviders(providerId: String?): VideoProviderRefreshResult {
    val targets = database.providers()
      .filter { it.enabled && (providerId == null || it.id == providerId) }
      .flatMap { provider ->
        database.subscriptions(provider.id).map { subscription -> RefreshTarget(provider, subscription) }
      }
    var refreshed = 0
    var added = 0
    var pending = targets
    var attempt = 0
    val finalFailures = mutableListOf<Throwable>()

    while (pending.isNotEmpty()) {
      val retryTargets = mutableListOf<RefreshTarget>()
      pending.forEach { target ->
        val feed = try {
          refreshFeed(target)
        } catch (error: CancellationException) {
          throw error
        } catch (error: Throwable) {
          if (
            attempt < PROVIDER_REFRESH_RETRY_DELAYS_MILLIS.size &&
            shouldRetryProviderRefresh(target.provider.type, error)
          ) {
            retryTargets += target
          } else {
            finalFailures += error
          }
          return@forEach
        }

        try {
          added += database.upsertProviderFeed(target.provider, feed).second
          refreshed += 1
        } catch (error: CancellationException) {
          throw error
        } catch (error: Throwable) {
          finalFailures += error
        }
      }

      pending = retryTargets
      if (pending.isNotEmpty()) {
        delay(PROVIDER_REFRESH_RETRY_DELAYS_MILLIS[attempt])
        attempt += 1
      }
    }

    if (finalFailures.isNotEmpty()) {
      throw providerRefreshException(refreshed, finalFailures)
    }
    return VideoProviderRefreshResult(refreshed, added, 0)
  }

  override fun unreadVideos(): List<VideoProviderVideo> = database.unreadVideos()

  override fun watchLaterVideos(): List<VideoProviderVideo> = database.watchLaterVideos()

  override fun historyVideos(limit: Int): List<VideoProviderVideo> = database.historyVideos(limit)

  override fun markRead(videoId: String) = database.markRead(videoId)

  override fun markUnread(videoId: String) = database.markUnread(videoId)

  override fun setWatchLater(videoId: String, watchLater: Boolean) = database.setWatchLater(videoId, watchLater)

  override fun markAllRead(providerId: String?) = database.markAllRead(providerId)

  private suspend fun refreshFeed(target: RefreshTarget): VideoProviderFeed = when (target.provider.type) {
    VideoProviderType.YOUTUBE -> youtubeClient.refresh(target.subscription.sourceId)
    VideoProviderType.CUSTOM -> customRuntime().refresh(
      target.provider.requireFunctionCode(),
      target.subscription.sourceId,
    )
  }

  private fun customRuntime(): CustomVideoProviderRuntime = requireNotNull(customProviderRuntime) {
    "カスタム動画プロバイダ実行環境が構成されていません"
  }

  private data class RefreshTarget(
    val provider: VideoProvider,
    val subscription: VideoSubscription,
  )
}

internal fun shouldRetryProviderRefresh(type: VideoProviderType, error: Throwable): Boolean {
  if (type != VideoProviderType.YOUTUBE) return false
  return error is VideoProviderHttpException && (
    error.statusCode == 404 ||
      error.statusCode == 408 ||
      error.statusCode == 425 ||
      error.statusCode == 429 ||
      error.statusCode in 500..599
    )
}

private fun providerRefreshException(refreshed: Int, failures: List<Throwable>): IOException {
  val reasons = failures
    .groupingBy(::providerRefreshFailureReason)
    .eachCount()
    .entries
    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
    .joinToString(" / ") { (reason, count) -> "$reason × $count" }
  return IOException(
    "動画プロバイダの一部を更新できませんでした（成功: $refreshed / 失敗: ${failures.size}）\n" +
      "失敗理由: $reasons",
  )
}

private fun providerRefreshFailureReason(error: Throwable): String = when (error) {
  is VideoProviderHttpException -> "HTTP ${error.statusCode}"
  is SAXException -> "フィード解析エラー"
  is IOException -> when {
    error.message.orEmpty().startsWith("ネットワーク通信がタイムアウトしました") -> "タイムアウト"
    error.message.orEmpty().startsWith("ホスト名を解決できませんでした") -> "DNSエラー"
    error.message.orEmpty().startsWith("サーバーに接続できませんでした") -> "接続エラー"
    else -> "通信エラー"
  }
  is IllegalArgumentException, is IllegalStateException -> "プロバイダ処理エラー"
  else -> "その他のエラー"
}

private fun VideoProvider.requireFunctionCode(): String = requireNotNull(functionCode?.takeIf(String::isNotBlank)) {
  "カスタム動画プロバイダのfunction codeがありません"
}

private val PROVIDER_REFRESH_RETRY_DELAYS_MILLIS = longArrayOf(500L, 1_500L)
