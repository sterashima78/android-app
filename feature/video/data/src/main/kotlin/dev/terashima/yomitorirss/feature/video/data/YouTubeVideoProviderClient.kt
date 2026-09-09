package dev.terashima.yomitorirss.feature.video.data

import dev.terashima.yomitorirss.core.network.HttpClient
import dev.terashima.yomitorirss.core.network.HttpRequest
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element

internal data class VideoProviderFeed(
  val sourceId: String,
  val title: String,
  val sourceUrl: String,
  val videos: List<VideoProviderFeedItem>,
)

internal data class VideoProviderFeedItem(
  val id: String,
  val title: String,
  val url: String,
  val thumbnailUrl: String?,
  val publishedAtEpochMillis: Long,
)

internal class VideoProviderHttpException(
  val statusCode: Int,
) : IOException("動画チャンネルの取得に失敗しました: HTTP $statusCode")

internal class YouTubeVideoProviderClient(
  private val httpClient: HttpClient,
) {
  suspend fun subscribe(sourceUrl: String): VideoProviderFeed {
    val requestedChannelId = channelId(sourceUrl)
    val feed = fetch(requestedChannelId)
    require(feed.sourceId == requestedChannelId) { "チャンネルIDが取得結果と一致しません" }
    return feed
  }

  suspend fun refresh(sourceId: String): VideoProviderFeed = fetch(validateChannelId(sourceId))

  private suspend fun fetch(channelId: String): VideoProviderFeed = withContext(Dispatchers.IO) {
    val response = httpClient.execute(
      HttpRequest(
        url = feedUrl(channelId),
        maxResponseBytes = 4L * 1024 * 1024,
      ),
    )
    if (!response.isSuccessful) {
      throw VideoProviderHttpException(response.statusCode)
    }
    parse(response.body)
  }

  private fun parse(bytes: ByteArray): VideoProviderFeed {
    val factory = DocumentBuilderFactory.newInstance().apply {
      isNamespaceAware = true
      runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
      runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
      runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
      isExpandEntityReferences = false
    }
    val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    val root = document.documentElement ?: error("動画チャンネルのフィードが空です")
    val channelId = root.firstText(YT_NAMESPACE, "channelId")
      ?.let(::canonicalChannelId)
      ?: error("動画チャンネルのフィードにchannelIdがありません")
    val channelTitle = root.firstDirectText(ATOM_NAMESPACE, "title")
      ?: error("動画チャンネルのフィードにタイトルがありません")
    val entries = root.getElementsByTagNameNS(ATOM_NAMESPACE, "entry")
    val videos = buildList {
      for (index in 0 until entries.length) {
        val entry = entries.item(index) as? Element ?: continue
        val id = entry.firstText(YT_NAMESPACE, "videoId") ?: continue
        val title = entry.firstDirectText(ATOM_NAMESPACE, "title") ?: continue
        val url = entry.alternateLink() ?: "https://www.youtube.com/watch?v=$id"
        val published = entry.firstDirectText(ATOM_NAMESPACE, "published")
          ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
          ?: continue
        add(
          VideoProviderFeedItem(
            id = id,
            title = title,
            url = url,
            thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
            publishedAtEpochMillis = published,
          ),
        )
      }
    }
    return VideoProviderFeed(
      sourceId = channelId,
      title = channelTitle,
      sourceUrl = canonicalUrl(channelId),
      videos = videos,
    )
  }

  private fun channelId(input: String): String {
    val uri = runCatching { URI(input.trim()) }
      .getOrElse { throw IllegalArgumentException("チャンネルURLが正しくありません") }
    val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotBlank)
    require(
      uri.scheme == "https" &&
        uri.host == "www.youtube.com" &&
        uri.rawQuery == null &&
        uri.rawFragment == null &&
        segments.size == 2 &&
        segments[0] == "channel" &&
        CHANNEL_ID_PATTERN.matches(segments[1]),
    ) {
      "channel ID を含む HTTPS のチャンネルURLを入力してください"
    }
    return segments[1]
  }

  private fun canonicalChannelId(value: String): String = when {
    CHANNEL_ID_PATTERN.matches(value) -> value
    CHANNEL_ID_WITHOUT_PREFIX_PATTERN.matches(value) -> "UC$value"
    else -> error("動画チャンネルのchannelIdが不正です")
  }

  private fun validateChannelId(value: String): String = value.also {
    require(CHANNEL_ID_PATTERN.matches(it)) { "動画チャンネルのIDが不正です" }
  }

  private fun canonicalUrl(channelId: String): String = "https://www.youtube.com/channel/$channelId"

  private fun feedUrl(channelId: String): String =
    "https://www.youtube.com/feeds/videos.xml?playlist_id=UULF${channelId.removePrefix("UC")}"

  private fun Element.firstText(namespace: String, localName: String): String? =
    getElementsByTagNameNS(namespace, localName)
      .item(0)
      ?.textContent
      ?.trim()
      ?.takeIf(String::isNotBlank)

  private fun Element.firstDirectText(namespace: String, localName: String): String? {
    val children = childNodes
    for (index in 0 until children.length) {
      val element = children.item(index) as? Element ?: continue
      if (element.namespaceURI == namespace && element.localName == localName) {
        return element.textContent?.trim()?.takeIf(String::isNotBlank)
      }
    }
    return null
  }

  private fun Element.alternateLink(): String? {
    val links = getElementsByTagNameNS(ATOM_NAMESPACE, "link")
    for (index in 0 until links.length) {
      val link = links.item(index) as? Element ?: continue
      if (link.getAttribute("rel") == "alternate") {
        return link.getAttribute("href").trim().takeIf(String::isNotBlank)
      }
    }
    return null
  }

  private companion object {
    const val ATOM_NAMESPACE = "http://www.w3.org/2005/Atom"
    const val YT_NAMESPACE = "http://www.youtube.com/xml/schemas/2015"
    val CHANNEL_ID_PATTERN = Regex("^UC[A-Za-z0-9_-]{22}$")
    val CHANNEL_ID_WITHOUT_PREFIX_PATTERN = Regex("^[A-Za-z0-9_-]{22}$")
  }
}
