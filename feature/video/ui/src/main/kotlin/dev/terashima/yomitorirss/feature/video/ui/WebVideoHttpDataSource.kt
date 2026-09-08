package dev.terashima.yomitorirss.feature.video.ui

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import dev.terashima.yomitorirss.feature.video.VideoPlaybackCookieProvider
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP data source used only for explicitly opted-in Web stream Cookie sharing.
 *
 * Redirects are followed manually so the Cookie header is resolved again for every redirect URL.
 */
internal class WebVideoHttpDataSource(
  private val userAgent: String,
  private val defaultRequestProperties: Map<String, String>,
  private val cookieProvider: VideoPlaybackCookieProvider,
) : BaseDataSource(true) {
  private var connection: HttpURLConnection? = null
  private var inputStream: InputStream? = null
  private var opened = false
  private var bytesRemaining = C.LENGTH_UNSET.toLong()

  override fun open(dataSpec: DataSpec): Long {
    transferInitializing(dataSpec)
    require(dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET) {
      "Web動画再生はGET requestのみ対応しています"
    }

    val openedConnection = openFollowingRedirects(dataSpec)
    connection = openedConnection
    val responseCode = openedConnection.responseCode
    if (responseCode !in 200..299) {
      val headers = openedConnection.headerFields
        .filterKeys { it != null }
        .mapKeys { requireNotNull(it.key) }
      val message = openedConnection.responseMessage
      openedConnection.disconnect()
      connection = null
      throw HttpDataSource.InvalidResponseCodeException(
        responseCode,
        message,
        null,
        headers,
        dataSpec,
        ByteArray(0),
      )
    }

    val stream = openedConnection.inputStream
    inputStream = stream
    val needsManualSkip = responseCode == HttpURLConnection.HTTP_OK && dataSpec.position > 0L
    if (needsManualSkip) skipFully(stream, dataSpec.position)

    bytesRemaining = when {
      dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
      else -> openedConnection.getHeaderField("Content-Length")
        ?.toLongOrNull()
        ?.let { contentLength ->
          if (needsManualSkip) (contentLength - dataSpec.position).coerceAtLeast(0L) else contentLength
        }
        ?: C.LENGTH_UNSET.toLong()
    }

    opened = true
    transferStarted(dataSpec)
    return bytesRemaining
  }

  override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
    if (length == 0) return 0
    if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
    val stream = inputStream ?: return C.RESULT_END_OF_INPUT
    val requested = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
      length
    } else {
      minOf(length.toLong(), bytesRemaining).toInt()
    }
    val read = stream.read(buffer, offset, requested)
    if (read == -1) return C.RESULT_END_OF_INPUT
    if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
    bytesTransferred(read)
    return read
  }

  override fun getUri(): Uri? = connection?.url?.let { Uri.parse(it.toString()) }

  override fun close() {
    try {
      inputStream?.close()
    } finally {
      inputStream = null
      connection?.disconnect()
      connection = null
      bytesRemaining = C.LENGTH_UNSET.toLong()
      if (opened) {
        opened = false
        transferEnded()
      }
    }
  }

  private fun openFollowingRedirects(dataSpec: DataSpec): HttpURLConnection {
    var url = URL(dataSpec.uri.toString())
    val initialProtocol = url.protocol
    repeat(MAX_REDIRECTS + 1) { redirectCount ->
      require(url.protocol == "http" || url.protocol == "https") { "Web動画のURL schemeが不正です" }
      require(url.protocol == initialProtocol) { "Web動画のcross-protocol redirectには対応していません" }

      val currentUrl = url
      val current = (currentUrl.openConnection() as HttpURLConnection).apply {
        connectTimeout = DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS
        readTimeout = DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS
        instanceFollowRedirects = false
        requestMethod = "GET"
        setRequestProperty("User-Agent", userAgent)
        setRequestProperty("Accept-Encoding", "identity")
        webVideoRequestProperties(
          requestUrl = currentUrl.toString(),
          defaultRequestProperties = defaultRequestProperties,
          dataSpecRequestProperties = dataSpec.httpRequestHeaders,
          cookieProvider = cookieProvider,
        ).forEach { (name, value) -> setRequestProperty(name, value) }
        rangeRequestHeader(dataSpec)?.let { setRequestProperty("Range", it) }
        connect()
      }

      val responseCode = current.responseCode
      if (responseCode !in REDIRECT_CODES) return current
      val location = current.getHeaderField("Location")
      if (location.isNullOrBlank()) return current
      if (redirectCount >= MAX_REDIRECTS) {
        current.disconnect()
        throw IOException("Web動画のredirect回数が上限を超えました")
      }
      current.disconnect()
      url = URL(currentUrl, location)
    }
    error("unreachable")
  }

  class Factory(
    private val userAgent: String,
    private val defaultRequestProperties: Map<String, String>,
    private val cookieProvider: VideoPlaybackCookieProvider,
  ) : DataSource.Factory {
    override fun createDataSource(): DataSource = WebVideoHttpDataSource(
      userAgent = userAgent,
      defaultRequestProperties = defaultRequestProperties,
      cookieProvider = cookieProvider,
    )
  }

  private companion object {
    const val MAX_REDIRECTS = 20
    val REDIRECT_CODES = setOf(300, 301, 302, 303, 307, 308)
  }
}

internal fun webVideoRequestProperties(
  requestUrl: String,
  defaultRequestProperties: Map<String, String>,
  dataSpecRequestProperties: Map<String, String>,
  cookieProvider: VideoPlaybackCookieProvider,
): Map<String, String> = buildMap {
  putAll(defaultRequestProperties)
  putAll(dataSpecRequestProperties)
  runCatching { cookieProvider.cookieHeaderFor(requestUrl) }
    .getOrNull()
    ?.takeIf(String::isNotBlank)
    ?.let { put("Cookie", it) }
}

private fun rangeRequestHeader(dataSpec: DataSpec): String? {
  if (dataSpec.position == 0L && dataSpec.length == C.LENGTH_UNSET.toLong()) return null
  val end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
    ""
  } else {
    (dataSpec.position + dataSpec.length - 1L).toString()
  }
  return "bytes=${dataSpec.position}-$end"
}

private fun skipFully(stream: InputStream, bytes: Long) {
  var remaining = bytes
  val scratch = ByteArray(4_096)
  while (remaining > 0L) {
    val skipped = stream.skip(remaining)
    if (skipped > 0L) {
      remaining -= skipped
      continue
    }
    val read = stream.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
    if (read == -1) throw IOException("Web動画のrequested positionまでskipできませんでした")
    remaining -= read
  }
}
