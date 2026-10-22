package dev.terashima.yomitorirss.composition.background

import android.app.Application
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import dev.terashima.yomitorirss.core.network.TrafficTransfer
import dev.terashima.yomitorirss.feature.settings.RssFeedTrafficRecord
import dev.terashima.yomitorirss.feature.settings.TrafficUsageOverview
import dev.terashima.yomitorirss.feature.settings.TrafficUsageReader
import dev.terashima.yomitorirss.feature.settings.TrafficUsageRecord
import java.time.LocalDate
import java.time.ZoneId

/**
 * Privacy-preserving, bounded counters. No destination, user identifier or request body is stored.
 * The counters are application-specific diagnostics and do not enter the backup allowlist.
 */
internal class AndroidTrafficUsageRepository(
  private val application: Application,
  private val feedTitleResolver: () -> Map<String, String> = { emptyMap() },
) : TrafficUsageReader {
  private val preferences: SharedPreferences =
    application.getSharedPreferences("traffic_diagnostics", Context.MODE_PRIVATE)

  @Synchronized
  fun record(transfer: TrafficTransfer) {
    val feature = transfer.tag.takeIf { it in KNOWN_FEATURES } ?: "other"
    val date = LocalDate.now().toString()
    val transport = currentTransport()
    val key = "$date|$feature|$transport"
    val previous = decode(preferences.getString(key, null))
    val now = System.currentTimeMillis()
    val editor = preferences.edit()
      .putString(key, "${previous.first + transfer.receivedBytes},${previous.second + transfer.sentBytes}")
    val resource = transfer.resource
    if (feature == "rss" && resource != null &&
      resource.id.matches(FEED_ID_PATTERN) && resource.origin in KNOWN_ORIGINS
    ) {
      val resourceKey = "$date|$FEED_RECORD_TYPE|$transport|${resource.id}|${resource.origin}"
      val counts = decodeFeed(preferences.getString(resourceKey, null))
      editor.putString(
        resourceKey,
        "${counts.first + transfer.receivedBytes},${counts.second + 1},${counts.third + if (transfer.responseStatus == 304) 1 else 0}",
      )
    }
    if (!preferences.contains(FIRST_RECORD_MILLIS)) editor.putLong(FIRST_RECORD_MILLIS, now)
    prune(editor, date)
    editor.apply()
  }

  @Synchronized
  private fun savedRecords(startDate: String): List<TrafficUsageRecord> =
    preferences.all.mapNotNull { (key, value) ->
      val parts = key.split('|')
      if (parts.size != 3 || parts[0] < startDate || value !is String) return@mapNotNull null
      val (rx, tx) = decode(value)
      TrafficUsageRecord(parts[0], parts[1], parts[2], rx, tx)
    }

  private fun savedFeedRecords(startDate: String): List<RssFeedTrafficRecord> =
    preferences.all.mapNotNull { (key, value) ->
      val parts = key.split('|')
      if (parts.size != 5 || parts[1] != FEED_RECORD_TYPE || parts[0] < startDate || value !is String) {
        return@mapNotNull null
      }
      val (rx, requests, notModified) = decodeFeed(value)
      RssFeedTrafficRecord(
        date = parts[0],
        feedId = parts[3],
        feedTitle = null,
        transport = parts[2],
        origin = parts[4],
        receivedBytes = rx,
        requests = requests,
        notModified = notModified,
      )
    }

  override fun read(days: Int): TrafficUsageOverview {
    require(days in 1..MAX_DAYS)
    val start = LocalDate.now().minusDays(days.toLong() - 1)
    val records = savedRecords(start.toString())
    val titles = runCatching(feedTitleResolver).getOrDefault(emptyMap())
    val feedRecords = savedFeedRecords(start.toString()).map { record ->
      record.copy(feedTitle = titles[record.feedId])
    }
    val from = maxOf(
      start.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
      preferences.getLong(FIRST_RECORD_MILLIS, Long.MAX_VALUE),
    )
    val until = System.currentTimeMillis()
    return TrafficUsageOverview(
      records = records,
      feedRecords = feedRecords,
      systemMobileBytes = systemBytes(ConnectivityManager.TYPE_MOBILE, from, until),
      systemWifiBytes = systemBytes(ConnectivityManager.TYPE_WIFI, from, until),
    )
  }

  private fun currentTransport(): String {
    val connectivity = application.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
    val network = connectivity.activeNetwork ?: return "unknown"
    val caps = connectivity.getNetworkCapabilities(network) ?: return "unknown"
    // VPN and simultaneous transports cannot safely be attributed to one radio.
    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return "unknown"
    val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    val mobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    return when {
      wifi && !mobile -> "wifi"
      mobile && !wifi -> "mobile"
      else -> "unknown"
    }
  }

  @Suppress("DEPRECATION")
  private fun systemBytes(networkType: Int, from: Long, until: Long): Long? {
    if (from >= until) return null
    val manager = application.getSystemService(NetworkStatsManager::class.java) ?: return null
    return try {
      manager.queryDetailsForUid(networkType, null, from, until, Process.myUid()).use { stats ->
        var total = 0L
        val bucket = NetworkStats.Bucket()
        while (stats.hasNextBucket()) {
          stats.getNextBucket(bucket)
          total += bucket.rxBytes.coerceAtLeast(0L) + bucket.txBytes.coerceAtLeast(0L)
        }
        total
      }
    } catch (_: SecurityException) {
      null
    } catch (_: RuntimeException) {
      null // Vendor implementations may restrict per-UID network history.
    }
  }

  private fun prune(editor: SharedPreferences.Editor, today: String) {
    val earliest = LocalDate.parse(today).minusDays(MAX_DAYS.toLong() - 1).toString()
    preferences.all.keys.forEach { key ->
      if (key.length >= 10 && key[4] == '-' && key[7] == '-' && key.take(10) < earliest) {
        editor.remove(key)
      }
    }
  }

  private fun decode(value: String?): Pair<Long, Long> {
    val values = value?.split(',') ?: return 0L to 0L
    if (values.size != 2) return 0L to 0L
    return (values[0].toLongOrNull() ?: 0L) to (values[1].toLongOrNull() ?: 0L)
  }

  private fun decodeFeed(value: String?): Triple<Long, Long, Long> {
    val values = value?.split(',') ?: return Triple(0L, 0L, 0L)
    if (values.size != 3) return Triple(0L, 0L, 0L)
    return Triple(
      (values[0].toLongOrNull() ?: 0L).coerceAtLeast(0L),
      (values[1].toLongOrNull() ?: 0L).coerceAtLeast(0L),
      (values[2].toLongOrNull() ?: 0L).coerceAtLeast(0L),
    )
  }

  private companion object {
    const val FEED_RECORD_TYPE = "rss_feed"
    val FEED_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,64}")
    val KNOWN_ORIGINS = setOf("screen_open", "manual", "background", "import", "other")
    const val MAX_DAYS = 90
    const val FIRST_RECORD_MILLIS = "_traffic_first_record"
    val KNOWN_FEATURES = setOf(
      "rss", "article", "mail", "cloud_ai", "wiki", "podcast", "video", "library",
      "model", "other",
    )
  }
}
