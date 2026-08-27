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

private const val WEB_LIBRARY_IMAGE_ACCEPT =
  "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"

@Composable
internal fun WebLibraryThumbnailPreview(
  book: LibraryBook,
  modifier: Modifier = Modifier,
) {
  val thumbnailUrl = book.thumbnailUrl?.trim()?.takeIf(String::isNotEmpty) ?: return
  val pageUrl = book.infoUrl?.takeIf(String::isNotBlank) ?: book.sourceId
  val requestModels = rememberLibraryThumbnailModels(book, thumbnailUrl)
  var requestIndex by remember(thumbnailUrl, pageUrl) { mutableStateOf(0) }
  var loadFailed by remember(thumbnailUrl, pageUrl) { mutableStateOf(false) }
  val requestModel = requestModels.getOrElse(requestIndex) { requestModels.lastOrNull() ?: thumbnailUrl }

  Column(modifier = modifier) {
    AsyncImage(
      model = requestModel,
      contentDescription = "${book.title} の表紙",
      modifier = Modifier.size(width = 96.dp, height = 144.dp),
      contentScale = ContentScale.Fit,
      onSuccess = { loadFailed = false },
      onError = {
        if (requestIndex + 1 < requestModels.size) {
          requestIndex += 1
          loadFailed = false
        } else {
          loadFailed = true
        }
      },
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
internal fun rememberLibraryThumbnailModels(
  book: LibraryBook,
  thumbnailUrl: String,
): List<Any> {
  if (book.source != LibrarySource.WEB) return remember(thumbnailUrl) { listOf(thumbnailUrl) }

  val context = LocalContext.current
  val pageUrl = book.infoUrl?.takeIf(String::isNotBlank) ?: book.sourceId
  val browserUserAgent = remember(context) {
    runCatching { WebSettings.getDefaultUserAgent(context) }.getOrNull()
  }
  val headerCandidates = remember(pageUrl, browserUserAgent) {
    webLibraryImageRequestHeaderCandidates(pageUrl, browserUserAgent)
  }
  return remember(context, thumbnailUrl, headerCandidates) {
    headerCandidates.map { headers ->
      val networkHeaders = NetworkHeaders.Builder().apply {
        headers.forEach { (name, value) -> set(name, value) }
      }.build()
      ImageRequest.Builder(context)
        .data(thumbnailUrl)
        .httpHeaders(networkHeaders)
        .build()
    }.ifEmpty { listOf(thumbnailUrl) }
  }
}

internal fun webLibraryImageRequestHeaderCandidates(
  pageUrl: String,
  browserUserAgent: String?,
): List<Map<String, String>> {
  val commonHeaders = buildMap {
    put("Accept", WEB_LIBRARY_IMAGE_ACCEPT)
    browserUserAgent?.trim()?.takeIf(String::isNotEmpty)?.let { userAgent ->
      put("User-Agent", userAgent)
    }
  }
  val referers = listOfNotNull(
    webLibraryImageReferer(pageUrl),
    webLibraryImagePageReferer(pageUrl),
  ).distinct()
  return if (referers.isEmpty()) {
    listOf(commonHeaders)
  } else {
    referers.map { referer -> commonHeaders + ("Referer" to referer) }
  }
}

internal fun webLibraryImageRequestHeaders(
  pageUrl: String,
  browserUserAgent: String?,
): Map<String, String> = webLibraryImageRequestHeaderCandidates(pageUrl, browserUserAgent).first()

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

internal fun webLibraryImagePageReferer(pageUrl: String): String? = runCatching {
  val uri = URI(pageUrl)
  val scheme = uri.scheme?.lowercase()?.takeIf { it == "http" || it == "https" } ?: return null
  val host = uri.host?.takeIf(String::isNotBlank) ?: return null
  val port = uri.port.takeIf { port ->
    port != -1 &&
      !(scheme == "http" && port == 80) &&
      !(scheme == "https" && port == 443)
  }
  val path = uri.rawPath?.takeIf(String::isNotEmpty) ?: "/"
  buildString {
    append(scheme)
    append("://")
    append(host)
    if (port != null) append(":$port")
    append(path)
  }
}.getOrNull()
