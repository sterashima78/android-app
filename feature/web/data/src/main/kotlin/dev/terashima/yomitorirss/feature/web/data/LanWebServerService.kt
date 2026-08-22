package dev.terashima.yomitorirss.feature.web.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.terashima.yomitorirss.feature.web.LanWebRepositoryProvider
import java.net.Inet4Address
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LanWebServerService : Service() {
  private val executor: ExecutorService = Executors.newSingleThreadExecutor()
  private lateinit var connectivityManager: ConnectivityManager
  private lateinit var accessToken: String
  private var server: LanWebServer? = null
  private var networkCallbackRegistered = false
  private var terminalError: String? = null

  private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) = refreshAddress()
    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = refreshAddress()
    override fun onLost(network: Network) = refreshAddress()
  }

  override fun onCreate() {
    super.onCreate()
    createNotificationChannel()
    accessToken = loadOrCreateToken()
    connectivityManager = getSystemService(ConnectivityManager::class.java)
    LanWebServerStateStore.starting()
    ServiceCompat.startForeground(
      this,
      NOTIFICATION_ID,
      notification(null),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
    )
    registerNetworkCallback()
    executor.execute { startServer() }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      stopServer()
      return START_NOT_STICKY
    }
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onDestroy() {
    runCatching { if (networkCallbackRegistered) connectivityManager.unregisterNetworkCallback(networkCallback) }
    networkCallbackRegistered = false
    runCatching { server?.close() }
    server = null
    executor.shutdownNow()
    LanWebServerStateStore.stopped(terminalError)
    super.onDestroy()
  }

  private fun startServer() {
    runCatching {
      val repositories = applicationContext as? LanWebRepositoryProvider
        ?: error("Application must provide LAN web repositories")
      LanWebServer(
        articleRepository = repositories.lanWebArticleRepository,
        bookmarkRepository = repositories.lanWebBookmarkRepository,
        feedRepository = repositories.lanWebFeedRepository,
        accessToken = accessToken,
      ).also {
        it.start()
        server = it
      }
    }.onSuccess {
      refreshAddress()
    }.onFailure { error ->
      val message = error.message?.takeIf(String::isNotBlank) ?: "Webサーバを起動できませんでした"
      terminalError = message
      LanWebServerStateStore.stopped(message)
      stopForeground(STOP_FOREGROUND_REMOVE)
      stopSelf()
    }
  }

  private fun stopServer() {
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  private fun registerNetworkCallback() {
    runCatching {
      connectivityManager.registerDefaultNetworkCallback(networkCallback)
      networkCallbackRegistered = true
    }
  }

  private fun refreshAddress() {
    executor.execute {
      val address = connectivityManager.activeNetwork
        ?.let(connectivityManager::getLinkProperties)
        ?.linkAddresses
        ?.asSequence()
        ?.map { it.address }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
        ?.hostAddress
      val accessUrl = address?.let { "http://$it:${LanWebServer.PORT}/?token=$accessToken" }
      LanWebServerStateStore.running(address, accessUrl)
      getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(address))
    }
  }

  private fun notification(address: String?): Notification {
    val stopIntent = PendingIntent.getService(
      this,
      0,
      Intent(this, LanWebServerService::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val detail = if (address == null) {
      "LAN接続を待機中です。タップして停止"
    } else {
      "http://$address:${LanWebServer.PORT}/  タップして停止"
    }
    return NotificationCompat.Builder(this, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("Webサーバ起動中")
      .setContentText(detail)
      .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
      .setContentIntent(stopIntent)
      .addAction(0, "停止", stopIntent)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setCategory(NotificationCompat.CATEGORY_SERVICE)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .build()
  }

  private fun createNotificationChannel() {
    val channel = NotificationChannel(
      CHANNEL_ID,
      "Webサーバ",
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = "LAN内でWebサーバを起動している間に表示します"
      setShowBadge(false)
    }
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  private fun loadOrCreateToken(): String {
    val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
    preferences.getString(KEY_ACCESS_TOKEN, null)?.takeIf(String::isNotBlank)?.let { return it }
    val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
    val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    preferences.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    return token
  }

  companion object {
    private const val CHANNEL_ID = "lan_web_server"
    private const val NOTIFICATION_ID = 8765
    private const val ACTION_STOP = "dev.terashima.yomitorirss.web.STOP"
    private const val PREFERENCES_NAME = "lan_web_server"
    private const val KEY_ACCESS_TOKEN = "access_token"

    fun start(context: Context) {
      ContextCompat.startForegroundService(
        context,
        Intent(context, LanWebServerService::class.java),
      )
    }

    fun stop(context: Context) {
      context.startService(
        Intent(context, LanWebServerService::class.java).setAction(ACTION_STOP),
      )
    }
  }
}
