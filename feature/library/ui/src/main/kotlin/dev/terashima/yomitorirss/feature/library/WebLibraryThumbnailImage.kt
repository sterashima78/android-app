package dev.terashima.yomitorirss.feature.library

import android.webkit.WebSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import java.net.URI

@Composable
internal fun WebLibraryThumbnailPreview(
  book: LibraryBook,
  modifier: Modifier = Modifier,
) {
  val thumbnailUrl = book.thumbnailUrl?.trim()?.takeIf(String::isNotEmpty) ?: return
  var loadFailed by remember(thumbnailUrl) { mutableStateOf(false) }

  Column(modifier = modifier) {
    AsyncImage(
      model = rememberLibraryThumbnailModel(book, thumbnailUrl),
      contentDescription = "${book.title} の表紙",
      modifier = Modifier.size(width = 96.dp, height = 144.dp),
      contentScale = ContentScale.Fit,
      onSuccess = { loadFailed = false },
      onError = { loadFailed = true },
    )
    if (loadFailed) {
      Text(
        text = "表紙画像を読み込めませんでした",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
  }
}

@Composable
internal fun rememberLibraryThumbnailModel(
  book: LibraryBook,
  thumbnailUrl: String,
): Any {
  if (book.source != LibrarySource.WEB) return thumbnailUrl

  val context = LocalContext.current
  val pageUrl = book.infoUrl?.takeIf(String::isNotBlank) ?: book.sourceId
  val browserUserAgent = remember(context) {
    runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
  }
  val headers = remember(pageUrl, browserUserAgent) {
    webLibraryImageRequestHeaders(pageUrl, browserUserAgent)
  }
  return remember(context, thumbnailUrl, headers) {
    if (headers.isEmpty()) {
      thumbnailUrl
    } else {
      val networkHeaders = NetworkHeaders.Builder().apply {
        headers.forEach { (name, value) -> set(name, value) }
      }.build()
      ImageRequest.Builder(context)
        .data(thumbnailUrl)
        .httpHeaders(networkHeaders)
        .build()
    }
  }
}

internal fun webLibraryImageRequestHeaders(
  pageUrl: String,
  browserUserAgent: String?,
): Map<String, String> = buildMap {
  webLibraryImageReferer(pageUrl)?.let { referer ->
    put("Referer", referer)
  }
  browserUserAgent?.trim()?.takeIf(String::isNotEmpty)?.let { userAgent ->
    put("User-Agent", userAgent)
  }
}

internal fun webLibraryImageReferer(pageUrl: String): String? = runCatching {
  val uri = URI(pageUrl)
  val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
  val host = uri.host?.takeIf(String::isNotBlank) ?: return null
  val port = uri.port.takeIf { port ->
    port != -1 &&
      !(scheme == "http" && port == 80) &&
      !(scheme == "https" && port == 443)
  }
  buildString {
    append(scheme)
    append("://")
    append(host)
    if (port != null) append(":$port")
    append('/')
  }
}.getOrNull()
