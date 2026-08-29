package dev.terashima.yomitorirss.feature.backup.data

import android.net.NetworkCapabilities

internal fun NetworkCapabilities.isValidatedWifiForGoogleDriveBackup(): Boolean =
  hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
    hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
