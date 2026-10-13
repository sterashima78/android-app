package dev.terashima.yomitorirss.core.airuntime

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal fun openModelDownloadConnection(initialUrl: String): HttpURLConnection {
  var currentUrl = initialUrl
  repeat(MAX_REDIRECTS) {
    val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
      connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MILLIS
      readTimeout = DOWNLOAD_READ_TIMEOUT_MILLIS
      instanceFollowRedirects = false
      setRequestProperty("Accept-Encoding", "identity")
      setRequestProperty("User-Agent", "Yomitori-RSS-Reader/0.2")
    }
    val status = connection.responseCode
    if (status in 300..399) {
      val location = connection.getHeaderField("Location") ?: throw IOException("リダイレクト先がありません")
      currentUrl = URL(URL(currentUrl), location).toString()
      connection.disconnect()
    } else {
      if (status !in 200..299) {
        connection.disconnect()
        throw IOException("HTTP $status")
      }
      return connection
    }
  }
  throw IOException("リダイレクト回数が上限を超えました")
}

private const val DOWNLOAD_CONNECT_TIMEOUT_MILLIS = 30_000
private const val DOWNLOAD_READ_TIMEOUT_MILLIS = 60_000
private const val MAX_REDIRECTS = 8
