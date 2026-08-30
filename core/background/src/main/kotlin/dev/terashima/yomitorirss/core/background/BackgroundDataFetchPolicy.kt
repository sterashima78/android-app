package dev.terashima.yomitorirss.core.background

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.Constraints
import androidx.work.NetworkType

class BackgroundDataFetchPreferences(context: Context) {
  private val preferences = context.applicationContext.getSharedPreferences(
    PREFERENCES_NAME,
    Context.MODE_PRIVATE,
  )

  var wifiOnly: Boolean
    get() = preferences.getBoolean(KEY_WIFI_ONLY, false)
    set(value) {
      preferences.edit().putBoolean(KEY_WIFI_ONLY, value).apply()
    }

  var integratedRefreshIntervalMinutes: Long
    get() = preferences.getLong(
      KEY_INTEGRATED_REFRESH_INTERVAL_MINUTES,
      DEFAULT_INTEGRATED_REFRESH_INTERVAL_MINUTES,
    ).takeIf(INTEGRATED_REFRESH_INTERVAL_OPTIONS_MINUTES::contains)
      ?: DEFAULT_INTEGRATED_REFRESH_INTERVAL_MINUTES
    set(value) {
      require(value in INTEGRATED_REFRESH_INTERVAL_OPTIONS_MINUTES) {
        "Unsupported integrated refresh interval: $value minutes"
      }
      preferences.edit().putLong(KEY_INTEGRATED_REFRESH_INTERVAL_MINUTES, value).apply()
    }

  companion object {
    val INTEGRATED_REFRESH_INTERVAL_OPTIONS_MINUTES = listOf(15L, 30L, 60L, 180L, 360L, 720L, 1440L)
    const val DEFAULT_INTEGRATED_REFRESH_INTERVAL_MINUTES = 60L

    private const val PREFERENCES_NAME = "background_data_fetch"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_INTEGRATED_REFRESH_INTERVAL_MINUTES = "integrated_refresh_interval_minutes"
  }
}

fun backgroundDataFetchConstraints(context: Context): Constraints {
  val wifiOnly = BackgroundDataFetchPreferences(context).wifiOnly
  return if (wifiOnly) {
    Constraints.Builder()
      .setRequiredNetworkRequest(backgroundDataFetchNetworkRequest(wifiOnly = true), NetworkType.CONNECTED)
      .build()
  } else {
    Constraints.Builder()
      .setRequiredNetworkType(NetworkType.CONNECTED)
      .build()
  }
}

fun backgroundDataFetchNetworkRequest(context: Context): NetworkRequest =
  backgroundDataFetchNetworkRequest(BackgroundDataFetchPreferences(context).wifiOnly)

fun isBackgroundDataFetchAllowed(context: Context): Boolean {
  if (!BackgroundDataFetchPreferences(context).wifiOnly) return true
  val connectivityManager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
  val activeNetwork = connectivityManager.activeNetwork ?: return false
  val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
  return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

private fun backgroundDataFetchNetworkRequest(wifiOnly: Boolean): NetworkRequest =
  NetworkRequest.Builder()
    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    .apply {
      if (wifiOnly) {
        addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      }
    }
    .build()
